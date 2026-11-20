package com.example.travelplanner.service;

import com.example.travelplanner.dto.Travel.*;
import com.example.travelplanner.integration.TravelProviders;
import com.example.travelplanner.pricing.Pricing;
import com.example.travelplanner.pricing.Pricing.Item;
import com.example.travelplanner.pricing.Pricing.Total;
import com.example.travelplanner.recommendation.Recommendations;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
public class JourneyService {
    private final CatalogService catalog;
    private final TravelProviders.Routes routes;
    private final Pricing pricing;

    public JourneyService(CatalogService catalog, TravelProviders.Routes routes, Pricing pricing) {
        this.catalog = catalog;
        this.routes = routes;
        this.pricing = pricing;
    }

    public record Section(String key, List<Segment> options, Segment chosen, Item cost) {}
    public record Journey(List<Section> sections, Total cost, BigDecimal kilometers, int minutes, int transfers) {}
    public record RouteOption(String id, List<Mode> modes, Journey journey, BigDecimal roundTripKrw) {}
    public record RouteResults(Trip trip, Journey selected, BigDecimal roundTripKrw, List<RouteOption> alternatives,
                               Map<String, Recommendations.RoutePick> recommendations) {}

    public RouteResults compare(RouteRequest request) {
        Trip trip = catalog.validateTrip(request.trip());
        List<List<Segment>> options = routes.options(trip.origin(), trip.destination(), trip.start());
        Journey selected = resolve(options, request.legs(), trip.preferredMode(), trip.party(), trip.start());
        List<RouteOption> alternatives = new ArrayList<>();
        alternatives.add(option("selected", selected));
        List<Map<String, Leg>> combinations = List.of(Map.of());
        for (Section section : selected.sections()) {
            combinations = combinations.stream().flatMap(partial -> section.options().stream().map(candidate -> {
                Map<String, Leg> choice = new LinkedHashMap<>(partial);
                Fare override = candidate.mode() == section.chosen().mode() && request.legs() != null
                        && request.legs().containsKey(section.key()) ? request.legs().get(section.key()).override() : null;
                choice.put(section.key(), new Leg(candidate.mode(), override));
                return choice;
            })).toList();
        }
        for (Map<String, Leg> choices : combinations) {
            List<Mode> modes = choices.values().stream().map(Leg::mode).toList();
            if (modes.equals(alternatives.get(0).modes())) continue;
            alternatives.add(option(modes.toString(), resolve(options, choices, null, trip.party(), trip.start())));
        }
        return new RouteResults(trip, selected, roundTrip(selected), List.copyOf(alternatives),
                Recommendations.routes(alternatives));
    }

    private static RouteOption option(String id, Journey journey) {
        return new RouteOption(id, journey.sections().stream().map(s -> s.chosen().mode()).toList(),
                journey, roundTrip(journey));
    }

    private static BigDecimal roundTrip(Journey journey) {
        return journey.cost().totalKrw().multiply(BigDecimal.valueOf(2));
    }

    public Journey betweenCities(String origin, String destination, Party party, LocalDate date) {
        return resolve(routes.options(origin, destination, date), Map.of(), null, party, date);
    }

    public Journey loop(String destination, List<Attraction> stops, Map<String, Leg> legs,
                        Mode preferred, Party party, LocalDate date) {
        Place center = catalog.center(destination);
        String country = catalog.destination(destination).country();
        List<List<Segment>> options = new ArrayList<>();
        Place previous = center;
        for (Attraction stop : stops) {
            Place next = catalog.place(stop);
            options.add(routes.local(previous, next, country, date));
            previous = next;
        }
        if (!stops.isEmpty()) options.add(routes.local(previous, center, country, date));
        return resolve(options, legs, preferred, party, date);
    }

    private Journey resolve(List<List<Segment>> options, Map<String, Leg> overrides,
                            Mode preferred, Party party, LocalDate date) {
        Map<String, Leg> choices = overrides == null ? Map.of() : overrides;
        List<Section> sections = new ArrayList<>();
        for (List<Segment> legs : options) {
            if (legs.isEmpty()) throw new TravelProviders.Unavailable("이동 구간 정보를 확인할 수 없습니다.", null);
            Segment first = legs.get(0);
            String key = first.from().id() + "~" + first.to().id();
            Leg choice = choices.get(key);
            Segment chosen = legs.stream().filter(leg -> leg.mode() == (choice == null ? preferred : choice.mode()))
                    .findFirst().orElse(first);
            if (choice != null) {
                if (chosen.mode() != choice.mode()) {
                    throw new IllegalArgumentException("선택할 수 없는 교통수단입니다: " + key);
                }
                if (choice.override() != null) {
                    chosen = new Segment(chosen.from(), chosen.to(), chosen.mode(), chosen.kilometers(),
                            chosen.minutes(), chosen.transfers(), choice.override(), Source.MANUAL, Instant.now());
                }
            }
            Item cost = pricing.item(chosen.from().name() + " → " + chosen.to().name(),
                    chosen.fare(), chosen.source(), party, date);
            sections.add(new Section(key, legs, chosen, cost));
        }
        if (!sections.stream().map(Section::key).toList().containsAll(choices.keySet())) {
            throw new IllegalArgumentException("현재 경로에 없는 이동 구간입니다.");
        }
        return new Journey(List.copyOf(sections), pricing.total(sections.stream().map(Section::cost).toList(), party),
                sections.stream().map(s -> s.chosen().kilometers()).reduce(BigDecimal.ZERO, BigDecimal::add),
                sections.stream().mapToInt(s -> s.chosen().minutes()).sum(),
                sections.stream().mapToInt(s -> s.chosen().transfers()).sum());
    }
}
