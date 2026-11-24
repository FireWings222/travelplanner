package com.example.travelplanner.integration;

import com.example.travelplanner.dto.Travel.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OdsayRoutesTest {
    private final ObjectMapper json = new ObjectMapper();
    private final Instant checkedAt = Instant.parse("2026-11-22T00:00:00Z");
    private final Segment sample = new Segment(new Place("cju", "제주공항", 33.510, 126.493),
            new Place("jeju", "제주시 중심", 33.500, 126.531), Mode.AIRPORT_BUS,
            BigDecimal.valueOf(4), 20, 0, new Fare(BigDecimal.TEN, BigDecimal.ONE, null),
            Source.UNCONFIRMED, checkedAt);

    @Test
    void addsIntercityAndExpressBusesAlongsideKakaoTransit() {
        var from = new Place("seoul", "서울", 37.57, 126.98);
        var to = new Place("gangwon", "강릉", 37.75, 128.89);
        var response = json.readTree("""
                {"result":{"searchType":1,"path":[
                  {"pathType":12,"info":{"totalTime":150,"totalPayment":21000,"totalDistance":200000},
                   "subPath":[{"trafficType":5,"startName":"서울고속버스터미널","endName":"강릉고속버스터미널",
                    "sectionTime":150,"distance":200000,"startX":0,"startY":37.5}]},
                  {"pathType":12,"info":{"totalTime":165,"totalPayment":19500,"totalDistance":205000},
                   "subPath":[{"trafficType":6,"startName":"동서울터미널","endName":"강릉시외버스터미널",
                    "sectionTime":165,"distance":205000,"startX":"127.09","startY":37.54,
                    "endX":128.89,"endY":37.75}]},
                  {"pathType":11,"info":{"totalTime":110,"totalPayment":28000,"totalDistance":220000}}]}}
                """);
        var buses = OdsayRoutes.readIntercityBuses(from, to, response, checkedAt);
        assertEquals(List.of(Mode.EXPRESS_BUS, Mode.INTERCITY_BUS), buses.stream().map(Segment::mode).toList());
        var intercity = buses.get(1);
        assertEquals(165, intercity.minutes());
        assertEquals(BigDecimal.valueOf(19500), intercity.fare().adult());
        assertNull(intercity.fare().child());
        assertEquals("동서울터미널", intercity.boardings().get(0).start());
        assertEquals("강릉시외버스터미널", intercity.boardings().get(0).end());
        assertEquals(new RoutePoint(37.54, 127.09), intercity.boardings().get(0).terminalFrom());
        assertEquals(new RoutePoint(37.75, 128.89), intercity.boardings().get(0).terminalTo());
        assertNull(buses.get(0).boardings().get(0).terminalFrom());
        assertNull(buses.get(0).boardings().get(0).terminalTo());
        assertTrue(intercity.geometry().isEmpty());

        var odsay = mock(OdsayRoutes.class);
        var kakao = mock(KakaoMapRoutes.class);
        var road = mock(KakaoRoadRoutes.class);
        when(road.enrich(anyList())).thenAnswer(call -> call.getArgument(0));
        when(kakao.transit(from, to)).thenReturn(List.of(new Segment(from, to, Mode.CITY_BUS,
                BigDecimal.TEN, 20, 0, null, Source.KAKAO_ROUTING, checkedAt)));
        when(odsay.intercityBuses(from, to)).thenReturn(buses);
        var lookup = new RouteLookup(odsay, road, kakao);
        var options = lookup.options("seoul", "gangwon", from, to).get(0);
        assertEquals(List.of(Mode.CITY_BUS, Mode.CAR, Mode.TAXI, Mode.EXPRESS_BUS, Mode.INTERCITY_BUS),
                options.stream().map(Segment::mode).toList());
        verify(odsay, never()).enrich(anyList());
    }

    /** 터미널 접근은 지도 좌표를 가진 도보·대중교통 후보를 시간 순으로 반환합니다. */
    @Test
    void usesDrawableRoutesToTheTerminal() {
        var odsay = mock(OdsayRoutes.class);
        var kakao = mock(KakaoMapRoutes.class);
        var road = mock(KakaoRoadRoutes.class);
        var from = new RoutePoint(37.38, 127.12);
        var terminal = new RoutePoint(37.41, 127.13);
        var start = new Place("access-start", "출발", from.latitude(), from.longitude());
        var end = new Place("access-end", "도착", terminal.latitude(), terminal.longitude());
        var geometry = List.of(List.of(from, new RoutePoint(37.4, 127.13), terminal));
        var walk = new Segment(start, end, Mode.WALK, BigDecimal.TEN, 36, 0, null,
                Source.KAKAO_ROUTING, checkedAt, null, List.of(), geometry);
        var bus = new Segment(start, end, Mode.CITY_BUS, BigDecimal.TEN, 22, 0, null,
                Source.KAKAO_ROUTING, checkedAt, null, List.of(), geometry);
        var withoutGeometry = new Segment(start, end, Mode.SUBWAY, BigDecimal.TEN, 12, 0, null,
                Source.KAKAO_ROUTING, checkedAt);
        when(kakao.walk(start, end)).thenReturn(java.util.Optional.of(walk));
        when(kakao.transit(start, end)).thenReturn(List.of(withoutGeometry, bus));

        var lookup = new RouteLookup(odsay, road, kakao);
        assertEquals(List.of(bus, walk), lookup.terminalAccess(from, terminal));
        assertEquals(List.of(), lookup.terminalAccess(terminal, terminal));
        verifyNoInteractions(road, odsay);
    }

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
