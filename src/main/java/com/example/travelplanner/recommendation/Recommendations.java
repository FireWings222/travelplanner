package com.example.travelplanner.recommendation;

import com.example.travelplanner.service.JourneyService.RouteOption;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Recommendations {
    /** 경로 추천을 제공하는 정적 유틸리티의 직접 생성을 막습니다. */
    private Recommendations() {}

    public record RoutePick(String optionId, String reason) {}

    /** JourneyService의 경로 후보를 비교해 시간·비용·환승 기준으로 추천을 만듭니다. */
    public static Map<String, RoutePick> routes(List<RouteOption> candidates) {
        if (candidates.isEmpty() || candidates.get(0).journey().sections().isEmpty()) return Map.of();
        Map<String, RoutePick> picks = new LinkedHashMap<>();
        List<RouteOption> timed = candidates.stream().filter(x -> x.journey().minutes() != null).toList();
        add(picks, "가장 짧은 이동", timed, Comparator.comparingInt(x -> x.journey().minutes()), "편도 이동 시간 최소");
        List<RouteOption> transferKnown = candidates.stream().filter(x -> x.journey().transfers() != null).toList();
        add(picks, "가장 적은 환승", transferKnown, Comparator.comparingInt((RouteOption x) -> x.journey().transfers())
                .thenComparing(x -> x.journey().minutes(), Comparator.nullsLast(Integer::compare)), "환승 횟수 최소, 동률이면 이동 시간 우선");
        List<RouteOption> priced = candidates.stream().filter(x -> x.journey().cost().isComplete()).toList();
        if (!priced.isEmpty()) {
            add(picks, "가장 저렴한 이동", priced, Comparator.comparing(x -> x.journey().cost().totalKrw()),
                    "전체 요금이 확인된 후보 중 일행 편도 교통비 최소");
        }
        List<RouteOption> balanced = timed.stream().filter(x -> x.journey().cost().isComplete()).toList();
        add(picks, "비용과 시간 균형", balanced, Comparator.comparing(x -> x.journey().cost().totalKrw()
                .add(BigDecimal.valueOf(100L * x.journey().minutes()))),
                "요금과 시간이 확인된 후보 중 일행 편도 교통비 + 이동 1분당 100원 점수 최소");
        return picks;
    }

    /** 추천 기준 하나에 맞는 경로를 골라 비교 화면에 표시할 이유와 함께 추가합니다. */
    private static void add(Map<String, RoutePick> picks, String name, List<RouteOption> choices,
                            Comparator<RouteOption> criteria, String reason) {
        choices.stream().min(criteria).ifPresent(choice -> picks.put(name, new RoutePick(choice.id(), reason)));
    }
}
