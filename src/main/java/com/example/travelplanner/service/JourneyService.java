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
import java.util.*;

@Service
public class JourneyService {
    private final CatalogService catalog;
    private final TravelProviders.Routes routes;
    private final Pricing pricing;

    /** 도시 카탈로그, 교통 경로 제공자, 요금 계산기를 연결합니다. */
    public JourneyService(CatalogService catalog, TravelProviders.Routes routes, Pricing pricing) {
        this.catalog = catalog;
        this.routes = routes;
        this.pricing = pricing;
    }

    public record Section(String key, List<Segment> options, Segment chosen, Item cost) {}
    public record Journey(List<Section> sections, Total cost, BigDecimal kilometers, Integer minutes, Integer transfers) {}
    public record RouteOption(String id, List<Mode> modes, Journey journey, BigDecimal roundTripKrw) {}
    public record RouteResults(Trip trip, Journey selected, BigDecimal roundTripKrw, List<RouteOption> alternatives,
                               Map<String, Recommendations.RoutePick> recommendations) {}

    /**
     * 도시 간 이동 선택을 해석하고 화면에 표시할 교통수단 조합과 추천을 계산합니다.
     *
     * TravelApi의 POST /api/routes/compare에서 호출합니다. request.trip은 도시·기간·인원이고 request.legs는 사용자가 변경한 구간입니다.
     *
     * 각 구간의 교통수단을 조합한 뒤 권장 경로와 함께 반환합니다. 직접 입력한 요금은 같은 구간에서 같은 교통수단을 사용하는 후보에만 적용합니다.
     */
    public RouteResults compare(RouteRequest request) {
        Trip trip = catalog.validateTrip(request.trip());
        Place from = catalog.point(trip.origin(), trip.originPlace());
        Place to = catalog.point(trip.destination(), trip.destinationPlace());
        List<List<Segment>> options = routes.options(trip.origin(), trip.destination(), from, to);
        Journey selected = resolve(options, request.legs(), trip.preferredMode(), trip.party());
        List<RouteOption> alternatives = new ArrayList<>();
        // 화면은 첫 번째 후보를 현재 선택으로 사용합니다. 동일한 교통수단 조합은 대안 목록에 다시 넣지 않습니다.
        alternatives.add(option("selected", selected));
        List<Map<String, Leg>> combinations = List.of(Map.of());
        for (Section section : selected.sections()) {
            combinations = combinations.stream().flatMap(partial -> section.options().stream().map(candidate -> {
                Map<String, Leg> choice = new LinkedHashMap<>(partial);
                Leg requested = request.legs() == null ? null : request.legs().get(section.key());
                Fare override = requested != null && requested.mode() == candidate.mode() ? requested.override() : null;
                choice.put(section.key(), new Leg(candidate.mode(), override));
                return choice;
            })).toList();
        }
        for (Map<String, Leg> choices : combinations) {
            List<Mode> modes = choices.values().stream().map(Leg::mode).toList();
            if (modes.equals(alternatives.get(0).modes())) continue;
            alternatives.add(option(modes.toString(), resolve(options, choices, null, trip.party())));
        }
        return new RouteResults(trip, selected, roundTrip(selected), List.copyOf(alternatives),
                Recommendations.routes(alternatives));
    }

    /**
     * 계산된 여정을 경로 비교 표에 사용할 식별자, 교통수단 목록, 왕복 비용과 함께 묶습니다.
     *
     * id는 추천 결과가 이 경로를 참조하는 값입니다. modes는 선택한 구간 순서대로 추출해 화면에서 이동 방식을 표시하는 데 사용합니다.
     */
    private static RouteOption option(String id, Journey journey) {
        return new RouteOption(id, journey.sections().stream().map(s -> s.chosen().mode()).toList(),
                journey, roundTrip(journey));
    }

    /**
     * 경로 비교 화면의 왕복 예상 비용에 사용할 편도 비용의 두 배를 반환합니다.
     *
     * 반환값은 방향이 다른 귀경편을 따로 조회하지 않고 편도 요금을 두 번 합한 원화 예상 값입니다.
     */
    private static BigDecimal roundTrip(Journey journey) {
        return journey.cost().totalKrw().multiply(BigDecimal.valueOf(2));
    }

    /**
     * 투어 방문지를 기준으로 자유여행 견적을 낼 때 도시 중심에서 출발해 다시 돌아오는 경로를 계산합니다.
     */
    public Journey loop(String destination, List<Attraction> stops, Map<String, Leg> legs,
                        Mode preferred, Party party) {
        Place center = catalog.center(destination);
        List<List<Segment>> options = new ArrayList<>();
        Place previous = center;
        for (Attraction stop : stops) {
            Place next = catalog.place(stop);
            options.add(routes.local(previous, next));
            previous = next;
        }
        if (!stops.isEmpty()) options.add(routes.local(previous, center));
        return resolve(options, legs, preferred, party);
    }

    /** 내 일정에서는 첫 장소에서 출발해 마지막 장소까지 방문 순서대로 이동합니다. 한 장소만 있으면 교통비는 없습니다. */
    public Journey visits(List<Attraction> stops, Map<String, Leg> legs, Mode preferred, Party party) {
        List<List<Segment>> options = new ArrayList<>();
        Map<String, List<Segment>> byPair = new HashMap<>();
        for (int index = 1; index < stops.size(); index++) {
            Place from = catalog.place(stops.get(index - 1));
            Place to = catalog.place(stops.get(index));
            // 같은 방향으로 다시 이동할 때 조회 결과를 재사용하되, 견적에서는 구간마다 비용을 계산합니다.
            options.add(byPair.computeIfAbsent(from.id() + "~" + to.id(), key -> {
                if (from.id().equals(to.id())) return List.of(new Segment(from, to, Mode.WALK,
                        BigDecimal.ZERO, 0, 0, Pricing.free(), Source.CATALOG, null));
                return routes.local(from, to);
            }));
        }
        return resolve(options, legs, preferred, party);
    }

    /** 이전 일정의 자동 도시 중심 출발·복귀 설정만 제거하고, 방문지 사이에 직접 적용한 선택은 보존합니다. */
    public Map<String, Leg> visitLegs(String destination, List<Attraction> stops, Map<String, Leg> legs) {
        if (legs == null || legs.isEmpty() || stops.isEmpty()) return legs;
        Map<String, Leg> current = new LinkedHashMap<>(legs);
        current.remove(destination + "~" + stops.get(0).id());
        current.remove(stops.get(stops.size() - 1).id() + "~" + destination);
        return current;
    }

    /**
     * 구간별 교통수단과 직접 입력 요금을 적용하고 거리·시간·환승·비용을 합칩니다.
     *
     * options의 바깥 목록은 이동 순서이고 안쪽 목록은 해당 구간의 교통수단 후보입니다.
     * 첫 구간의 키는 출발지ID~도착지ID이며, 같은 방향이 반복되면 ~2, ~3을 붙여 선택을 구분합니다.
     *
     * 조회 가능한 교통수단이 바뀌면 첫 후보를 사용합니다. 이전 수단에 직접 입력한 요금은 새 수단에 적용하지 않습니다.
     *
     * 직접 요금을 입력해도 기존 거리·시간·노선 코드는 이어받습니다. 반환값에는 각 구간의 선택 결과와 일행 전체 합계가 함께 들어갑니다.
     */
    private Journey resolve(List<List<Segment>> options, Map<String, Leg> overrides,
                            Mode preferred, Party party) {
        Map<String, Leg> choices = overrides == null ? Map.of() : overrides;
        Map<String, Integer> occurrences = new HashMap<>();
        List<Section> sections = new ArrayList<>();
        for (List<Segment> legs : options) {
            if (legs.isEmpty()) throw new TravelProviders.Unavailable("이동 구간 정보를 확인할 수 없습니다.", null);
            Segment first = legs.get(0);
            String pair = first.from().id() + "~" + first.to().id();
            int occurrence = occurrences.merge(pair, 1, Integer::sum);
            String key = occurrence == 1 ? pair : pair + "~" + occurrence;
            Leg choice = choices.get(key);
            Mode requestedMode = choice == null ? preferred : choice.mode();
            Segment chosen = legs.stream().filter(leg -> leg.mode() == requestedMode)
                    .findFirst().orElse(first);
            if (choice != null && chosen.mode() == choice.mode() && choice.override() != null
                    && !chosen.from().id().equals(chosen.to().id())) {
                chosen = new Segment(chosen.from(), chosen.to(), chosen.mode(), chosen.kilometers(),
                        chosen.minutes(), chosen.transfers(), choice.override(), Source.MANUAL, Instant.now(),
                        chosen.mapObject(), chosen.boardings(), chosen.geometry());
            }
            Item cost = pricing.item(chosen.from().name() + " → " + chosen.to().name(),
                    chosen.fare(), chosen.source(), party);
            sections.add(new Section(key, legs, chosen, cost));
        }
        if (!sections.stream().map(Section::key).toList().containsAll(choices.keySet())) {
            throw new IllegalArgumentException("현재 경로에 없는 이동 구간입니다.");
        }
        List<Segment> chosen = sections.stream().map(Section::chosen).toList();
        // 일부 구간이 미확인이면 확인된 값만 더한 수치를 전체 거리나 전체 시간처럼 표시하지 않습니다.
        var kilometers = chosen.stream().anyMatch(s -> s.kilometers() == null) ? null
                : chosen.stream().map(Segment::kilometers).reduce(BigDecimal.ZERO, BigDecimal::add);
        Integer minutes = chosen.stream().anyMatch(s -> s.minutes() == null) ? null
                : chosen.stream().mapToInt(Segment::minutes).sum();
        Integer transfers = chosen.stream().anyMatch(s -> s.transfers() == null) ? null
                : chosen.stream().mapToInt(Segment::transfers).sum();
        return new Journey(List.copyOf(sections), pricing.total(sections.stream().map(Section::cost).toList(), party),
                kilometers, minutes, transfers);
    }
}
