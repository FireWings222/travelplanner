package com.example.travelplanner.integration;

import com.example.travelplanner.dto.Travel.*;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class SampleRoutes implements TravelProviders.Routes {
    private static final Instant CHECKED = Instant.parse("2026-11-20T00:00:00Z");
    private static final Place INCHEON = new Place("icn", "인천국제공항", 37.460, 126.441);
    private static final Place GIMPO = new Place("gmp", "김포국제공항", 37.558, 126.791);
    private static final Place GIMHAE = new Place("pus", "김해국제공항", 35.179, 128.938);
    private static final Place JEJU = new Place("cju", "제주국제공항", 33.510, 126.493);
    private static final Place KANSAI = new Place("kix", "간사이국제공항", 34.435, 135.244);
    private static final Place NARITA = new Place("nrt", "나리타국제공항", 35.773, 140.391);
    private final TravelProviders.Catalog catalog;

    public SampleRoutes(TravelProviders.Catalog catalog) { this.catalog = catalog; }

    @Override
    public List<List<Segment>> options(String origin, String destination, LocalDate date) {
        if (origin.equals(destination)) return List.of();
        Place from = catalog.center(origin);
        Place to = catalog.center(destination);
        boolean koreanOrigin = country(origin).equals("KR");
        boolean koreanDestination = country(destination).equals("KR");
        if (koreanOrigin != koreanDestination || origin.equals("jeju") || destination.equals("jeju")) {
            boolean domestic = koreanOrigin && koreanDestination;
            Place departure = airport(origin, domestic);
            Place arrival = airport(destination, domestic);
            List<Segment> access = airportAccess(from, departure, country(origin));
            List<Segment> egress = airportAccess(arrival, to, country(destination));
            int km = kilometers(departure, arrival);
            Segment flight = perPerson(departure, arrival, Mode.FLIGHT, km,
                    45 + km / 12, 0, Currency.KRW, domestic ? 58000 : 185000);
            return List.of(access, List.of(flight), egress);
        }
        int km = kilometers(from, to);
        if (!koreanOrigin && km < 60) return List.of(local(from, to, "JP", date));

        Currency currency = koreanOrigin ? Currency.KRW : Currency.JPY;
        List<Segment> options = new ArrayList<>();
        options.add(perPerson(from, to, koreanOrigin ? Mode.KTX : Mode.TRAIN,
                km, 22 + km / 3, 0, currency, koreanOrigin ? 59800 : 14200));
        if (koreanOrigin) {
            options.add(perPerson(from, to, Mode.SRT, km, 38 + km / 3, 1, currency, 52000));
            options.add(perPerson(from, to, Mode.TRAIN, km, 45 + km, 0, currency, 28600));
            options.add(perPerson(from, to, Mode.INTERCITY_BUS, km, 35 + km, 0, currency, 25800));
        }
        options.add(perPerson(from, to, Mode.EXPRESS_BUS, km, 35 + km, 0, currency, koreanOrigin ? 36500 : 4900));
        options.add(shared(from, to, Mode.CAR, km, 22 + km, currency, koreanOrigin ? 72000 : 12500));
        return List.of(List.copyOf(options));
    }

    @Override
    public List<Segment> local(Place from, Place to, String country, LocalDate date) {
        int km = kilometers(from, to);
        boolean korea = country.equals("KR");
        Currency currency = korea ? Currency.KRW : Currency.JPY;
        List<Segment> choices = new ArrayList<>();
        if (!isJeju(from, to)) {
            choices.add(perPerson(from, to, km > 20 ? Mode.TRAIN : Mode.SUBWAY, km, 12 + km * 2,
                    km > 8 ? 1 : 0, currency, korea ? 1450 + km * 30 : 190 + km * 12));
        }
        choices.add(perPerson(from, to, Mode.CITY_BUS, km, 9 + km * 3, km > 14 ? 1 : 0,
                currency, korea ? 1500 + km * 20 : 210 + km * 8));
        choices.add(shared(from, to, Mode.TAXI, km, 6 + km * 2, currency, korea ? 4800 + km * 800 : 650 + km * 330));
        choices.add(shared(from, to, Mode.RENTAL_CAR, km, 10 + km * 2, currency, korea ? 5000 + km * 400 : 750 + km * 130));
        if (km <= 8) choices.add(perPerson(from, to, Mode.WALK, km, km * 14, 0, currency, 0));
        return List.copyOf(choices);
    }

    private static boolean isJeju(Place from, Place to) {
        return from.latitude() < 34 && to.latitude() < 34 && from.longitude() < 127 && to.longitude() < 127;
    }

    private List<Segment> airportAccess(Place from, Place to, String country) {
        boolean korea = country.equals("KR");
        int km = kilometers(from, to);
        Currency currency = korea ? Currency.KRW : Currency.JPY;
        Segment bus = perPerson(from, to, Mode.AIRPORT_BUS, km, 12 + km * 2, 0, currency, korea ? 11000 : 1600);
        Segment taxi = shared(from, to, Mode.TAXI, km, 10 + km, currency, korea ? 4700 + km * 1100 : 900 + km * 300);
        if (isJeju(from, to)) return List.of(bus, taxi);
        Segment rail = perPerson(from, to, Mode.AIRPORT_RAIL, km, 15 + km, 0, currency, korea ? 4900 : 1200);
        return List.of(rail, bus, taxi);
    }

    private String country(String id) {
        return catalog.destinations().stream().filter(city -> city.id().equals(id)).findFirst().orElseThrow().country();
    }

    private static Place airport(String id, boolean domestic) {
        return switch (id) {
            case "seoul" -> domestic ? GIMPO : INCHEON;
            case "busan" -> GIMHAE;
            case "jeju" -> JEJU;
            case "osaka", "kyoto" -> KANSAI;
            case "tokyo" -> NARITA;
            default -> throw new IllegalArgumentException("공항 정보가 없습니다.");
        };
    }

    private static Segment perPerson(Place from, Place to, Mode mode, int km, int minutes, int transfers,
                                     Currency currency, int adult) {
        return new Segment(from, to, mode, BigDecimal.valueOf(km), minutes, transfers,
                new Fare(currency, BigDecimal.valueOf(adult), BigDecimal.valueOf(adult * 7L / 10), null),
                Source.SAMPLE, CHECKED);
    }

    private static Segment shared(Place from, Place to, Mode mode, int km, int minutes, Currency currency, int fare) {
        return new Segment(from, to, mode, BigDecimal.valueOf(km), minutes, 0,
                new Fare(currency, null, null, BigDecimal.valueOf(fare)), Source.SAMPLE, CHECKED);
    }

    private static int kilometers(Place from, Place to) {
        double latitude = Math.toRadians(to.latitude() - from.latitude());
        double longitude = Math.toRadians(to.longitude() - from.longitude());
        double a = Math.pow(Math.sin(latitude / 2), 2) + Math.cos(Math.toRadians(from.latitude()))
                * Math.cos(Math.toRadians(to.latitude())) * Math.pow(Math.sin(longitude / 2), 2);
        return Math.max(1, (int) Math.round(6371 * 2 * Math.asin(Math.sqrt(a))));
    }
}
