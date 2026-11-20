package com.example.travelplanner.integration;

import com.example.travelplanner.dto.Travel.*;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

@Component
public class SampleTours implements TravelProviders.Tours {
    private final Tour tour = new Tour("kyoto-day", new TourInput("kyoto", Platform.KLOOK, "교토 명소 일일 버스투어",
            null, null, LocalTime.of(8, 30), LocalTime.of(17, 30), "교토역", "교토역",
            "샘플 취소 조건 · 출발 24시간 전까지 취소 가능", Mode.CITY_BUS, true, true, false, true,
            List.of("fushimi", "kiyomizu", "arashiyama", "kinkaku"),
            List.of(price(2026, 1, 1, 2027, 3, 31, 65000, 49000),
                    price(2027, 4, 1, 2030, 12, 31, 72000, 52000)),
            new Fare(Currency.KRW, BigDecimal.ZERO, BigDecimal.ZERO, null), null),
            Source.SAMPLE, Instant.parse("2026-11-20T00:00:00Z"));

    @Override public Platform platform() { return Platform.KLOOK; }

    @Override
    public List<Tour> search(String destination) {
        return destination.equals("kyoto") || destination.equals("osaka") ? List.of(tour) : List.of();
    }

    private static Price price(int year, int month, int day, int endYear, int endMonth, int endDay, int adult, int child) {
        return new Price(LocalDate.of(year, month, day), LocalDate.of(endYear, endMonth, endDay),
                new Fare(Currency.KRW, BigDecimal.valueOf(adult), BigDecimal.valueOf(child), null));
    }
}
