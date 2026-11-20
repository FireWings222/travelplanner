package com.example.travelplanner.integration;

import com.example.travelplanner.dto.Travel.*;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
public class SampleCatalog implements TravelProviders.Catalog {
    private final List<Destination> places = List.of(
            new Destination("seoul", "서울", "KR", "대한민국 한국 Seoul"),
            new Destination("busan", "부산", "KR", "대한민국 한국 Busan"),
            new Destination("jeju", "제주", "KR", "대한민국 한국 Jeju"),
            new Destination("osaka", "일본 오사카", "JP", "Osaka Japan"),
            new Destination("kyoto", "일본 교토", "JP", "Kyoto Japan"),
            new Destination("tokyo", "일본 도쿄", "JP", "Tokyo Japan"));

    private final Map<String, Place> centers = Map.of(
            "seoul", new Place("seoul", "서울역", 37.555, 126.971),
            "busan", new Place("busan", "부산역", 35.115, 129.042),
            "jeju", new Place("jeju", "제주시 중심", 33.500, 126.531),
            "osaka", new Place("osaka", "오사카역", 34.702, 135.496),
            "kyoto", new Place("kyoto", "교토역", 34.985, 135.758),
            "tokyo", new Place("tokyo", "도쿄역", 35.681, 139.767));

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
            jp("dotonbori", "osaka", "도톤보리", "도톤보리강과 난바 거리", 90, 0, 34.6687, 135.5013),
            jp("osaka-castle", "osaka", "오사카성", "천수각과 오사카성 공원", 120, 1200, 34.6873, 135.5262),
            jp("umeda", "osaka", "우메다 전망대", "우메다 스카이 빌딩 전망대", 75, 2000, 34.7053, 135.4897),
            jp("fushimi", "kyoto", "후시미 이나리 신사", "붉은 도리이 산책로", 90, 0, 34.9671, 135.7727),
            jp("kiyomizu", "kyoto", "기요미즈데라", "히가시야마의 목조 사찰", 90, 500, 34.9949, 135.7850),
            jp("arashiyama", "kyoto", "아라시야마", "대나무숲과 강변 산책로", 120, 0, 35.0170, 135.6719),
            jp("kinkaku", "kyoto", "금각사", "연못과 금빛 누각", 60, 500, 35.0394, 135.7292),
            jp("sensoji", "tokyo", "센소지", "아사쿠사의 불교 사찰", 90, 0, 35.7148, 139.7967),
            jp("skytree", "tokyo", "도쿄 스카이트리", "스미다의 전망대", 75, 2400, 35.7101, 139.8107),
            jp("ueno", "tokyo", "우에노 공원", "박물관이 모인 도심 공원", 90, 0, 35.7146, 139.7745));

    public List<Destination> destinations() { return places; }
    public List<Attraction> attractions() { return sights; }
    public Place center(String destination) { return centers.get(destination); }

    private static Attraction kr(String id, String city, String name, String description, int minutes,
                                 int admission, double lat, double lon) {
        return attraction(id, city, name, description, minutes, Currency.KRW, admission, lat, lon);
    }

    private static Attraction jp(String id, String city, String name, String description, int minutes,
                                 int admission, double lat, double lon) {
        return attraction(id, city, name, description, minutes, Currency.JPY, admission, lat, lon);
    }

    private static Attraction attraction(String id, String city, String name, String description, int minutes,
                                         Currency currency, int admission, double lat, double lon) {
        return new Attraction(id, city, name, description, minutes,
                new Fare(currency, BigDecimal.valueOf(admission), BigDecimal.valueOf(admission / 2), null), lat, lon);
    }
}
