package com.example.travelplanner.integration;

import com.example.travelplanner.dto.Travel.*;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class RouteLookup implements TravelProviders.Routes {
    private static final Place GIMPO = new Place("gmp", "김포국제공항", 37.558, 126.791);
    private static final Place GIMHAE = new Place("pus", "김해국제공항", 35.179, 128.938);
    private static final Place JEJU = new Place("cju", "제주국제공항", 33.510, 126.493);
    private static final List<Place> JEJU_GATEWAYS = List.of(GIMPO, GIMHAE,
            new Place("cjj", "청주국제공항", 36.716, 127.499),
            new Place("tae", "대구국제공항", 35.894, 128.658),
            new Place("kwj", "광주공항", 35.126, 126.810),
            new Place("rsu", "여수공항", 34.843, 127.617),
            new Place("usn", "울산공항", 35.593, 129.352));
    private final OdsayRoutes odsay;
    private final KakaoRoadRoutes road;
    private final KakaoMapRoutes mapRoutes;

    public RouteLookup(OdsayRoutes odsay, KakaoRoadRoutes road, KakaoMapRoutes mapRoutes) {
        this.odsay = odsay;
        this.road = road;
        this.mapRoutes = mapRoutes;
    }

    /** 제주와 육지 사이는 공항을 경유하는 연결을 안내합니다. 항공편과 운임은 별도 조회가 필요합니다. */
    @Override
    public List<List<Segment>> options(String origin, String destination, Place from, Place to) {
        if (samePoint(from, to)) return List.of();
        boolean fromJeju = origin.equals("jeju");
        boolean toJeju = destination.equals("jeju");
        if (fromJeju == toJeju) return List.of(local(from, to));

        Place departure = fromJeju ? JEJU : gateway(from);
        Place arrival = toJeju ? JEJU : gateway(to);
        List<List<Segment>> sections = new ArrayList<>();
        if (!samePoint(from, departure)) sections.add(local(from, departure));
        sections.add(List.of(unconfirmed(departure, arrival, Mode.FLIGHT)));
        if (!samePoint(arrival, to)) sections.add(local(arrival, to));
        return List.copyOf(sections);
    }

    /** 도로 수단은 수동으로 요금을 입력할 수 있습니다. 대중교통은 조회 응답에 포함된 수단을 추가합니다. */
    @Override
    public List<Segment> local(Place from, Place to) {
        List<Segment> prepared = List.of(unconfirmed(from, to, Mode.CAR), unconfirmed(from, to, Mode.TAXI));
        List<Segment> transit = mapRoutes.transit(from, to);
        // 카카오에서 지도에 그릴 수 있는 대중교통 경로를 얻지 못하면 기존 ODsay 연동을 사용합니다.
        List<Segment> choices = new ArrayList<>(road.enrich(transit.isEmpty() ? odsay.enrich(prepared) : prepared));
        choices.addAll(transit);
        mapRoutes.walk(from, to).ifPresent(choices::add);
        return choices.stream()
                .sorted(Comparator.comparing(segment -> segment.source() == Source.UNCONFIRMED))
                .toList();
    }

    /** 두 지점과 수단만 표시하고 외부에서 확인하지 못한 운임, 거리, 시간과 환승은 비워 둡니다. */
    private static Segment unconfirmed(Place from, Place to, Mode mode) {
        return new Segment(from, to, mode, null, null, null, null, Source.UNCONFIRMED, null);
    }

    private static Place gateway(Place point) {
        return JEJU_GATEWAYS.stream().min(Comparator.comparingDouble(airport -> distance(point, airport))).orElseThrow();
    }

    /** 같은 ID이거나 좌표 차이가 약 5m 미만이면 이동 구간을 만들지 않습니다. 공항이 출발지인 경우에도 같은 기준을 씁니다. */
    private static boolean samePoint(Place from, Place to) {
        return from.id().equals(to.id()) || distance(from, to) < 0.005;
    }

    private static double distance(Place from, Place to) {
        double latitude = Math.toRadians(to.latitude() - from.latitude());
        double longitude = Math.toRadians(to.longitude() - from.longitude());
        double a = Math.pow(Math.sin(latitude / 2), 2) + Math.cos(Math.toRadians(from.latitude()))
                * Math.cos(Math.toRadians(to.latitude())) * Math.pow(Math.sin(longitude / 2), 2);
        return 6371 * 2 * Math.asin(Math.sqrt(a));
    }
}
