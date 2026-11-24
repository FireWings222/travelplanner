package com.example.travelplanner.integration;

import com.example.travelplanner.dto.Travel.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KakaoMapRoutesTest {
    private final ObjectMapper json = new ObjectMapper();
    private final Place from = new Place("from", "출발", 37.39, 127.11);
    private final Place to = new Place("to", "도착", 37.42, 127.13);
    private final Instant checkedAt = Instant.parse("2026-11-23T00:00:00Z");

    @Test
    void keepsTransitStepsSeparateAndReadsTheFareFromTheSameRoute() {
        var reply = json.readTree("""
                {"status":"OK","routes":[{"properties":{"type":"BUS_AND_SUBWAY","totalDistance":5120,
                 "totalTime":721,"transfers":1,"fare":{"value":1550}},"steps":[
                  {"properties":{"type":"WALKING","distance":90,"time":65},"path":{"points":[[127.11,37.39],[127.1102,37.391]]}},
                  {"properties":{"type":"BUS","time":250,"stops":[{"name":"첫 정류장"},{"name":"환승 정류장"}],
                    "vehicles":[{"name":"76"}]},"path":{"points":[[127.11,37.39],[127.12,37.40],[127.12,37.41]]}},
                  {"properties":{"type":"WALKING"},"path":{"points":[[null,37.41],[127.13,37.42]]}},
                  {"properties":{"type":"SUBWAY","stops":[{"name":"환승역"},{"name":"마지막 역"}]},
                    "path":{"points":[[127.13,37.41],[127.13,37.42]]}},
                  {"properties":{"type":"WALKING"},"path":{"points":[[127.13,37.42],[127.131,37.421]]}}
                 ]}]}
                """);
        Segment result = KakaoMapRoutes.transitSegments(from, to, reply, checkedAt).get(0);
        assertEquals(Mode.BUS_SUBWAY, result.mode());
        assertEquals(13, result.minutes());
        assertEquals(new BigDecimal("1550"), result.fare().adult());
        assertNull(result.fare().child());
        assertEquals(List.of(Mode.WALK, Mode.CITY_BUS, Mode.WALK, Mode.SUBWAY, Mode.WALK),
                result.boardings().stream().map(Boarding::mode).toList());
        Boarding firstWalk = result.boardings().get(0);
        assertEquals(from.name(), firstWalk.start());
        assertEquals("첫 정류장", firstWalk.end());
        assertEquals(90, firstWalk.meters());
        assertEquals(2, firstWalk.minutes());
        assertEquals("환승 정류장", result.boardings().get(2).start());
        assertEquals("환승역", result.boardings().get(2).end());
        assertTrue(result.boardings().get(2).path().isEmpty());
        assertEquals("마지막 역", result.boardings().get(4).start());
        assertEquals(to.name(), result.boardings().get(4).end());
        assertEquals(List.of(
                List.of(new RoutePoint(37.39, 127.11), new RoutePoint(37.391, 127.1102)),
                List.of(new RoutePoint(37.39, 127.11), new RoutePoint(37.40, 127.12), new RoutePoint(37.41, 127.12)),
                List.of(new RoutePoint(37.41, 127.13), new RoutePoint(37.42, 127.13)),
                List.of(new RoutePoint(37.42, 127.13), new RoutePoint(37.421, 127.131))), result.geometry());
        assertEquals(result.geometry().get(0), firstWalk.path());
    }

    @Test
    void addsWalkConnectionsWhenTransitResponseOnlyHasVehiclePaths() {
        var reply = json.readTree("""
                {"status":"OK","routes":[{"properties":{"type":"BUS","totalDistance":5120,"totalTime":721},"steps":[
                  {"properties":{"type":"BUS","stops":[{"name":"승차"},{"name":"하차"}]},
                    "path":{"points":[[127.111,37.391],[127.129,37.419]]}}
                ]}]}
                """);
        var steps = KakaoMapRoutes.transitSegments(from, to, reply, checkedAt).get(0).boardings();
        assertEquals(List.of(Mode.WALK, Mode.CITY_BUS, Mode.WALK), steps.stream().map(Boarding::mode).toList());
        assertEquals(new RoutePoint(37.39, 127.11), steps.get(0).walkFrom());
        assertEquals(new RoutePoint(37.391, 127.111), steps.get(0).walkTo());
        assertEquals("승차", steps.get(0).end());
        assertEquals("하차", steps.get(2).start());
        assertEquals(new RoutePoint(37.419, 127.129), steps.get(2).walkFrom());
        assertEquals(new RoutePoint(37.42, 127.13), steps.get(2).walkTo());
        assertTrue(steps.get(0).path().isEmpty());
        assertNull(steps.get(0).minutes());
    }

    @Test
    void readsNestedWalkGeometryAndSkipsUnsuccessfulLookups() {
        var reply = json.readTree("""
                {"route":{"properties":{"totalDistance":300,"totalTime":61},"legs":[{"steps":[
                  {"path":{"points":[[127.11,37.39],[127.12,37.39],[127.12,37.40]]}}
                ]}]}}
                """);
        Segment walk = KakaoMapRoutes.walkingSegment(from, to, reply, checkedAt).orElseThrow();
        assertEquals(Mode.WALK, walk.mode());
        assertEquals(2, walk.minutes());
        assertEquals(BigDecimal.ZERO, walk.fare().adult());
        assertEquals(List.of(List.of(new RoutePoint(37.39, 127.11), new RoutePoint(37.39, 127.12),
                new RoutePoint(37.40, 127.12))), walk.geometry());
        assertTrue(KakaoMapRoutes.walkingSegment(from, to,
                json.readTree("{\"status\":\"TOO_FAR_AWAY\"}"), checkedAt).isEmpty());
        assertTrue(KakaoMapRoutes.transitSegments(from, to, json.readTree("{\"status\":\"NO_RESULTS\"}"), checkedAt).isEmpty());
    }
}
