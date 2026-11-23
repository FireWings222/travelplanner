package com.example.travelplanner.integration;

import com.example.travelplanner.dto.Travel.Destination;
import com.example.travelplanner.dto.Travel.SearchedPlace;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KakaoPlaceSearchTest {
    /** 장소 이름이 없는 주소도 정확한 좌표가 있으면 안정적인 ID로 추가할 수 있습니다. */
    @Test
    void usesStreetAddressesAsStopsWithoutARegisteredPlaceName() {
        var city = new Destination("jeju", "제주", "");
        var json = new ObjectMapper().readTree("""
                {"documents":[
                  {"address_name":"제주특별자치도 제주시 관덕로 25","address_type":"ROAD_ADDR",
                   "x":"126.522","y":"33.513","road_address":{"building_name":""}},
                  {"address_name":"제주 제주시 관덕로","address_type":"ROAD","x":"126.52","y":"33.51"},
                  {"address_name":"부산광역시 중구 중앙대로 1","address_type":"ROAD_ADDR","x":"129.03","y":"35.1"}
                ]}
                """);
        var results = KakaoPlaceSearch.readAddresses(city, json);
        assertEquals(1, results.size());
        assertEquals("제주특별자치도 제주시 관덕로 25", results.get(0).name());
        assertEquals(126.522, results.get(0).longitude());
        assertEquals(results, KakaoPlaceSearch.readAddresses(city, json));
        assertEquals(2, KakaoPlaceSearch.readAddresses(null, json).size());
    }

    /** 카카오맵 문자열 좌표와 지번 주소를 읽고 다른 도시 결과를 제외하는지 확인합니다. */
    @Test
    void readsOnlyPlacesInTheSelectedDestination() {
        var city = new Destination("jeju", "제주", "");
        var response = new ObjectMapper().readTree("""
                {"documents":[
                  {"id":"123","place_name":"제주목 관아","road_address_name":"",
                   "address_name":"제주 제주시 삼도이동","x":"126.522","y":"33.513"},
                  {"id":"456","place_name":"부산 전시관","road_address_name":"부산광역시 영도구",
                   "x":"129.04","y":"35.11"}
                ]}
                """);
        assertEquals(List.of(new SearchedPlace("kakao-123", "제주목 관아", "제주 제주시 삼도이동", 33.513, 126.522)),
                KakaoPlaceSearch.readPlaces(city, response));
        assertEquals(2, KakaoPlaceSearch.readPlaces(null, response).size());
    }
}
