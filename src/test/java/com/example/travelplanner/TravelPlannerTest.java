package com.example.travelplanner;

import com.example.travelplanner.dto.Travel.*;
import com.example.travelplanner.integration.TravelProviders;
import com.example.travelplanner.integration.KakaoPlaceSearch;
import com.example.travelplanner.pricing.Pricing;
import com.example.travelplanner.service.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:travelplanner-tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "ODSAY_API_KEY=",
        "KAKAO_MOBILITY_REST_API_KEY="
})
@AutoConfigureMockMvc
class TravelPlannerTest {
    private static final LocalDate DATE = LocalDate.of(2027, 3, 10);
    private static final Party PARTY = new Party(2, 1);
    private static final Trip TRIP = new Trip("서울", "Jeju", DATE, DATE.plusDays(3), PARTY, null, null, TripType.FAMILY);
    @Autowired Pricing pricing;
    @Autowired CatalogService catalog;
    @Autowired JourneyService routes;
    @Autowired PlanService plans;
    @Autowired TourService tours;
    @Autowired ComparisonService comparison;
    @Autowired MockMvc http;
    @MockitoBean KakaoPlaceSearch placeSearch;

    /** Pricing이 성인과 아동 요금을 각 인원수에 맞게 합산하는지 검증합니다. */
    @Test
    void multipliesAdultAndChildFaresByHeadcount() {
        var fare = fare(65000, 49000);
        var cost = pricing.item("투어", fare, Source.SAMPLE, PARTY);
        amount("179000", cost.totalKrw());
        amount("65000", pricing.total(List.of(cost), PARTY).adultPerPersonKrw());
        amount("49000", pricing.total(List.of(cost), PARTY).childPerPersonKrw());
    }

    /** 구간 교통수단을 바꾸면 경로 가격과 투어 비교 결과가 갱신되는지 검증합니다. */
    @Test
    void changingALegRecalculatesTheRouteAndTourComparison() {
        var before = routes.compare(new RouteRequest(TRIP, Map.of()));
        var segment = before.selected().sections().get(0);
        var after = routes.compare(new RouteRequest(TRIP, Map.of(segment.key(), new Leg(Mode.TAXI, null))));
        assertNotEquals(before.selected().cost().totalKrw(), after.selected().cost().totalKrw());
        assertNotEquals(before.selected().minutes(), after.selected().minutes());

        var comparisonBefore = comparison.compare(request(DATE, Map.of()));
        var local = comparisonBefore.journey().sections().get(0);
        var comparisonAfter = comparison.compare(request(DATE, Map.of(local.key(), new Leg(Mode.TAXI, null))));
        assertNotEquals(comparisonBefore.independent().cost().totalKrw(), comparisonAfter.independent().cost().totalKrw());
        assertNotEquals(comparisonBefore.independent().travelMinutes(), comparisonAfter.independent().travelMinutes());
        assertNotEquals(comparisonBefore.tourMinusIndependentKrw(), comparisonAfter.tourMinusIndependentKrw());
    }

    /** 투어에 포함된 입장료가 투어 쪽에 중복 계산되지 않는지 검증합니다. */
    @Test
    void includedAdmissionIsOnlyChargedToIndependentVisitors() {
        var result = comparison.compare(request(DATE, Map.of()));
        assertTrue(result.tour().details().admissionIncluded());
        assertTrue(result.guided().items().stream().noneMatch(item -> item.label().endsWith("입장료")));
        assertEquals(4, result.independent().items().stream().filter(item -> item.label().endsWith("입장료")).count());
    }

    /** 제주행 공항 접근과 항공편 비용이 모두 포함되고 왕복 견적이 계산되는지 검증합니다. */
    @Test
    void addsAllAirportLegsAndCalculatesRoundTrip() {
        var result = routes.compare(new RouteRequest(TRIP, null));
        assertEquals(3, result.selected().sections().size());
        assertEquals(12, result.alternatives().size());
        assertEquals(List.of("gmp", "cju", "jeju"), result.selected().sections().stream()
                .map(section -> section.chosen().to().id()).toList());
        var amounts = result.selected().sections().stream().map(leg -> leg.cost().totalKrw())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        amount(amounts.toPlainString(), result.selected().cost().totalKrw());
        amount(amounts.multiply(BigDecimal.valueOf(2)).toPlainString(), result.roundTripKrw());
    }

    /** 서울과 부산 사이의 육상 교통수단이 원화로 비교되는지 검증합니다. */
    @Test
    void comparesMainlandRoutesWithKoreanWonFares() {
        var trip = new Trip("서울", "부산", DATE, DATE, PARTY, null, null, TripType.FAMILY);
        var result = routes.compare(new RouteRequest(trip, null));
        assertEquals(1, result.selected().sections().size());
        var options = result.selected().sections().get(0).options();
        assertEquals(List.of(Mode.KTX, Mode.TRAIN, Mode.INTERCITY_BUS, Mode.EXPRESS_BUS, Mode.CAR),
                options.stream().map(Segment::mode).toList());
        assertTrue(result.selected().cost().totalKrw().signum() > 0);
    }

    /** 택시 공동 비용이 한 번 합산되고 여행 인원에 균등 배분되는지 검증합니다. */
    @Test
    void sharedTaxiIsOnlyChargedOnceAndSplitEqually() {
        var fare = new Fare(null, null, new BigDecimal("12000"));
        var cost = pricing.total(List.of(pricing.item("택시", fare, Source.MANUAL, PARTY)), PARTY);
        amount("12000", cost.totalKrw());
        amount("4000", cost.adultPerPersonKrw());
        amount("4000", cost.childPerPersonKrw());
    }

    /** 기존 원화 JSON을 읽고 외화 및 아동 요금 누락을 미확인으로 표시하는지 검증합니다. */
    @Test
    void readsLegacyWonFaresWithoutTreatingForeignAmountsAsWon() {
        ObjectMapper mapper = new ObjectMapper();
        var won = mapper.readValue("""
                {"currency":"KRW","adult":1500}
                """, Fare.class);
        amount("3000", pricing.item("입장료", won, Source.MANUAL, PARTY).totalKrw());
        assertEquals(List.of("입장료 아동 요금"), pricing.item("입장료", won, Source.MANUAL, PARTY).missing());

        var foreign = mapper.readValue("""
                {"currency":"JPY","adult":500,"child":250}
                """, Fare.class);
        var unknown = pricing.item("입장료", foreign, Source.MANUAL, PARTY);
        amount("0", unknown.totalKrw());
        assertEquals(List.of("입장료 성인 요금", "입장료 아동 요금"), unknown.missing());
        assertFalse(mapper.writeValueAsString(won).contains("currency"));
    }

    /** 같은 방문지 기준의 비용 비교와 날짜별 투어 요금 적용을 검증합니다. */
    @Test
    void comparesMatchingStopsAndAppliesDatedTourPricing() {
        var result = comparison.compare(request(DATE, Map.of()));
        assertEquals(result.tourStops(), result.independentStops());
        amount("179000", result.guided().cost().totalKrw());
        amount(result.guided().cost().totalKrw().subtract(result.independent().cost().totalKrw()).toPlainString(),
                result.tourMinusIndependentKrw());
        var april = comparison.compare(request(LocalDate.of(2027, 4, 1), Map.of()));
        amount("196000", april.guided().cost().totalKrw());
        var unmatched = comparison.compare(new CompareRequest("jeju-day", DATE, PARTY,
                List.of("dongmun"), Map.of(), null, null, null));
        assertTrue(unmatched.notices().stream().anyMatch(notice -> notice.startsWith("투어만 방문")));
        assertTrue(unmatched.recommendation().contains("보류"));
        var unavailable = comparison.compare(request(LocalDate.of(2040, 1, 1), Map.of()));
        assertNull(unavailable.tourMinusIndependentKrw());
    }

    /** PlanService의 일정 등록, 조회, 수정, 목록, 삭제 흐름을 검증합니다. */
    @Test
    void savesEditsListsAndDeletesTheItinerary() {
        var plan = plans.generate(TRIP).plan();
        assertEquals(List.of("doldam"), plan.days().get(1).stops());
        var saved = plans.save(null, plan);
        assertEquals(plan, plans.get(saved.id()).plan());
        assertTrue(plans.list().stream().anyMatch(item -> item.id().equals(saved.id())));
        List<Day> changed = new ArrayList<>(plan.days());
        changed.set(0, new Day(DATE, List.of("gimnyeong"), Map.of()));
        var update = new Plan(plan.trip(), changed);
        plans.save(saved.id(), update);
        assertEquals(update, plans.get(saved.id()).plan());
        plans.delete(saved.id());
        assertThrows(com.example.travelplanner.exception.NotFound.class, () -> plans.get(saved.id()));
    }

    /** 검색한 장소의 좌표와 입장료 미확인 상태가 일정 저장·조회·재견적에 이어지는지 확인합니다. */
    @Test
    void addsKakaoPlacesToSavedItinerary() throws Exception {
        var place = new SearchedPlace("kakao-123", "제주목 관아", "제주특별자치도 제주시 관덕로 25", 33.513, 126.522);
        when(placeSearch.search(catalog.destination("jeju"), "관아")).thenReturn(List.of(place));
        http.perform(get("/api/itineraries/places").param("destination", "jeju").param("q", "관아"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(place.id()));
        var address = new SearchedPlace("kakao-address-26e23384a2823e589715e4262681a0c5",
                "제주특별자치도 제주시 관덕로 25", "제주특별자치도 제주시 관덕로 25", 33.513, 126.522);
        when(placeSearch.searchAddress(catalog.destination("jeju"), "관덕로 25")).thenReturn(List.of(address));
        http.perform(get("/api/itineraries/places").param("destination", "jeju")
                        .param("q", "관덕로 25").param("address", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(address.id()));

        var trip = plans.generate(TRIP).plan().trip();
        var days = new ArrayList<>(plans.generate(TRIP).plan().days());
        days.set(0, new Day(DATE, List.of(address.id()), Map.of(
                "jeju~" + address.id(), new Leg(Mode.TAXI, null),
                address.id() + "~jeju", new Leg(Mode.TAXI, null)), List.of(address)));
        var saved = plans.save(null, new Plan(trip, days));
        try {
            var restored = plans.get(saved.id()).plan();
            assertEquals(List.of(address), restored.days().get(0).places());
            var quote = plans.quote(restored).days().get(0);
            assertEquals(address.name(), quote.stops().get(0).name());
            assertTrue(restored.days().get(0).legs().isEmpty());
            assertTrue(quote.journey().sections().isEmpty());
            assertEquals(Source.KAKAO_MAP, quote.admissions().get(0).source());
            assertFalse(quote.admissions().get(0).missing().isEmpty());

            days.set(0, new Day(DATE, List.of(address.id(), "seongsan"),
                    Map.of(address.id() + "~seongsan", new Leg(Mode.TAXI, null)), List.of(address)));
            var journey = plans.quote(new Plan(trip, days)).days().get(0).journey();
            assertEquals(1, journey.sections().size());
            assertEquals(address.longitude(), journey.sections().get(0).chosen().from().longitude());
            assertEquals("seongsan", journey.sections().get(0).chosen().to().id());
            assertEquals(Mode.TAXI, journey.sections().get(0).chosen().mode());

            var result = comparison.compare(new CompareRequest("jeju-day", DATE, PARTY,
                    List.of(place.id()), Map.of(), null, null, null, List.of(place)));
            assertEquals(place.name(), result.independentStops().get(0).name());
        } finally {
            plans.delete(saved.id());
        }
    }

    /** 웹 페이지가 열리고 잘못된 여행·투어 요청이 거절되는지 확인합니다. */
    @Test
    void rejectsInvalidInputsAndRendersPages() throws Exception {
        for (String path : List.of("/", "/search", "/itinerary", "/tours", "/compare")) {
            http.perform(get(path)).andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("TravelPlanner")));
        }
        http.perform(post("/api/routes/compare").contentType(MediaType.APPLICATION_JSON).content("""
                {"trip":{"origin":"서울","destination":"jeju","start":"2027-03-10","end":"2027-03-12",
                 "party":{"adults":0,"children":-1},"type":"FAMILY"}}
                """)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields['trip.party.adults']").exists());
        assertThrows(IllegalArgumentException.class, () -> catalog.validateTrip(new Trip(
                "seoul", "jeju", DATE, DATE.minusDays(1), PARTY, null, null, TripType.FAMILY)));
        http.perform(get("/api/destinations/search")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
        http.perform(get("/api/tours/search").param("destination", "jeju").param("date", "bad"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").isString());
    }

    /** 외부 투어 제공자가 실패해도 샘플 상품 검색이 유지되는지 검증합니다. */
    @Test
    void keepsSampleToursWhenAPartnerProviderFails() {
        TravelProviders.Tours failing = mock(TravelProviders.Tours.class);
        when(failing.platform()).thenReturn(Platform.NOL);
        when(failing.search("jeju")).thenThrow(new TravelProviders.Unavailable("timeout", null));
        var repository = mock(com.example.travelplanner.repository.TourRepository.class);
        when(repository.findByDestinationOrderByCheckedAtDesc("jeju")).thenReturn(List.of());
        var fallback = new TourService(catalog, repository,
                List.of(failing, new com.example.travelplanner.integration.SampleTours()), null);
        var result = fallback.search("jeju", DATE, null);
        assertEquals(1, result.notices().size());
        assertEquals("jeju-day", result.tours().get(0).id());
    }

    /** 직접 입력 투어를 등록하고 비교 화면에서 미확인 비용을 표시하는지 검증합니다. */
    @Test
    void registersDomesticTourWithUnconfirmedOptionalCosts() throws Exception {
        http.perform(post("/api/tours").contentType(MediaType.APPLICATION_JSON).content("""
                {"destination":"jeju","platform":"MYREALTRIP","name":"직접 등록","bookingUrl":"https://example.com/tour",
                 "departure":"09:00","arrival":"15:00","meetingPlace":"별도 집합장","endPlace":"제주시 중심","mode":"CITY_BUS",
                 "transportIncluded":true,"admissionIncluded":false,"mealIncluded":false,"guideIncluded":true,
                 "stops":["seongsan"],"prices":[{"from":"2027-03-10","to":"2027-03-12",
                 "fare":{"adult":30000,"child":20000}}],
                 "requiredExtras":{"shared":0}}
                """)).andExpect(status().isCreated()).andExpect(jsonPath("$.source").value("MANUAL"));
        var saved = tours.search("jeju", DATE, Platform.MYREALTRIP).tours().stream()
                .filter(tour -> tour.details().name().equals("직접 등록")).findFirst().orElseThrow();
        assertEquals(Source.MANUAL, tours.get(saved.id()).source());
        var result = comparison.compare(new CompareRequest(saved.id(), DATE, PARTY, null, null, null, null, null));
        assertEquals(List.of("숙소 → 집합 장소 가격"), result.guided().cost().missing());
        assertTrue(result.guided().items().stream().anyMatch(item -> item.label().equals("성산일출봉 입장료")));
    }

    /** 비용 비교 테스트에서 공통으로 쓰는 샘플 투어, 여행 날짜, 교통 변경 조건을 구성합니다. */
    private static CompareRequest request(LocalDate date, Map<String, Leg> legs) {
        return new CompareRequest("jeju-day", date, PARTY, null, legs, null, null, Pricing.free());
    }

    /** 요금 테스트용 성인·아동 금액을 원화 Fare 객체로 만듭니다. */
    private static Fare fare(int adult, int child) {
        return new Fare(BigDecimal.valueOf(adult), BigDecimal.valueOf(child), null);
    }

    /** 소수 자릿수 표기 차이를 제외하고 예상 금액과 실제 금액을 비교합니다. */
    private static void amount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> expected + " != " + actual);
    }
}
