package com.example.travelplanner.integration;

import com.example.travelplanner.dto.Travel.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OdsayRoutesTest {
    private final ObjectMapper json = new ObjectMapper();
    private final Instant checkedAt = Instant.parse("2026-11-22T00:00:00Z");
    private final Segment sample = new Segment(new Place("cju", "제주공항", 33.510, 126.493),
            new Place("jeju", "제주시 중심", 33.500, 126.531), Mode.AIRPORT_BUS,
            BigDecimal.valueOf(4), 20, 0, new Fare(BigDecimal.TEN, BigDecimal.ONE, null),
            Source.UNCONFIRMED, checkedAt);

    /** ODsay 응답에서 요금·시간·지도 코드를 읽고 제공되지 않은 아동 운임을 비워 두는지 검증합니다. */
    @Test
    void usesProviderFareAndTimeWithoutInventingAChildFare() {
        var response = json.readTree("""
                {"result":{"searchType":0,"path":[{"pathType":2,"info":{
                  "totalTime":31,"payment":1200,"totalDistance":5630,
                  "busTransitCount":1,"subwayTransitCount":0,"mapObj":"12018:1:1:3"
                }}]}}
                """);
        var route = OdsayRoutes.apply(List.of(sample), response, checkedAt).get(0);
        assertEquals(Mode.AIRPORT_BUS, route.mode());
        assertEquals(Source.ODSAY, route.source());
        assertEquals(31, route.minutes());
        assertEquals(0, route.transfers());
        assertEquals(new BigDecimal("5.630"), route.kilometers());
        assertEquals(BigDecimal.valueOf(1200), route.fare().adult());
        assertNull(route.fare().child());
        assertEquals("12018:1:1:3", route.mapObject());
    }

    /** 혼합 경로의 전체 요금과 버스·지하철 탑승 순서를 같은 경로 응답에서 읽는지 확인합니다. */
    @Test
    void includesMixedRoutesAndKeepsBoardingOrder() {
        var response = json.readTree("""
                {"result":{"searchType":0,"path":[{"pathType":3,"info":{
                  "totalTime":38,"payment":1650,"totalDistance":8800,
                  "busTransitCount":1,"subwayTransitCount":1,"mapObj":"12:1:1:3@1:2:3:5"
                },"subPath":[
                  {"trafficType":3,"sectionTime":4},
                  {"trafficType":2,"startName":"정류장 A","endName":"정류장 B",
                   "sectionTime":10,"lane":[{"busNo":"101"},{"busNo":"102"}]},
                  {"trafficType":1,"startName":"역 A","endName":"역 B",
                   "sectionTime":20,"lane":[{"name":"1호선"}]}
                ]}]}}
                """);
        var routes = OdsayRoutes.apply(List.of(sample), response, checkedAt);
        assertEquals(sample, routes.get(0));
        var mixed = routes.get(1);
        assertEquals(Mode.BUS_SUBWAY, mixed.mode());
        assertEquals(38, mixed.minutes());
        assertEquals(BigDecimal.valueOf(1650), mixed.fare().adult());
        assertEquals(1, mixed.transfers());
        assertEquals(List.of(new Boarding(Mode.WALK, List.of(), mixed.from().name(), "정류장 A", 4),
                new Boarding(Mode.CITY_BUS, List.of("101", "102"), "정류장 A", "정류장 B", 10),
                new Boarding(Mode.SUBWAY, List.of("1호선"), "역 A", "역 B", 20)), mixed.boardings());
    }

    /** 지도에 선을 그릴 때 구간 경계와 위도·경도 순서가 유지되는지 검증합니다. */
    @Test
    void keepsPolylineSectionsSeparateAndConvertsCoordinatesToLatitudeLongitude() {
        var response = json.readTree("""
                {"result":{"lane":[{"section":[
                  {"graphPos":[{"x":126.493,"y":33.510},{"x":126.500,"y":33.509}]},
                  {"graphPos":[{"x":126.520,"y":33.502},{"x":126.531,"y":33.500}]},
                  {"graphPos":[{"x":999,"y":999},{}]}
                ]}]}}
                """);
        assertEquals(List.of(
                List.of(new OdsayRoutes.Point(33.510, 126.493), new OdsayRoutes.Point(33.509, 126.500)),
                List.of(new OdsayRoutes.Point(33.502, 126.520), new OdsayRoutes.Point(33.500, 126.531))),
                OdsayRoutes.readLines(response));
    }

    /** ODsay가 오류를 반환해도 기존 샘플 교통수단을 사용할 수 있는지 검증합니다. */
    @Test
    void preservesTheSampleWhenTheProviderReturnsAnError() {
        var response = json.readTree("""
                {"error":[{"code":"500","message":"ApiKeyAuthFailed"}]}
                """);
        assertEquals(List.of(sample), OdsayRoutes.apply(List.of(sample), response, checkedAt));
    }
}
