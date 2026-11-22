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
import java.util.List;

@Component
class KakaoRoadRoutes {
    private static final String ENDPOINT = "https://apis-navi.kakaomobility.com/v1/directions";
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper json;
    private final String apiKey;

    /** 자동차 경로 조회 전용 REST 키를 받습니다. 이 값은 브라우저에 전달하지 않습니다. */
    KakaoRoadRoutes(ObjectMapper json, @Value("${KAKAO_MOBILITY_REST_API_KEY:}") String apiKey) {
        this.json = json;
        this.apiKey = apiKey.strip();
    }

    /** 같은 출발지와 도착지의 택시·자가용 정보를 도로 경로 요청 한 번으로 계산합니다. */
    List<Segment> enrich(List<Segment> choices) {
        if (apiKey.isEmpty() || Thread.currentThread().isInterrupted()) return choices;
        Place from = choices.get(0).from();
        Place to = choices.get(0).to();
        URI uri = URI.create(ENDPOINT + "?origin=" + from.longitude() + "," + from.latitude()
                + "&destination=" + to.longitude() + "," + to.latitude() + "&summary=true");
        try {
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(3))
                    .header("Authorization", "KakaoAK " + apiKey).GET().build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) return apply(choices, json.readTree(response.body()), Instant.now());
        } catch (IOException | JacksonException | IllegalArgumentException ignored) {
            // 호출이 실패하면 기존 샘플을 표시합니다. 인증 정보가 포함된 요청은 기록하지 않습니다.
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
        return choices.stream().map(choice -> {
            if (choice.mode() != Mode.CAR && choice.mode() != Mode.TAXI) return choice;
            Fare fare = choice.mode() == Mode.TAXI && taxi >= 0 ? new Fare(null, null, BigDecimal.valueOf(taxi)) : null;
            return new Segment(choice.from(), choice.to(), choice.mode(), kilometers, minutes, 0,
                    fare, Source.KAKAO_MOBILITY, checkedAt);
        }).toList();
    }
}
