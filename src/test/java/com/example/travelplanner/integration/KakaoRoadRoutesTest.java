package com.example.travelplanner.integration;

import com.example.travelplanner.dto.Travel.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KakaoRoadRoutesTest {
    private final ObjectMapper json = new ObjectMapper();
    private final Instant checkedAt = Instant.parse("2026-11-22T00:00:00Z");
    private final Place from = new Place("cju", "제주공항", 33.510, 126.493);
    private final Place to = new Place("jeju", "제주시 중심", 33.500, 126.531);

    /** 동일한 도로 응답을 두 수단에 적용하고 택시 요금만 공동 비용으로 사용하는지 검증합니다. */
    @Test
    void sharesRoadDurationAndDistanceWithoutApplyingTaxiFareToCar() {
        var response = json.readTree("""
                {"routes":[{"result_code":0,"summary":{
                  "duration":839,"distance":5230,"fare":{"taxi":8500,"toll":100}
                },"sections":[{"roads":[{"vertexes":[126.493,33.510,126.502,33.503,126.531,33.500]},
                  {"vertexes":[126.541,33.511,126.542]}]}]}]}
                """);
        var routes = KakaoRoadRoutes.apply(List.of(sample(Mode.TAXI), sample(Mode.CAR)), response, checkedAt);
        var taxi = routes.get(0);
        var car = routes.get(1);
        assertEquals(Source.KAKAO_MOBILITY, taxi.source());
        assertEquals(Source.KAKAO_MOBILITY, car.source());
        assertEquals(14, taxi.minutes());
        assertEquals(taxi.minutes(), car.minutes());
        assertEquals(new BigDecimal("5.230"), car.kilometers());
        assertEquals(BigDecimal.valueOf(8500), taxi.fare().shared());
        assertNull(car.fare());
        assertEquals(List.of(List.of(new RoutePoint(33.510, 126.493), new RoutePoint(33.503, 126.502),
                new RoutePoint(33.500, 126.531))), taxi.geometry());
        assertEquals(taxi.geometry(), car.geometry());
    }

    /** 자동차 길찾기에 성공하지 못했을 때 기존 샘플을 반환하는지 확인합니다. */
    @Test
    void keepsSamplesWhenRoadLookupReturnsAnError() {
        var choices = List.of(sample(Mode.TAXI), sample(Mode.CAR));
        var response = json.readTree("""
                {"routes":[{"result_code":104}]}
                """);
        assertEquals(choices, KakaoRoadRoutes.apply(choices, response, checkedAt));
    }

    /** 도로 응답 적용 전 화면에 사용하던 구간을 만듭니다. */
    private Segment sample(Mode mode) {
        return new Segment(from, to, mode, BigDecimal.valueOf(4), 20, 0,
                new Fare(null, null, BigDecimal.TEN), Source.UNCONFIRMED, checkedAt);
    }
}
