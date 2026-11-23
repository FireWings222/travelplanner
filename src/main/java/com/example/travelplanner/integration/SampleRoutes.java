package com.example.travelplanner.integration;

import com.example.travelplanner.dto.Travel.*;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class SampleRoutes implements TravelProviders.Routes {
    private static final Instant CHECKED = Instant.parse("2026-11-20T00:00:00Z");
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

    /** 실제 대중교통과 자동차 경로를 조회하는 제공자를 연결합니다. */
    public SampleRoutes(OdsayRoutes odsay, KakaoRoadRoutes road) {
        this.odsay = odsay;
        this.road = road;
    }

    /**
     * 경로 비교에 사용할 도시 간 교통수단을 구간별로 구성합니다. 제주행은 공항 접근과 항공편으로 나눕니다.
     *
     * JourneyService의 도시 간 비교에서 호출합니다. 출발지와 목적지가 같으면 빈 목록을 반환하고, 제주가 포함되면 공항 접근·국내선·공항에서 목적지 순으로 구간을 구성합니다.
     */
    @Override
    public List<List<Segment>> options(String origin, String destination, Place from, Place to) {
        if (samePoint(from, to)) return List.of();
        boolean fromJeju = origin.equals("jeju");
        boolean toJeju = destination.equals("jeju");
        if (fromJeju != toJeju) {
            Place departure = fromJeju ? JEJU : gateway(from);
            Place arrival = toJeju ? JEJU : gateway(to);
            int km = kilometers(departure, arrival);
            Segment flight = perPerson(departure, arrival, Mode.FLIGHT, km, 45 + km / 12, 0, 58000);
            List<List<Segment>> sections = new ArrayList<>();
            if (!samePoint(from, departure)) {
                sections.add(legacyCenter(from) ? airportAccess(from, departure) : pointToPoint(from, departure));
            }
            sections.add(List.of(flight));
            if (!samePoint(arrival, to)) {
                sections.add(legacyCenter(to) ? airportAccess(arrival, to) : pointToPoint(arrival, to));
            }
            return List.copyOf(sections);
        }
        if (!legacyCenter(from) || !legacyCenter(to)) {
            return List.of(pointToPoint(from, to));
        }
        int km = kilometers(from, to);
        return List.of(road.enrich(List.of(
                perPerson(from, to, Mode.KTX, km, 22 + km / 3, 0, 59800),
                perPerson(from, to, Mode.TRAIN, km, 45 + km, 0, 28600),
                perPerson(from, to, Mode.INTERCITY_BUS, km, 35 + km, 0, 25800),
                perPerson(from, to, Mode.EXPRESS_BUS, km, 35 + km, 0, 36500),
                shared(from, to, Mode.CAR, km, 22 + km, 72000))));
    }

    private List<Segment> pointToPoint(Place from, Place to) {
        int km = kilometers(from, to);
        List<Segment> choices = new ArrayList<>();
        int driveMinutes = Math.max(5, 10 + km * (km <= 30 ? 2 : 1));
        choices.add(sampleCar(from, to, km, driveMinutes));
        choices.add(new Segment(from, to, Mode.TAXI, BigDecimal.valueOf(km), driveMinutes, 0,
                null, Source.SAMPLE, CHECKED));
        if (km <= 8) choices.add(perPerson(from, to, Mode.WALK, km, km * 14, 0, 0));
        return road.enrich(odsay.enrich(choices));
    }

    private static boolean legacyCenter(Place place) {
        return switch (place.id()) {
            case "seoul", "busan", "jeju" -> true;
            default -> false;
        };
    }

    /**
     * 관광지 사이의 교통수단 후보를 만들고 조회 가능한 버스·지하철 구간을 ODsay 데이터로 보완합니다.
     *
     * 일정과 자유여행 비교에 사용할 지역 이동 후보를 만듭니다. 거리와 제주 여부에 따라 철도·도보 후보를 조정한 뒤 ODsay에서 일치하는 대중교통 결과를 적용합니다.
     */
    @Override
    public List<Segment> local(Place from, Place to) {
        if (from.id().startsWith("kakao-") || to.id().startsWith("kakao-")) return pointToPoint(from, to);
        int km = kilometers(from, to);
        List<Segment> choices = new ArrayList<>();
        if (!isJeju(from, to)) {
            choices.add(perPerson(from, to, km > 20 ? Mode.TRAIN : Mode.SUBWAY, km, 12 + km * 2,
                    km > 8 ? 1 : 0, 1450 + km * 30));
        }
        choices.add(perPerson(from, to, Mode.CITY_BUS, km, 9 + km * 3, km > 14 ? 1 : 0,
                1500 + km * 20));
        choices.add(shared(from, to, Mode.TAXI, km, 6 + km * 2, 4800 + km * 800));
        choices.add(sampleCar(from, to, km, 10 + km * 2));
        choices.add(shared(from, to, Mode.RENTAL_CAR, km, 10 + km * 2, 5000 + km * 400));
        if (km <= 8) choices.add(perPerson(from, to, Mode.WALK, km, km * 14, 0, 0));
        return road.enrich(odsay.enrich(choices));
    }

    /** 두 지점이 제주 범위에 있는지 판단해 현지·공항 접근 후보에 철도를 넣을지 결정합니다. */
    private static boolean isJeju(Place from, Place to) {
        return from.latitude() < 34 && to.latitude() < 34 && from.longitude() < 127 && to.longitude() < 127;
    }

    /** 공항과 도시 중심 사이의 버스·철도·택시 후보를 만들고 ODsay 조회를 적용합니다. */
    private List<Segment> airportAccess(Place from, Place to) {
        int km = kilometers(from, to);
        Segment bus = perPerson(from, to, Mode.AIRPORT_BUS, km, 12 + km * 2, 0, 11000);
        Segment taxi = shared(from, to, Mode.TAXI, km, 10 + km, 4700 + km * 1100);
        Segment car = sampleCar(from, to, km, 10 + km);
        if (isJeju(from, to)) return road.enrich(odsay.enrich(List.of(bus, taxi, car)));
        Segment rail = perPerson(from, to, Mode.AIRPORT_RAIL, km, 15 + km, 0, 4900);
        return road.enrich(odsay.enrich(List.of(rail, bus, taxi, car)));
    }

    /** 제주행 또는 제주발 경로를 만들 때 사용할 국내 공항의 기준 좌표를 도시 ID로 찾습니다. */
    private static Place gateway(Place place) {
        return JEJU_GATEWAYS.stream().min(Comparator.comparingDouble(airport -> distance(place, airport))).orElseThrow();
    }

    /** 버스·철도·항공편처럼 인원수 기준으로 계산할 샘플 이동 구간을 만듭니다. */
    private static Segment perPerson(Place from, Place to, Mode mode, int km, int minutes, int transfers,
                                     int adult) {
        return new Segment(from, to, mode, BigDecimal.valueOf(km), minutes, transfers,
                new Fare(BigDecimal.valueOf(adult), BigDecimal.valueOf(adult * 7L / 10), null),
                Source.SAMPLE, CHECKED);
    }

    /** 택시나 자가용처럼 일행이 한 번 부담하는 샘플 이동 구간을 만듭니다. */
    private static Segment shared(Place from, Place to, Mode mode, int km, int minutes, int fare) {
        return new Segment(from, to, mode, BigDecimal.valueOf(km), minutes, 0,
                new Fare(null, null, BigDecimal.valueOf(fare)), Source.SAMPLE, CHECKED);
    }

    /** 자동차 API를 쓸 수 없을 때 표시할 자가용 시간 견적입니다. 운행비는 입력을 받습니다. */
    private static Segment sampleCar(Place from, Place to, int km, int minutes) {
        return new Segment(from, to, Mode.CAR, BigDecimal.valueOf(km), minutes, 0, null, Source.SAMPLE, CHECKED);
    }

    /** 실제 노선 거리가 없는 샘플 구간의 요금과 시간을 추정할 때 두 지점의 직선거리를 사용합니다. */
    private static int kilometers(Place from, Place to) {
        return Math.max(1, (int) Math.round(distance(from, to)));
    }

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
