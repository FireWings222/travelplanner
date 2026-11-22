package com.example.travelplanner.integration;

import com.example.travelplanner.dto.Travel.*;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

@Component
public class SampleTours implements TravelProviders.Tours {
    private final Tour tour = new Tour("jeju-day", new TourInput("jeju", Platform.KLOOK, "제주 동부 일일 버스투어",
            null, null, LocalTime.of(8, 30), LocalTime.of(17, 30), "제주시 중심", "제주시 중심",
            "샘플 취소 조건 · 출발 24시간 전까지 취소 가능", Mode.CITY_BUS, true, true, false, true,
            List.of("dongmun", "doldam", "gimnyeong", "seongsan"),
            List.of(price(2026, 1, 1, 2027, 3, 31, 65000, 49000),
                    price(2027, 4, 1, 2030, 12, 31, 72000, 52000)),
            new Fare(BigDecimal.ZERO, BigDecimal.ZERO, null), null),
            Source.SAMPLE, Instant.parse("2026-11-20T00:00:00Z"));

    /** TourService에서 샘플 상품을 필터링할 때 쓸 플랫폼 분류를 반환합니다. */
    @Override public Platform platform() { return Platform.KLOOK; }

    /** 투어 검색 요청이 제주 대상이면 샘플 일일투어를 반환합니다. */
    @Override
    public List<Tour> search(String destination) {
        return destination.equals("jeju") ? List.of(tour) : List.of();
    }

    /** 샘플 투어의 날짜 범위와 성인·아동 원화 요금을 한 가격 항목으로 묶습니다. */
    private static Price price(int year, int month, int day, int endYear, int endMonth, int endDay, int adult, int child) {
        return new Price(LocalDate.of(year, month, day), LocalDate.of(endYear, endMonth, endDay),
                new Fare(BigDecimal.valueOf(adult), BigDecimal.valueOf(child), null));
    }
}
