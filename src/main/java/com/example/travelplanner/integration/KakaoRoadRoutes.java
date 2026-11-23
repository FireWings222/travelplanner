package com.example.travelplanner.integration;

import com.example.travelplanner.dto.Travel.*;
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
import java.util.List;

@Component
class KakaoRoadRoutes {
    private static final String ENDPOINT = "https://apis-navi.kakaomobility.com/v1/directions";
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper json;
    private final String apiKey;

    /** 자동차 전용 키를 우선 사용합니다. 별도 키가 없으면 같은 앱에 설정한 카카오맵 REST 키를 사용합니다. */
    KakaoRoadRoutes(ObjectMapper json, @Value("${KAKAO_MOBILITY_REST_API_KEY:}") String mobilityKey,
                    @Value("${KAKAO_MAP_REST_API_KEY:}") String mapKey) {
        this.json = json;
        this.apiKey = (mobilityKey.isBlank() ? mapKey : mobilityKey).strip();
    }

    /** 같은 출발지와 도착지의 택시·자가용 거리, 시간, 운임과 지도 좌표를 요청 한 번으로 가져옵니다. */
    List<Segment> enrich(List<Segment> choices) {
        if (apiKey.isEmpty() || Thread.currentThread().isInterrupted()) return choices;
        Place from = choices.get(0).from();
        Place to = choices.get(0).to();
        URI uri = URI.create(ENDPOINT + "?origin=" + from.longitude() + "," + from.latitude()
                + "&destination=" + to.longitude() + "," + to.latitude() + "&summary=false");
        try {
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(3))
                    .header("Authorization", "KakaoAK " + apiKey).GET().build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) return apply(choices, json.readTree(response.body()), Instant.now());
        } catch (IOException | JacksonException | IllegalArgumentException ignored) {
            // 조회에 실패하면 준비한 선택지만 유지합니다. 인증 정보가 포함된 요청은 기록하지 않습니다.
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        return choices;
    }

    /** 도로 경로의 미터·초를 km·분으로 바꾸고 택시 운임은 차량 한 대의 공동 요금으로 적용합니다. */
    static List<Segment> apply(List<Segment> choices, JsonNode response, Instant checkedAt) {
        JsonNode route = response.path("routes").path(0);
        if (route.path("result_code").asInt(-1) != 0) return choices;
        JsonNode summary = route.path("summary");
        int seconds = summary.path("duration").asInt(-1);
        int meters = summary.path("distance").asInt(-1);
        if (seconds < 0 || meters < 0) return choices;
        int taxi = summary.path("fare").path("taxi").asInt(-1);
        int minutes = (int) ((seconds + 59L) / 60);
        BigDecimal kilometers = BigDecimal.valueOf(meters, 3);
        List<List<RoutePoint>> geometry = roadLines(route);
        return choices.stream().map(choice -> {
            if (choice.mode() != Mode.CAR && choice.mode() != Mode.TAXI) return choice;
            Fare fare = choice.mode() == Mode.TAXI && taxi >= 0 ? new Fare(null, null, BigDecimal.valueOf(taxi)) : null;
            return new Segment(choice.from(), choice.to(), choice.mode(), kilometers, minutes, 0,
                    fare, Source.KAKAO_MOBILITY, checkedAt, null, List.of(), geometry);
        }).toList();
    }

    /**
     * 상세 응답의 sections[].roads[].vertexes는 경도와 위도가 번갈아 나오는 배열입니다.
     * 인접한 도로가 동일한 접점을 공유할 때만 하나의 선으로 합칩니다.
     * 중간 좌표가 누락되면 선을 분리해 지도에서 빈 구간을 임의의 직선으로 메우지 않습니다.
     */
    private static List<List<RoutePoint>> roadLines(JsonNode route) {
        List<List<RoutePoint>> lines = new ArrayList<>();
        List<RoutePoint> previous = List.of();
        for (JsonNode section : route.path("sections")) {
            for (JsonNode road : section.path("roads")) {
                JsonNode vertices = road.path("vertexes");
                if (vertices.size() < 4 || vertices.size() % 2 != 0) {
                    previous = List.of();
                    continue;
                }
                List<RoutePoint> line = new ArrayList<>();
                for (int i = 0; i < vertices.size(); i += 2) {
                    line.add(new RoutePoint(vertices.path(i + 1).asDouble(Double.NaN), vertices.path(i).asDouble(Double.NaN)));
                }
                if (!line.stream().allMatch(RoutePoint::valid)) {
                    previous = List.of();
                    continue;
                }
                if (!previous.isEmpty() && previous.get(previous.size() - 1).equals(line.get(0))) {
                    previous.addAll(line.subList(1, line.size()));
                } else {
                    lines.add(line);
                    previous = line;
                }
            }
        }
        return lines.stream().map(List::copyOf).toList();
    }
}
