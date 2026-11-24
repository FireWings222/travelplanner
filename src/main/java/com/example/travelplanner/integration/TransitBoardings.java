package com.example.travelplanner.integration;

import com.example.travelplanner.dto.Travel.*;

import java.util.ArrayList;
import java.util.List;

final class TransitBoardings {
    private TransitBoardings() {}

    /**
     * 카카오 대중교통 응답에서 도보가 빠질 수 있어 탑승선이 시작하고 끝나는 실제 좌표를 비교합니다.
     * 빠진 연결은 양 끝 좌표만 보관하며 지도가 열릴 때 도보 길찾기를 요청합니다. 기존 도보 단계는 그대로 사용합니다.
     */
    static List<Boarding> withKakaoWalkConnections(List<Boarding> steps, Place from, Place to) {
        List<Boarding> result = new ArrayList<>();
        RoutePoint previousEnd = new RoutePoint(from.latitude(), from.longitude());
        boolean previousWalking = false;
        for (int i = 0; i < steps.size(); i++) {
            Boarding step = steps.get(i);
            boolean walking = step.mode() == Mode.WALK;
            if (!walking && !previousWalking && !step.path().isEmpty()) {
                addConnection(result, previousEnd, step.path().get(0));
            }
            if (walking && step.path().isEmpty()) {
                RoutePoint nextStart = i == steps.size() - 1 ? new RoutePoint(to.latitude(), to.longitude())
                        : steps.get(i + 1).path().stream().findFirst().orElse(null);
                step = new Boarding(step.mode(), step.lines(), step.start(), step.end(), step.minutes(), step.meters(),
                        step.path(), previousEnd, nextStart);
            }
            result.add(step);
            previousEnd = step.path().isEmpty() ? null : step.path().get(step.path().size() - 1);
            previousWalking = walking;
        }
        if (!steps.isEmpty() && !previousWalking) addConnection(result, previousEnd, new RoutePoint(to.latitude(), to.longitude()));
        return withWalkEndpoints(result, from, to);
    }

    private static void addConnection(List<Boarding> result, RoutePoint start, RoutePoint end) {
        if (start == null || (Math.abs(start.latitude() - end.latitude()) < 0.00001
                && Math.abs(start.longitude() - end.longitude()) < 0.00001)) return;
        result.add(new Boarding(Mode.WALK, List.of(), null, null, null, null, List.of(), start, end));
    }

    /**
     * 도보 시작·끝 이름이 생략되면 앞 단계의 하차 지점과 다음 단계의 승차 지점을 사용합니다.
     * 첫 단계와 마지막 단계는 여행의 출발·도착 장소를 사용합니다. 인접한 지점의 이름도 없으면 미확인으로 남깁니다.
     */
    static List<Boarding> withWalkEndpoints(List<Boarding> steps, Place from, Place to) {
        List<Boarding> result = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            Boarding step = steps.get(i);
            if (step.mode() != Mode.WALK) {
                result.add(step);
                continue;
            }
            String start = i == 0 ? from.name() : steps.get(i - 1).end();
            String end = i == steps.size() - 1 ? to.name() : steps.get(i + 1).start();
            result.add(new Boarding(step.mode(), step.lines(), knownOr(step.start(), start), knownOr(step.end(), end),
                    step.minutes(), step.meters(), step.path(), step.walkFrom(), step.walkTo()));
        }
        return List.copyOf(result);
    }

    private static String knownOr(String name, String fallback) { return name == null || name.isBlank() ? fallback : name; }
}
