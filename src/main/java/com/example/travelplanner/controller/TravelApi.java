package com.example.travelplanner.controller;

import com.example.travelplanner.dto.Travel.*;
import com.example.travelplanner.integration.OdsayRoutes;
import com.example.travelplanner.integration.KakaoPlaceSearch;
import com.example.travelplanner.service.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class TravelApi {
    private final CatalogService catalog;
    private final JourneyService journeys;
    private final PlanService plans;
    private final TourService tours;
    private final ComparisonService comparison;
    private final OdsayRoutes odsay;
    private final KakaoPlaceSearch places;

    /** Spring이 전달한 서비스들을 REST 요청 처리에 사용할 수 있도록 연결합니다. */
    public TravelApi(CatalogService catalog, JourneyService journeys, PlanService plans,
                     TourService tours, ComparisonService comparison, OdsayRoutes odsay, KakaoPlaceSearch places) {
        this.catalog = catalog;
        this.journeys = journeys;
        this.plans = plans;
        this.tours = tours;
        this.comparison = comparison;
        this.odsay = odsay;
        this.places = places;
    }

    /** 홈 검색 폼의 도시 목록과 검색어를 CatalogService에 연결합니다. */
    @GetMapping("/destinations/search")
    public List<Destination> destinations(@RequestParam(defaultValue = "") String q) {
        return catalog.search(q);
    }

    @GetMapping("/places/search")
    public List<SearchedPlace> tripPlaces(@RequestParam String q,
                                          @RequestParam(defaultValue = "false") boolean address) {
        return places.searchNationwide(q, address);
    }

    /** 일정 편집 및 투어 등록 화면에 도시와 검색어로 필터링한 관광지를 반환합니다. */
    @GetMapping("/attractions")
    public List<Attraction> attractions(@RequestParam(required = false) String destination,
                                        @RequestParam(defaultValue = "") String q) {
        return catalog.attractions(destination, q);
    }

    /** 관광지 상세 API 요청을 받아 CatalogService에서 해당 관광지를 조회합니다. */
    @GetMapping("/attractions/{id}")
    public Attraction attraction(@PathVariable String id) { return catalog.attraction(id); }

    /** 일정 화면에서 입력한 장소명 또는 주소를 카카오맵에서 검색합니다. */
    @GetMapping("/itineraries/places")
    public List<SearchedPlace> searchPlaces(@RequestParam String destination, @RequestParam String q,
                                            @RequestParam(defaultValue = "false") boolean address) {
        Destination city = catalog.destination(destination);
        return address ? places.searchAddress(city, q) : places.search(city, q);
    }

    /**
     * 홈 검색과 경로 비교 화면의 요청을 JourneyService에 보내 구간별 견적을 반환합니다.
     *
     * 홈의 검색 버튼과 routes.js의 교통수단 변경에서 같은 API를 사용합니다. 요청의 여행 조건과 구간 선택을 받아 비교 후보, 선택 경로, 추천을 JSON으로 돌려줍니다.
     */
    @PostMapping("/routes/compare")
    public JourneyService.RouteResults routes(@Valid @RequestBody RouteRequest request) {
        return journeys.compare(request);
    }

    /**
     * 지도 버튼이 보낸 ODsay 노선 코드를 바탕으로 지도에 그릴 좌표 배열을 반환합니다.
     *
     * route-map.js가 지도 열기 버튼을 눌렀을 때만 이 API를 호출합니다. 반환값의 각 배열은 카카오맵에서 따로 그릴 노선 선분입니다.
     */
    @GetMapping("/routes/geometry")
    public List<List<OdsayRoutes.Point>> geometry(@RequestParam String mapObject) {
        return odsay.geometry(mapObject);
    }

    /**
     * 여행 일정 화면에 여행 조건을 바탕으로 만든 기본 일정과 견적을 반환합니다.
     *
     * 선택한 도시, 인원, 날짜로 만든 기본 일정과 견적을 plan.js로 반환합니다. 일정 저장은 /api/travel-plans에서 처리합니다.
     */
    @PostMapping("/itineraries/generate")
    public PlanService.PlanQuote generate(@Valid @RequestBody Trip trip) { return plans.generate(trip); }

    /**
     * 일정 화면에서 관광지나 교통수단을 편집할 때 변경된 일정의 견적을 반환합니다.
     *
     * plan.js에서 날짜별 방문지나 교통수단을 변경한 전체 계획을 전달합니다. 검증을 통과한 계획과 새 비용 합계를 돌려줍니다.
     */
    @PostMapping("/itineraries/quote")
    public PlanService.PlanQuote quote(@Valid @RequestBody Plan plan) { return plans.quote(plan); }

    /** 일정 화면의 저장 목록에 계획 요약을 최근 수정 순으로 반환합니다. */
    @GetMapping("/travel-plans")
    public List<PlanService.PlanSummary> plans() { return plans.list(); }

    /** 일정 화면에서 보낸 새 계획을 PlanService로 저장합니다. */
    @PostMapping("/travel-plans")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanService.StoredPlan save(@Valid @RequestBody Plan plan) { return plans.save(null, plan); }

    /** 저장 목록에서 ‘열기’를 누른 계획의 상세 데이터를 반환합니다. */
    @GetMapping("/travel-plans/{id}")
    public PlanService.StoredPlan plan(@PathVariable UUID id) { return plans.get(id); }

    /** 기존 저장 일정의 ID와 변경 내용을 PlanService에 전달해 갱신합니다. */
    @PutMapping("/travel-plans/{id}")
    public PlanService.StoredPlan update(@PathVariable UUID id, @Valid @RequestBody Plan plan) {
        return plans.save(id, plan);
    }

    /** 저장 목록에서 선택한 계획을 PlanService로 삭제합니다. */
    @DeleteMapping("/travel-plans/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { plans.delete(id); }

    /** 일일투어 화면에 도시·날짜·플랫폼 조건에 맞는 상품 목록을 돌려줍니다. */
    @GetMapping("/tours/search")
    public TourService.SearchResult tourSearch(@RequestParam String destination,
                                               @RequestParam(required = false) LocalDate date,
                                               @RequestParam(required = false) Platform platform) {
        return tours.search(destination, date, platform);
    }

    /** 일일투어 화면의 직접 등록 폼을 받아 상품을 저장합니다. */
    @PostMapping("/tours")
    @ResponseStatus(HttpStatus.CREATED)
    public Tour register(@Valid @RequestBody TourInput tour) { return tours.register(tour); }

    /** 투어 상세 화면과 비용 비교 화면에 지정한 상품 정보를 반환합니다. */
    @GetMapping("/tours/{id}")
    public Tour tour(@PathVariable String id) { return tours.get(id); }

    /**
     * 자유여행과 투어의 최초 비용 비교 및 교통수단 변경 후 재계산을 처리합니다.
     *
     * compare.js의 첫 조회와 구간 이동수단 변경에서 호출합니다. 상품 정보와 양쪽 견적, 비용 차이, 미확인 항목 안내를 같은 응답에 담습니다.
     */
    @PostMapping({"/compare", "/compare/recalculate"})
    public ComparisonService.Result compare(@Valid @RequestBody CompareRequest request) {
        return comparison.compare(request);
    }
}
