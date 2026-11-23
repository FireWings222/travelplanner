package com.example.travelplanner.integration;

import com.example.travelplanner.dto.Travel.*;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Component
public class SampleCatalog implements TravelProviders.Catalog {
    private final List<Destination> places = List.of(
            new Destination("seoul", "서울", "대한민국 한국 Seoul"),
            new Destination("busan", "부산", "대한민국 한국 Busan"),
            new Destination("jeju", "제주", "제주특별자치도 제주시 서귀포 Jeju"),
            new Destination("incheon", "인천", "인천광역시 Incheon"),
            new Destination("daejeon", "대전", "대전광역시 Daejeon"),
            new Destination("daegu", "대구", "대구광역시 Daegu"),
            new Destination("gwangju", "광주광역시", "광주 Gwangju"),
            new Destination("ulsan", "울산", "울산광역시 Ulsan"),
            new Destination("sejong", "세종", "세종특별자치시 Sejong"),
            new Destination("gyeonggi", "경기", "경기도 수원 성남 고양 Gyeonggi"),
            new Destination("gangwon", "강원", "강원도 강원특별자치도 춘천 강릉 속초 Gangwon"),
            new Destination("chungbuk", "충북", "충청북도 청주 Chungbuk"),
            new Destination("chungnam", "충남", "충청남도 홍성 천안 Chungnam"),
            new Destination("jeonbuk", "전북", "전라북도 전북특별자치도 전주 Jeonbuk"),
            new Destination("jeonnam", "전남", "전라남도 무안 순천 여수 목포 Jeonnam"),
            new Destination("gyeongbuk", "경북", "경상북도 안동 경주 포항 Gyeongbuk"),
            new Destination("gyeongnam", "경남", "경상남도 창원 김해 통영 Gyeongnam"));

    private final Map<String, Place> centers = Map.ofEntries(
            Map.entry("seoul", new Place("seoul", "서울역", 37.555, 126.971)),
            Map.entry("busan", new Place("busan", "부산역", 35.115, 129.042)),
            Map.entry("jeju", new Place("jeju", "제주시 중심", 33.500, 126.531)),
            Map.entry("incheon", new Place("incheon", "인천시청", 37.456, 126.705)),
            Map.entry("daejeon", new Place("daejeon", "대전역", 36.332, 127.434)),
            Map.entry("daegu", new Place("daegu", "동대구역", 35.879, 128.628)),
            Map.entry("gwangju", new Place("gwangju", "광주광역시청", 35.160, 126.851)),
            Map.entry("ulsan", new Place("ulsan", "울산시청", 35.539, 129.311)),
            Map.entry("sejong", new Place("sejong", "세종시청", 36.480, 127.289)),
            Map.entry("gyeonggi", new Place("gyeonggi", "경기도청", 37.288, 127.056)),
            Map.entry("gangwon", new Place("gangwon", "강원특별자치도청", 37.886, 127.730)),
            Map.entry("chungbuk", new Place("chungbuk", "충청북도청", 36.636, 127.492)),
            Map.entry("chungnam", new Place("chungnam", "충청남도청", 36.659, 126.673)),
            Map.entry("jeonbuk", new Place("jeonbuk", "전북특별자치도청", 35.820, 127.108)),
            Map.entry("jeonnam", new Place("jeonnam", "전라남도청", 34.817, 126.463)),
            Map.entry("gyeongbuk", new Place("gyeongbuk", "경상북도청", 36.576, 128.506)),
            Map.entry("gyeongnam", new Place("gyeongnam", "경상남도청", 35.238, 128.692)));

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

    private final List<Attraction> allSights;

    public SampleCatalog(DomesticTours domesticTours) {
        allSights = Stream.concat(sights.stream(), domesticTours.attractions().stream()).toList();
    }

    /** CatalogService의 도시 검색에 사용되는 국내 샘플 도시 목록을 반환합니다. */
    public List<Destination> destinations() { return places; }
    /** 일정과 투어에서 고를 수 있는 국내 샘플 관광지 목록을 반환합니다. */
    public List<Attraction> attractions() { return allSights; }
    @Override public List<Attraction> suggestedAttractions() { return sights; }
    /** JourneyService와 SampleRoutes에서 사용할 도시 중심 위치를 도시 ID로 찾습니다. */
    public Place center(String destination) { return centers.get(destination); }

    /** 카탈로그에 등록할 관광지의 좌표, 기본 체류 시간, 원화 샘플 입장료를 한 항목으로 만듭니다. */
    private static Attraction kr(String id, String city, String name, String description, int minutes,
                                 int admission, double lat, double lon) {
        return new Attraction(id, city, name, description, minutes,
                new Fare(BigDecimal.valueOf(admission), BigDecimal.valueOf(admission / 2), null), lat, lon);
    }
}
