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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import com.example.travelplanner.exception.NotFound;

@Component
public class OdsayRoutes {
    private static final String ENDPOINT = "https://api.odsay.com/v1/api/";
    private static final Pattern MAP_OBJECT = Pattern.compile(
            "(?:0:0@)?[0-9]+:[12]:[0-9]+:[0-9]+(?:@[0-9]+:[12]:[0-9]+:[0-9]+)*");
    public record Point(double latitude, double longitude) {}
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper json;
    private final String apiKey;

    /** 서버 환경의 ODSAY_API_KEY와 응답 해석에 사용할 JSON 변환기를 받습니다. */
    OdsayRoutes(ObjectMapper json, @Value("${ODSAY_API_KEY:}") String apiKey) {
        this.json = json;
        this.apiKey = apiKey.strip();
    }

    /**
     * RouteLookup이 제공한 출발·도착 좌표로 길찾기를 요청하고 응답에 있는 대중교통 경로를 추가합니다.
     *
     * 목록 첫 항목에서 출발·도착 좌표를 읽고 도시 내 경로를 한 번 요청합니다.
     *
     * 서버 키가 없거나 조회 결과가 없으면 기존 선택지만 유지합니다.
     */
    List<Segment> enrich(List<Segment> samples) {
        if (apiKey.isEmpty() || Thread.currentThread().isInterrupted()) return samples;
        Place from = samples.get(0).from();
        Place to = samples.get(0).to();
        JsonNode response = search(from, to, 0);
        return response == null ? samples : apply(samples, response, Instant.now());
    }

    /** 한 번의 도시 간 조회에서 열차와 시외·고속버스를 찾습니다. */
    List<Segment> intercity(Place from, Place to) {
        if (apiKey.isEmpty() || Thread.currentThread().isInterrupted()) return List.of();
        JsonNode response = search(from, to, 1);
        return response == null ? List.of() : readIntercity(from, to, response, Instant.now());
    }

    private JsonNode search(Place from, Place to, int searchType) {
        return query("searchPubTransPathT?SX=" + from.longitude() + "&SY=" + from.latitude()
                + "&EX=" + to.longitude() + "&EY=" + to.latitude() + "&SearchType=" + searchType);
    }

    /** ODsay 도시 간 응답에서 역·터미널 사이의 시간, 운임과 탑승 순서를 읽습니다. */
    static List<Segment> readIntercity(Place from, Place to, JsonNode response, Instant checkedAt) {
        JsonNode result = response.path("result");
        if (result.path("searchType").asInt(-1) != 1) return List.of();
        Map<Mode, Segment> choices = new LinkedHashMap<>();
        for (JsonNode path : result.path("path")) {
            // 11은 열차, 12는 시외·고속버스입니다. 항공과 수단이 섞인 경로는 이 비교 대상에서 제외합니다.
            int pathType = path.path("pathType").asInt(-1);
            if (pathType != 11 && pathType != 12) continue;
            boolean train = pathType == 11;
            List<Boarding> boardings = new ArrayList<>();
            for (JsonNode leg : path.path("subPath")) {
                int trainType = leg.path("trainType").asInt(-1);
                int trafficType = leg.path("trafficType").asInt(-1);
                Mode mode = switch (trafficType) {
                    case 4 -> train ? trainMode(trainType) : null;
                    case 5 -> train ? null : Mode.EXPRESS_BUS;
                    case 6 -> train ? null : Mode.INTERCITY_BUS;
                    default -> null;
                };
                String departure = leg.path("startName").asText("");
                String arrival = leg.path("endName").asText("");
                if (mode == null || departure.isBlank() || arrival.isBlank()) {
                    boardings.clear();
                    break;
                }
                int minutes = leg.path("sectionTime").asInt(-1);
                int meters = leg.path("distance").asInt(-1);
                boardings.add(new Boarding(mode, mode == Mode.TRAIN ? trainName(trainType) : List.of(), departure, arrival,
                        minutes < 0 ? null : minutes, meters < 0 ? null : meters, List.of(), null, null,
                        terminalPoint(leg, "start"), terminalPoint(leg, "end")));
            }
            if (boardings.isEmpty()) continue;
            Mode first = boardings.get(0).mode();
            // 열차 종류가 섞인 경로는 기차로 묶되, 탑승 순서에는 각 열차의 종류를 남깁니다.
            boolean mixed = boardings.stream().anyMatch(boarding -> boarding.mode() != first);
            Mode mode = mixed ? (train ? Mode.TRAIN : Mode.INTERCITY_BUS) : first;
            JsonNode info = path.path("info");
            int minutes = info.path("totalTime").asInt(-1);
            int meters = info.path("totalDistance").asInt(-1);
            int payment = info.path("totalPayment").asInt(-1);
            if (minutes <= 0 || meters <= 0) continue;
            // 역·터미널 간 편도 일반 운임만 사용합니다. 응답에 없는 아동 운임은 비워 둡니다.
            Fare fare = payment < 0 ? null : new Fare(BigDecimal.valueOf(payment), null, null);
            choices.putIfAbsent(mode, new Segment(from, to, mode, BigDecimal.valueOf(meters, 3), minutes,
                    boardings.size() - 1, fare, Source.ODSAY, checkedAt, null, List.copyOf(boardings)));
        }
        return List.copyOf(choices.values());
    }

    /** ODsay 열차 코드에서 KTX와 SRT는 별도 후보로 분류하고 나머지는 기차로 묶습니다. */
    private static Mode trainMode(int type) {
        return switch (type) {
            case 1 -> Mode.KTX;
            case 8 -> Mode.SRT;
            default -> Mode.TRAIN;
        };
    }

    /** 일반열차 후보의 탑승 단계에서 표시할 열차 종류입니다. */
    private static List<String> trainName(int type) {
        return switch (type) {
            case 2 -> List.of("새마을");
            case 3 -> List.of("무궁화");
            case 4 -> List.of("누리로");
            case 5 -> List.of("통근");
            case 6 -> List.of("ITX");
            case 7 -> List.of("ITX-청춘");
            default -> List.of();
        };
    }

    /** ODsay의 X는 경도, Y는 위도입니다. 누락되거나 0으로 채워진 좌표는 생략합니다. */
    private static RoutePoint terminalPoint(JsonNode leg, String prefix) {
        RoutePoint point = new RoutePoint(leg.path(prefix + "Y").asDouble(Double.NaN),
                leg.path(prefix + "X").asDouble(Double.NaN));
        return point.valid() && point.latitude() != 0 && point.longitude() != 0 ? point : null;
    }

    /**
     * 지도 요청의 노선 코드를 검증하고 ODsay에서 같은 노선의 좌표를 받아 반환합니다.
     *
     * TravelApi의 GET /api/routes/geometry에서 호출합니다. mapObject는 앞서 조회한 이동 구간에 포함된 ODsay 노선 코드입니다.
     *
     * 노선 코드의 형식·길이를 확인하고 loadLane을 요청합니다. 좌표가 없으면 404로, 외부 요청 오류는 503으로 이어져 지도 영역에 안내가 표시됩니다.
     */
    public List<List<Point>> geometry(String mapObject) {
        if (mapObject.length() > 1024 || !MAP_OBJECT.matcher(mapObject).matches()) {
            throw new IllegalArgumentException("노선 식별자 형식을 확인해 주세요.");
        }
        if (apiKey.isEmpty()) throw new NotFound("ODsay 서버 키를 설정해 주세요.");
        String code = mapObject.startsWith("0:0@") ? mapObject : "0:0@" + mapObject;
        JsonNode response = query("loadLane?mapObject=" + URLEncoder.encode(code, StandardCharsets.UTF_8));
        if (response == null || response.hasNonNull("error")) {
            throw new TravelProviders.Unavailable("ODsay 노선 조회에 실패했습니다.", null);
        }
        List<List<Point>> lines = readLines(response);
        if (lines.isEmpty()) throw new NotFound("이 구간의 노선 좌표가 없습니다.");
        return lines;
    }

    /**
     * ODsay 좌표의 x와 y를 경도와 위도로 해석하고 끊어진 구간을 각각의 지도 선으로 묶습니다.
     *
     * 입력은 ODsay의 result.lane[].section[].graphPos[] 구조입니다. x는 경도, y는 위도로 바꿔 카카오맵이 사용할 좌표로 반환합니다.
     *
     * 구간을 별도 배열로 유지해 화면이 떨어진 노선 사이에 임의의 선을 그리지 않게 합니다. 좌표가 두 점 미만인 구간은 제외합니다.
     */
    static List<List<Point>> readLines(JsonNode response) {
        List<List<Point>> lines = new ArrayList<>();
        for (JsonNode lane : response.path("result").path("lane")) {
            for (JsonNode section : lane.path("section")) {
                List<Point> line = new ArrayList<>();
                for (JsonNode point : section.path("graphPos")) {
                    double latitude = point.path("y").asDouble(Double.NaN);
                    double longitude = point.path("x").asDouble(Double.NaN);
                    if (Math.abs(latitude) <= 90 && Math.abs(longitude) <= 180) {
                        line.add(new Point(latitude, longitude));
                    }
                }
                if (line.size() >= 2) lines.add(line);
            }
        }
        return lines;
    }

    /**
     * ODsay의 길찾기와 노선 좌표 조회가 공유하는 HTTP 호출입니다. 실패하면 호출자에게 결과가 없음을 알립니다.
     *
     * path는 서버 코드가 만든 ODsay API 경로와 쿼리입니다. 환경변수에서 받은 Server 키를 이 요청에만 붙입니다.
     *
     * HTTP 200 응답을 JSON으로 해석합니다. 호출 또는 응답 해석에 실패하면 null을 반환하고, 스레드가 중단된 경우 중단 표시를 복구합니다.
     */
    private JsonNode query(String path) {
        URI uri = URI.create(ENDPOINT + path + "&apiKey=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(3)).GET().build();
        try {
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return json.readTree(response.body());
            }
        } catch (IOException | JacksonException ignored) {
            // 조회에 실패하면 기존 후보를 유지합니다. 키가 포함된 요청 주소는 로그에 남기지 않습니다.
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        return null;
    }

    /**
     * 한 번의 도시 내 응답에서 수단별 첫 유효한 경로를 선택합니다.
     * 기존 버스·지하철 선택지는 조회 결과로 보완하고, 혼합 경로는 제공자가 반환한 경우에만 추가합니다.
     */
    static List<Segment> apply(List<Segment> samples, JsonNode response, Instant checkedAt) {
        JsonNode result = response.path("result");
        if (result.path("searchType").asInt(-1) != 0) return samples;
        JsonNode paths = result.path("path");
        Place from = samples.get(0).from();
        Place to = samples.get(0).to();
        List<Segment> choices = new ArrayList<>();
        boolean hasRail = false;
        boolean hasBus = false;
        for (Segment sample : samples) {
            int type = switch (sample.mode()) {
                case SUBWAY, AIRPORT_RAIL -> 1;
                case CITY_BUS, AIRPORT_BUS -> 2;
                default -> 0;
            };
            hasRail |= type == 1;
            hasBus |= type == 2;
            Segment route = type == 0 ? null : firstRoute(paths, type, from, to, sample.mode(), checkedAt);
            choices.add(route == null ? sample : route);
        }
        if (!hasRail) {
            Segment subway = firstRoute(paths, 1, from, to, Mode.SUBWAY, checkedAt);
            if (subway != null) choices.add(subway);
        }
        if (!hasBus) {
            Segment bus = firstRoute(paths, 2, from, to, Mode.CITY_BUS, checkedAt);
            if (bus != null) choices.add(bus);
        }
        Segment mixed = firstRoute(paths, 3, from, to, Mode.BUS_SUBWAY, checkedAt);
        if (mixed != null) choices.add(mixed);
        return List.copyOf(choices);
    }

    /** 경로 유형별 첫 요약을 변환합니다. 아동 운임은 미확인으로 두고 거리 단위는 km로 맞춥니다. */
    private static Segment firstRoute(JsonNode paths, int type, Place from, Place to, Mode mode, Instant checkedAt) {
        for (JsonNode path : paths) {
            if (path.path("pathType").asInt(-1) != type) continue;
            JsonNode info = path.path("info");
            int minutes = info.path("totalTime").asInt(-1);
            int payment = info.path("payment").asInt(-1);
            int distance = info.path("totalDistance").asInt(-1);
            int busCount = info.path("busTransitCount").asInt(-1);
            int subwayCount = info.path("subwayTransitCount").asInt(-1);
            if (minutes <= 0 || payment < 0 || distance <= 0 || busCount < 0 || subwayCount < 0) continue;
            // ODsay의 수단별 탑승 수를 실제 환승 횟수로 바꿉니다.
            int transfers = Math.max(0, busCount + subwayCount - 1);
            return new Segment(from, to, mode, BigDecimal.valueOf(distance, 3), minutes, transfers,
                    new Fare(BigDecimal.valueOf(payment), null, null), Source.ODSAY, checkedAt,
                    info.path("mapObj").asText(null), TransitBoardings.withWalkEndpoints(boardings(path.path("subPath")), from, to));
        }
        return null;
    }

    /** 도보와 탑승 단계의 순서를 읽습니다. ODsay의 개별 단계에 지도 좌표를 붙이지 않고 loadLane 결과를 별도로 사용합니다. */
    private static List<Boarding> boardings(JsonNode subPaths) {
        List<Boarding> result = new ArrayList<>();
        for (JsonNode part : subPaths) {
            int type = part.path("trafficType").asInt(-1);
            Mode mode = switch (type) {
                case 1 -> Mode.SUBWAY;
                case 2 -> Mode.CITY_BUS;
                case 3 -> Mode.WALK;
                default -> null;
            };
            if (mode == null) continue;
            String field = type == 1 ? "name" : "busNo";
            List<String> names = new ArrayList<>();
            JsonNode lanes = part.path("lane");
            for (JsonNode lane : lanes.isArray() ? lanes : List.of(lanes)) {
                String name = lane.path(field).asText("");
                if (!name.isBlank() && !names.contains(name)) names.add(name);
            }
            int minutes = part.path("sectionTime").asInt(-1);
            result.add(new Boarding(mode, List.copyOf(names),
                    part.path("startName").asText(null), part.path("endName").asText(null),
                    minutes < 0 ? null : minutes));
        }
        return List.copyOf(result);
    }
}
