package com.example.travelplanner.service;

import com.example.travelplanner.dto.Travel.*;
import com.example.travelplanner.integration.TravelProviders;
import org.springframework.stereotype.Service;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

@Service
public class CatalogService {
    private final TravelProviders.Catalog catalog;

    /** 도시와 관광지 검색에 사용할 카탈로그 제공자를 주입받습니다. */
    public CatalogService(TravelProviders.Catalog catalog) { this.catalog = catalog; }

    /** 도시명·ID·키워드를 검색어와 비교해 홈의 후보 도시 목록을 만듭니다. */
    public List<Destination> search(String query) {
        String term = normalize(query);
        return catalog.destinations().stream().filter(city ->
                normalize(city.name() + " " + city.keywords() + " " + city.id()).contains(term)).toList();
    }

    /** 선택 도시와 검색어에 맞는 관광지를 일정·투어 화면에 제공합니다. */
    public List<Attraction> attractions(String destination, String query) {
        Destination city = destination == null || destination.isBlank() ? null : destination(destination);
        return catalog.attractions().stream()
                .filter(sight -> city == null || sight.destinationId().equals(city.id()))
                .filter(sight -> normalize(sight.name() + " " + sight.description()).contains(normalize(query)))
                .toList();
    }

    /**
     * 도시 ID나 이름을 카탈로그의 한 도시로 확정합니다. 후보가 여러 개면 입력 오류를 반환합니다.
     *
     * 먼저 표준 도시 ID와 도시명의 정확한 일치를 찾고, 해당 결과가 없으면 키워드 검색을 사용합니다. 입력이 비었거나 한 도시로 결정할 수 없으면 예외를 반환합니다.
     */
    public Destination destination(String value) {
        String term = normalize(value);
        if (term.isEmpty()) throw new IllegalArgumentException("여행 지역을 입력해 주세요.");
        String label = value.strip();
        List<Destination> exact = catalog.destinations().stream().filter(city ->
                normalize(city.id()).equals(term) || (!label.contains(" ") && matchesAddress(city, label))).toList();
        List<Destination> matches = exact.isEmpty() ? catalog.destinations().stream()
                .filter(city -> city.keywords().toLowerCase(Locale.ROOT).endsWith(" " + term))
                .filter(city -> term.matches("[a-z]+")).toList() : exact;
        if (matches.size() != 1) {
            throw new IllegalArgumentException("시·도를 선택하거나 장소 검색 결과에서 정확한 위치를 선택해 주세요.");
        }
        return matches.get(0);
    }

    /** 관광지 ID를 카탈로그 항목으로 바꿉니다. 일정과 투어의 방문지 검증에 사용합니다. */
    public Attraction attraction(String id) {
        return catalog.attractions().stream().filter(sight -> sight.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("관광지를 찾을 수 없습니다: " + id));
    }

    /** 경로 계산에서 출발점이나 숙소 기준으로 사용할 도시 중심 좌표를 반환합니다. */
    public Place center(String id) { return catalog.center(id); }

    /** 관광지 데이터를 JourneyService가 사용할 이름과 좌표의 이동 지점으로 변환합니다. */
    public Place place(Attraction attraction) {
        return new Place(attraction.id(), attraction.name(), attraction.latitude(), attraction.longitude());
    }

    /**
     * 경로와 일정을 계산하기 전에 여행 기간을 검증하고 도시 입력을 표준 ID로 정리합니다.
     *
     * 경로 비교와 일정 생성에서 공통으로 사용합니다. 시작일과 종료일을 포함한 기간이 1~30일인지 확인하고 출발지·목적지를 표준 ID로 바꿉니다.
     */
    public Trip validateTrip(Trip trip) {
        long days = ChronoUnit.DAYS.between(trip.start(), trip.end()) + 1;
        if (days < 1 || days > 30) throw new IllegalArgumentException("여행 기간은 1일 이상 30일 이하여야 합니다.");
        return new Trip(region(trip.origin(), trip.originPlace()).id(),
                region(trip.destination(), trip.destinationPlace()).id(), trip.start(), trip.end(),
                trip.party(), trip.budget(), trip.preferredMode(), trip.type(),
                trip.originPlace(), trip.destinationPlace());
    }

    /** 카카오 검색 결과를 선택하면 입력란의 이름 대신 선택한 장소의 주소로 시·도를 확정합니다. */
    private Destination region(String city, SearchedPlace place) {
        if (place == null) return destination(city);
        if (!validCoordinates(place) || !place.id().matches(SearchedPlace.ID_PATTERN)) {
            throw new IllegalArgumentException("검색한 장소의 좌표나 식별자가 올바르지 않습니다.");
        }
        return catalog.destinations().stream().filter(region -> matchesAddress(region, place.address())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("검색한 장소의 국내 주소를 확인할 수 없습니다."));
    }

    /** 구체 장소를 선택한 경로는 그 좌표를 쓰고, 시·도만 선택한 경로는 지역 대표 지점을 사용합니다. */
    public Place point(String region, SearchedPlace selected) {
        return selected == null ? center(region)
                : new Place(selected.id(), selected.name(), selected.latitude(), selected.longitude());
    }

    /**
     * 일정·투어의 관광지 ID를 순서대로 조회하고 중복 방문지와 다른 도시의 방문지를 확인합니다.
     *
     * 방문지 배열의 순서를 유지해 이동 순서로 사용합니다. 중복 ID, 존재하지 않는 ID, 목적지 외의 관광지는 입력 오류로 연결됩니다.
     */
    public List<Attraction> stops(String destination, List<String> ids) {
        return stops(destination, ids, List.of());
    }

    /** 저장된 카카오맵 장소는 해당 일정의 목적지와 좌표를 확인한 뒤 기존 관광지와 같은 순서로 처리합니다. */
    public List<Attraction> stops(String destination, List<String> ids, List<SearchedPlace> places) {
        if (ids.stream().distinct().count() != ids.size()) {
            throw new IllegalArgumentException("같은 날에 동일한 관광지를 두 번 추가할 수 없습니다.");
        }
        Destination city = destination(destination);
        Map<String, Attraction> selected = new HashMap<>();
        if (places != null) {
            for (SearchedPlace place : places) {
                if (!ids.contains(place.id()) || !place.id().matches(SearchedPlace.ID_PATTERN)
                        || !matchesAddress(city, place.address()) || !validCoordinates(place)) {
                    throw new IllegalArgumentException("검색한 장소의 주소와 좌표가 여행 일정에 맞는지 확인해 주세요.");
                }
                Attraction stop = new Attraction(place.id(), city.id(), place.name(), place.address(), null, null,
                        place.latitude(), place.longitude());
                if (selected.putIfAbsent(place.id(), stop) != null) {
                    throw new IllegalArgumentException("같은 장소가 두 번 등록되었습니다.");
                }
            }
        }
        return ids.stream().map(id -> selected.containsKey(id) ? selected.get(id) : attraction(id)).peek(attraction -> {
            if (!attraction.destinationId().equals(city.id())) {
                throw new IllegalArgumentException("선택한 도시 외의 관광지가 포함되어 있습니다.");
            }
        }).toList();
    }

    /** 카카오 지도에서 사용하는 약칭과 행정구역 정식 명칭을 모두 인식합니다. */
    public static boolean matchesAddress(Destination city, String address) {
        String province = address.strip().split("\\s+", 2)[0];
        if (province.equals(city.name())) return true;
        return switch (city.id()) {
            case "seoul" -> province.equals("서울특별시");
            case "busan" -> province.equals("부산광역시");
            case "jeju" -> province.equals("제주특별자치도");
            case "incheon" -> province.equals("인천광역시");
            case "daejeon" -> province.equals("대전광역시");
            case "daegu" -> province.equals("대구광역시");
            case "gwangju" -> province.equals("광주");
            case "ulsan" -> province.equals("울산광역시");
            case "sejong" -> province.equals("세종특별자치시");
            case "gyeonggi" -> province.equals("경기도");
            case "gangwon" -> province.equals("강원도") || province.equals("강원특별자치도");
            case "chungbuk" -> province.equals("충청북도");
            case "chungnam" -> province.equals("충청남도");
            case "jeonbuk" -> province.equals("전라북도") || province.equals("전북특별자치도");
            case "jeonnam" -> province.equals("전라남도");
            case "gyeongbuk" -> province.equals("경상북도");
            case "gyeongnam" -> province.equals("경상남도");
            default -> false;
        };
    }

    /** 지도와 교통 API에 보낼 수 있는 국내 경위도인지 검사합니다. */
    public static boolean validCoordinates(SearchedPlace place) {
        return place.latitude() >= 32 && place.latitude() <= 39
                && place.longitude() >= 124 && place.longitude() <= 132;
    }

    /** 도시·관광지 검색에서 대소문자와 공백 차이를 무시할 수 있도록 검색 문자열을 정리합니다. */
    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }
}
