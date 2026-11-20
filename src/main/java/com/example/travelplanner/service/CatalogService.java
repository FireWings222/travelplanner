package com.example.travelplanner.service;

import com.example.travelplanner.dto.Travel.*;
import com.example.travelplanner.integration.TravelProviders;
import org.springframework.stereotype.Service;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class CatalogService {
    private static final Set<String> KANSAI = Set.of("osaka", "kyoto");
    private final TravelProviders.Catalog catalog;

    public CatalogService(TravelProviders.Catalog catalog) { this.catalog = catalog; }

    public List<Destination> search(String query) {
        String term = normalize(query);
        return catalog.destinations().stream().filter(city ->
                normalize(city.name() + " " + city.keywords() + " " + city.id()).contains(term)).toList();
    }

    public List<Attraction> attractions(String destination, String query) {
        Destination city = destination == null || destination.isBlank() ? null : destination(destination);
        return catalog.attractions().stream()
                .filter(sight -> city == null || sight.destinationId().equals(city.id()))
                .filter(sight -> normalize(sight.name() + " " + sight.description()).contains(normalize(query)))
                .toList();
    }

    public Destination destination(String value) {
        String term = normalize(value);
        List<Destination> exact = catalog.destinations().stream().filter(city ->
                normalize(city.id()).equals(term) || normalize(city.name()).equals(term)).toList();
        List<Destination> matches = exact.isEmpty() ? search(value) : exact;
        if (term.isEmpty() || matches.size() != 1) {
            throw new IllegalArgumentException("목록에서 도시를 하나 선택해 주세요: " + value);
        }
        return matches.get(0);
    }

    public Attraction attraction(String id) {
        return catalog.attractions().stream().filter(sight -> sight.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("관광지를 찾을 수 없습니다: " + id));
    }

    public Place center(String id) { return catalog.center(id); }

    public Place place(Attraction attraction) {
        return new Place(attraction.id(), attraction.name(), attraction.latitude(), attraction.longitude());
    }

    public Trip validateTrip(Trip trip) {
        long days = ChronoUnit.DAYS.between(trip.start(), trip.end()) + 1;
        if (days < 1 || days > 30) throw new IllegalArgumentException("여행 기간은 1일 이상 30일 이하여야 합니다.");
        return new Trip(destination(trip.origin()).id(), destination(trip.destination()).id(),
                trip.start(), trip.end(), trip.party(), trip.budget(), trip.preferredMode(), trip.type());
    }

    public List<Attraction> stops(String destination, List<String> ids) {
        if (ids.stream().distinct().count() != ids.size()) {
            throw new IllegalArgumentException("같은 날에 동일한 관광지를 두 번 추가할 수 없습니다.");
        }
        Destination city = destination(destination);
        return ids.stream().map(this::attraction).peek(attraction -> {
            if (!attraction.destinationId().equals(city.id())
                    && !(KANSAI.contains(city.id()) && KANSAI.contains(attraction.destinationId()))) {
                throw new IllegalArgumentException("선택한 도시의 샘플 이동 범위를 벗어나는 관광지가 포함되어 있습니다.");
            }
        }).toList();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }
}
