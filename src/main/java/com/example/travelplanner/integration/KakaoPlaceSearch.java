package com.example.travelplanner.integration;

import com.example.travelplanner.dto.Travel.Destination;
import com.example.travelplanner.dto.Travel.SearchedPlace;
import com.example.travelplanner.exception.NotFound;
import com.example.travelplanner.service.CatalogService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class KakaoPlaceSearch {
    private static final String ENDPOINT = "https://dapi.kakao.com/v2/local/search/";
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper json;
    private final String apiKey;

    /** 장소 검색용 REST 키를 읽습니다. 별도 키가 없으면 자동차 길찾기에 설정한 REST 키를 사용합니다. */
    public KakaoPlaceSearch(ObjectMapper json,
                            @Value("${KAKAO_MAP_REST_API_KEY:${KAKAO_MOBILITY_REST_API_KEY:}}") String apiKey) {
        this.json = json;
        this.apiKey = apiKey.strip();
    }

    /** 선택한 여행지의 키워드 검색을 서버에서 실행합니다. 브라우저에는 검색 결과만 전달합니다. */
    public List<SearchedPlace> search(Destination city, String query) {
        String keyword = query.strip();
        if (keyword.isEmpty() || keyword.length() > 80) {
            throw new IllegalArgumentException("장소 이름을 1~80자로 입력해 주세요.");
        }
        return readPlaces(city, request("keyword.json", city.name() + " " + keyword, "&size=15"));
    }

    /** 장소명이 등록되지 않은 숙소도 도로명·지번 주소의 건물 위치로 추가할 수 있게 합니다. */
    public List<SearchedPlace> searchAddress(Destination city, String query) {
        String address = query.strip();
        if (address.isEmpty() || address.length() > 160) {
            throw new IllegalArgumentException("도로명 또는 지번 주소를 1~160자로 입력해 주세요.");
        }
        String scopedAddress = CatalogService.matchesAddress(city, address) ? address : city.name() + " " + address;
        return readAddresses(city, request("address.json", scopedAddress, "&analyze_type=exact"));
    }

    /** 장소명 검색과 주소 검색의 공통 카카오 REST 호출입니다. 키는 인증 헤더에만 포함합니다. */
    private JsonNode request(String endpoint, String query, String options) {
        if (apiKey.isEmpty()) throw new NotFound("카카오맵 장소 검색에 사용할 REST API 키를 설정해 주세요.");
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        try {
            var request = HttpRequest.newBuilder(URI.create(ENDPOINT + endpoint + "?query=" + encoded + options))
                    .timeout(Duration.ofSeconds(4)).header("Authorization", "KakaoAK " + apiKey).GET().build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) return json.readTree(response.body());
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                throw new NotFound("카카오디벨로퍼스에서 REST API 키와 카카오맵 사용 설정을 확인해 주세요.");
            }
        } catch (IOException | JacksonException ignored) {
            // 서비스 오류 응답에는 인증 정보가 포함될 수 있으므로 전달하지 않습니다.
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        throw new TravelProviders.Unavailable("카카오맵 장소 검색에 실패했습니다.", null);
    }

    /** 카카오 응답의 경도 x·위도 y, 장소명 및 주소를 일정에서 쓸 값으로 변환합니다. */
    static List<SearchedPlace> readPlaces(Destination city, JsonNode response) {
        Map<String, SearchedPlace> found = new LinkedHashMap<>();
        for (JsonNode item : response.path("documents")) {
            String id = item.path("id").asText("");
            String name = item.path("place_name").asText("");
            String address = item.path("road_address_name").asText("");
            if (address.isBlank()) address = item.path("address_name").asText("");
            var place = new SearchedPlace("kakao-" + id, name, address,
                    item.path("y").asDouble(Double.NaN), item.path("x").asDouble(Double.NaN));
            if (id.matches("[0-9]{1,24}") && !name.isBlank() && name.length() <= 100 && address.length() <= 200
                    && CatalogService.matchesAddress(city, address) && CatalogService.validCoordinates(place)) {
                found.putIfAbsent(place.id(), place);
            }
        }
        return List.copyOf(found.values());
    }

    /** 정확한 건물·지번 결과만 사용하고, 같은 대표 주소는 다시 검색해도 동일한 일정 ID를 부여합니다. */
    static List<SearchedPlace> readAddresses(Destination city, JsonNode response) {
        Map<String, SearchedPlace> found = new LinkedHashMap<>();
        for (JsonNode item : response.path("documents")) {
            String type = item.path("address_type").asText("");
            if (!type.equals("ROAD_ADDR") && !type.equals("REGION_ADDR")) continue;
            JsonNode road = item.path("road_address");
            String address = road.path("address_name").asText("");
            if (address.isBlank()) address = item.path("address_name").asText("");
            if (address.length() > 200 || !CatalogService.matchesAddress(city, address)) continue;
            String name = road.path("building_name").asText("");
            if (name.isBlank() || name.length() > 200) name = address;
            String id = UUID.nameUUIDFromBytes(address.getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
            var place = new SearchedPlace("kakao-address-" + id, name, address,
                    item.path("y").asDouble(Double.NaN), item.path("x").asDouble(Double.NaN));
            if (CatalogService.validCoordinates(place)) found.putIfAbsent(place.id(), place);
        }
        return List.copyOf(found.values());
    }
}
