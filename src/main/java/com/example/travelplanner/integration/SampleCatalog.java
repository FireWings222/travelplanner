package com.example.travelplanner.integration;

import com.example.travelplanner.dto.Travel.*;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
public class SampleCatalog implements TravelProviders.Catalog {
    private final List<Destination> places = List.of(
            new Destination("seoul", "서울", "대한민국 한국 Seoul"),
            new Destination("busan", "부산", "대한민국 한국 Busan"),
            new Destination("jeju", "제주", "대한민국 한국 Jeju"));

    private final Map<String, Place> centers = Map.of(
            "seoul", new Place("seoul", "서울역", 37.555, 126.971),
            "busan", new Place("busan", "부산역", 35.115, 129.042),
            "jeju", new Place("jeju", "제주시 중심", 33.500, 126.531));

    private final List<Attraction> sights = List.of(
            kr("palace", "seoul", "경복궁", "조선 왕조의 궁궐", 120, 3000, 37.5796, 126.9770),
            kr("tower", "seoul", "N서울타워", "남산 전망대", 90, 21000, 37.5512, 126.9882),
            kr("market", "seoul", "광장시장", "전통시장 먹거리 골목", 60, 0, 37.5700, 126.9997),
            kr("haeundae", "busan", "해운대", "해변 산책로", 90, 0, 35.1587, 129.1604),
            kr("gamcheon", "busan", "감천문화마을", "산비탈 골목 마을", 120, 0, 35.0975, 129.0106),
            kr("songdo", "busan", "송도 해상케이블카", "송도 앞바다 전망", 60, 17000, 35.0763, 129.0230),
            kr("seongsan", "jeju", "성산일출봉", "제주 동쪽의 응회구", 90, 5000, 33.4590, 126.9425),
            kr("doldam", "jeju", "함덕해수욕장", "제주 북쪽 해변", 90, 0, 33.5432, 126.6694),
            kr("dongmun", "jeju", "동문시장", "제주시 전통시장", 60, 0, 33.5117, 126.5263),
            kr("gimnyeong", "jeju", "김녕해수욕장", "제주 동북쪽의 해변 산책로", 60, 0, 33.5570, 126.7590));

    /** CatalogService의 도시 검색에 사용되는 국내 샘플 도시 목록을 반환합니다. */
    public List<Destination> destinations() { return places; }
    /** 일정과 투어에서 고를 수 있는 국내 샘플 관광지 목록을 반환합니다. */
    public List<Attraction> attractions() { return sights; }
    /** JourneyService와 SampleRoutes에서 사용할 도시 중심 위치를 도시 ID로 찾습니다. */
    public Place center(String destination) { return centers.get(destination); }

    /** 카탈로그에 등록할 관광지의 좌표, 기본 체류 시간, 원화 샘플 입장료를 한 항목으로 만듭니다. */
    private static Attraction kr(String id, String city, String name, String description, int minutes,
                                 int admission, double lat, double lon) {
        return new Attraction(id, city, name, description, minutes,
                new Fare(BigDecimal.valueOf(admission), BigDecimal.valueOf(admission / 2), null), lat, lon);
    }
}
