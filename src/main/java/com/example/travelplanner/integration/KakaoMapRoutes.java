package com.example.travelplanner.integration;

import com.example.travelplanner.dto.Travel.*;
import com.example.travelplanner.pricing.Pricing;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 카카오맵의 대중교통·도보 REST 응답을 요금 비교와 지도에서 함께 쓰는 구간으로 변환합니다. */
@Component
public class KakaoMapRoutes {
    private static final String ENDPOINT = "https://dapi.kakao.com/v2/routing/";
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper json;
    private final String apiKey;

    KakaoMapRoutes(ObjectMapper json,
                   @Value("${KAKAO_MAP_REST_API_KEY:${KAKAO_MOBILITY_REST_API_KEY:}}") String apiKey) {
        this.json = json;
        this.apiKey = apiKey.strip();
    }

    List<Segment> transit(Place from, Place to) {
        return request("publictraffic", from, to)
                .map(reply -> transitSegments(from, to, reply, Instant.now())).orElseGet(List::of);
    }

    Optional<Segment> walk(Place from, Place to) {
        return request("walk", from, to).flatMap(reply -> walkingSegment(from, to, reply, Instant.now()));
    }

    /** 화면에 선택한 대중교통의 도보 연결 좌표만 가져옵니다. 키는 일반 도보 조회와 동일합니다. */
    public List<List<RoutePoint>> walkingGeometry(RoutePoint from, RoutePoint to) {
        if (!from.valid() || !to.valid()) throw new IllegalArgumentException("도보 경로의 좌표를 확인하세요.");
        if (from.equals(to)) return List.of();
        return walk(new Place("walk-start", "출발", from.latitude(), from.longitude()),
                new Place("walk-end", "도착", to.latitude(), to.longitude()))
                .map(Segment::geometry).orElseGet(List::of);
    }

    /** REST 키는 서버에서만 사용합니다. 실패 응답은 빈 조회 결과로 반환해 다른 교통수단에 영향을 주지 않습니다. */
    private Optional<JsonNode> request(String endpoint, Place from, Place to) {
        if (apiKey.isEmpty() || Thread.currentThread().isInterrupted()) return Optional.empty();
        URI uri = URI.create(ENDPOINT + endpoint + "?start_x=" + from.longitude() + "&start_y=" + from.latitude()
                + "&end_x=" + to.longitude() + "&end_y=" + to.latitude());
        try {
            var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(3))
                    .header("Authorization", "KakaoAK " + apiKey).GET().build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) return Optional.of(json.readTree(response.body()));
        } catch (IOException | JacksonException | IllegalArgumentException ignored) {
            // 전송이 실패하거나 응답 형식이 맞지 않으면 가상 경로를 만들지 않습니다.
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        return Optional.empty();
    }

    /** 유형별 첫 유효 경로를 보관합니다. 요금, 탑승 안내와 노선 좌표를 동일한 응답 경로에서 읽습니다. */
    static List<Segment> transitSegments(Place from, Place to, JsonNode reply, Instant checkedAt) {
        if (!"OK".equals(reply.path("status").asText(""))) return List.of();
        Map<Mode, Segment> choices = new LinkedHashMap<>();
        for (JsonNode route : reply.path("routes")) {
            JsonNode props = route.path("properties");
            Mode mode = switch (props.path("type").asText("")) {
                case "BUS" -> Mode.CITY_BUS;
                case "SUBWAY" -> Mode.SUBWAY;
                case "BUS_AND_SUBWAY" -> Mode.BUS_SUBWAY;
                default -> null;
            };
            if (mode == null || choices.containsKey(mode)) continue;
            List<List<RoutePoint>> lines = new ArrayList<>();
            List<Boarding> boardings = new ArrayList<>();
            for (JsonNode step : route.path("steps")) {
                List<RoutePoint> path = stepLine(step);
                if (!path.isEmpty()) lines.add(path);
                boarding(step.path("properties"), path).ifPresent(boardings::add);
            }
            int adult = props.path("fare").path("value").asInt(-1);
            Fare fare = adult < 0 ? null : new Fare(BigDecimal.valueOf(adult), null, null);
            segment(from, to, mode, props, nonNegative(props.path("transfers").asInt(-1)), fare,
                    checkedAt, TransitBoardings.withKakaoWalkConnections(boardings, from, to), lines)
                    .ifPresent(routeSegment -> choices.put(mode, routeSegment));
        }
        return List.copyOf(choices.values());
    }

    /** 도보 응답은 route.legs[].steps[] 안에 좌표를 담습니다. 조회에 성공한 도보 구간은 교통비가 없습니다. */
    static Optional<Segment> walkingSegment(Place from, Place to, JsonNode reply, Instant checkedAt) {
        // 문서의 성공 예시처럼 status가 생략되기도 하므로 실제 route 객체의 존재도 확인합니다.
        if (!"OK".equals(reply.path("status").asText("OK"))) return Optional.empty();
        JsonNode route = reply.path("route");
        List<List<RoutePoint>> lines = new ArrayList<>();
        for (JsonNode leg : route.path("legs")) {
            for (JsonNode step : leg.path("steps")) {
                List<RoutePoint> path = stepLine(step);
                if (!path.isEmpty()) lines.add(path);
            }
        }
        return segment(from, to, Mode.WALK, route.path("properties"), 0, Pricing.free(), checkedAt, List.of(), lines);
    }

    /** 지도는 단계별 좌표를 별도 선으로 그립니다. 공급자가 좌표를 생략한 부분을 임의로 이어서 그리지 않습니다. */
    private static List<RoutePoint> stepLine(JsonNode step) {
        List<RoutePoint> line = new ArrayList<>();
        for (JsonNode xy : step.path("path").path("points")) {
            if (!xy.isArray() || xy.size() != 2) return List.of();
            var point = new RoutePoint(xy.path(1).asDouble(Double.NaN), xy.path(0).asDouble(Double.NaN));
            if (!point.valid()) return List.of();
            line.add(point);
        }
        return line.size() < 2 ? List.of() : List.copyOf(line);
    }

    /** 도보 단계도 탑승 단계 사이의 순서 그대로 반환해 UI 목록과 지도 선의 구분에 함께 사용합니다. */
    private static Optional<Boarding> boarding(JsonNode props, List<RoutePoint> path) {
        Mode mode = switch (props.path("type").asText("")) {
            case "BUS" -> Mode.CITY_BUS;
            case "SUBWAY" -> Mode.SUBWAY;
            case "WALKING" -> Mode.WALK;
            default -> null;
        };
        if (mode == null) return Optional.empty();
        JsonNode stops = props.path("stops");
        List<String> lines = new ArrayList<>();
        for (JsonNode vehicle : props.path("vehicles")) {
            String name = vehicle.path("name").asText("");
            if (!name.isBlank() && !lines.contains(name)) lines.add(name);
        }
        return Optional.of(new Boarding(mode, List.copyOf(lines), stops.path(0).path("name").asText(null),
                stops.path(stops.size() - 1).path("name").asText(null), minutes(props.path("time").asInt(-1)),
                nonNegative(props.path("distance").asInt(-1)), path));
    }

    private static Optional<Segment> segment(Place from, Place to, Mode mode, JsonNode props, Integer transfers,
                                             Fare fare, Instant checkedAt, List<Boarding> boardings,
                                             List<List<RoutePoint>> lines) {
        int meters = props.path("totalDistance").asInt(-1);
        Integer duration = minutes(props.path("totalTime").asInt(-1));
        if (meters < 0 || duration == null || lines.isEmpty()) return Optional.empty();
        return Optional.of(new Segment(from, to, mode, BigDecimal.valueOf(meters, 3), duration, transfers, fare,
                Source.KAKAO_ROUTING, checkedAt, null, List.copyOf(boardings), List.copyOf(lines)));
    }

    private static Integer minutes(int seconds) { return seconds < 0 ? null : (int) ((seconds + 59L) / 60); }

    private static Integer nonNegative(int value) { return value < 0 ? null : value; }
}
