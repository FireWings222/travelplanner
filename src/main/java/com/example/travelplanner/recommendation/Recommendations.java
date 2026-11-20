package com.example.travelplanner.recommendation;

import com.example.travelplanner.service.JourneyService.RouteOption;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Recommendations {
    private Recommendations() {}

    public record RoutePick(String optionId, String reason) {}

    public static Map<String, RoutePick> routes(List<RouteOption> candidates) {
        if (candidates.isEmpty() || candidates.get(0).journey().sections().isEmpty()) return Map.of();
        Map<String, RoutePick> picks = new LinkedHashMap<>();
        add(picks, "가장 짧은 이동", candidates, Comparator.comparingInt(x -> x.journey().minutes()), "편도 이동 시간 최소");
        add(picks, "가장 적은 환승", candidates, Comparator.comparingInt((RouteOption x) -> x.journey().transfers())
                .thenComparingInt(x -> x.journey().minutes()), "환승 횟수 최소, 동률이면 이동 시간 우선");
        if (candidates.stream().allMatch(x -> x.journey().cost().isComplete())) {
            add(picks, "가장 저렴한 이동", candidates, Comparator.comparing(x -> x.journey().cost().totalKrw()),
                    "전체 인원 편도 교통비 최소");
            add(picks, "비용과 시간 균형", candidates, Comparator.comparing(x -> x.journey().cost().totalKrw()
                    .add(BigDecimal.valueOf(100L * x.journey().minutes()))),
                    "전체 인원 편도 교통비 + 이동 1분당 100원 점수 최소");
        }
        return picks;
    }

    private static void add(Map<String, RoutePick> picks, String name, List<RouteOption> choices,
                            Comparator<RouteOption> criteria, String reason) {
        choices.stream().min(criteria).ifPresent(choice -> picks.put(name, new RoutePick(choice.id(), reason)));
    }
}
