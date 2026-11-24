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
        "KAKAO_MOBILITY_REST_API_KEY=",
        "KAKAO_MAP_REST_API_KEY="
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
    @Autowired ObjectMapper mapper;
    @MockitoBean KakaoPlaceSearch placeSearch;

    @Test
    void loadsRegionalToursAndResolvesTheirStopsThroughTheExistingCatalog() throws Exception {
        List<Tour> domestic = catalog.search("").stream().flatMap(region -> {
            List<Tour> regional = tours.search(region.id(), DATE, null).tours().stream()
                    .filter(tour -> tour.reference() != null).toList();
            assertEquals(3, regional.size(), region.id());
            for (Tour tour : regional) {
                assertEquals(tour.details().stops().size(), catalog.stops(region.id(), tour.details().stops()).size());
            }
            return regional.stream();
        }).toList();
        assertEquals(51, domestic.stream().map(Tour::id).distinct().count());
        assertEquals(30, domestic.stream().filter(tour -> tour.source() == Source.PUBLISHED).count());
        assertEquals(List.of("domestic-jeju-b"), tours.search("jeju", DATE, Platform.KKDAY).tours().stream()
                .map(Tour::id).toList());
        http.perform(get("/api/tours/domestic-gangwon-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("ESTIMATED"))
                .andExpect(jsonPath("$.details.prices[0].fare.adult").value(59000))
                .andExpect(jsonPath("$.details.prices[0].fare.child").value(59000));
    }

    @Test
    void multipliesEstimatedUnitFareByPartySizeAndPreservesPublishedDiscounts() {
        var estimate = tours.get("domestic-gangwon-b");
        for (Party party : List.of(new Party(1, 0), new Party(2, 1), new Party(6, 0), new Party(21, 0))) {
            var result = comparison.compare(new CompareRequest(estimate.id(), DATE, party,
                    null, Map.of(), null, null, null));
            var tourFare = result.guided().items().get(0);
            assertEquals("투어 예상 요금", tourFare.label());
            assertEquals(BigDecimal.valueOf(53000L * party.size()), tourFare.totalKrw());
            assertNull(result.guided().scheduledMinutes());
        }
        var incheon = tours.get("domestic-incheon-a");
        amount("9000", TourService.fare(incheon, DATE, new Party(9, 0)).adult());
        amount("7000", TourService.fare(incheon, DATE, new Party(10, 0)).adult());
    }

    /** Pricing이 성인과 아동 요금을 각 인원수에 맞게 합산하는지 검증합니다. */
    @Test
    void multipliesAdultAndChildFaresByHeadcount() {
        var fare = fare(65000, 49000);
        var cost = pricing.item("투어", fare, Source.MANUAL, PARTY);
        amount("179000", cost.totalKrw());
        amount("65000", pricing.total(List.of(cost), PARTY).adultPerPersonKrw());
        amount("49000", pricing.total(List.of(cost), PARTY).childPerPersonKrw());
    }

    /** 직접 입력한 구간 요금만 경로와 투어 비교에 반영되는지 검증합니다. */
    @Test
    void changingALegRecalculatesTheRouteAndTourComparison() {
        var before = routes.compare(new RouteRequest(TRIP, Map.of()));
        var segment = before.selected().sections().get(0);
        var taxiFare = new Fare(null, null, new BigDecimal("23000"));
        var after = routes.compare(new RouteRequest(TRIP, Map.of(segment.key(), new Leg(Mode.TAXI, taxiFare))));
        assertNotEquals(before.selected().cost().totalKrw(), after.selected().cost().totalKrw());
        assertNull(after.selected().minutes());

        var tour = registerDatedTour();
        var comparisonBefore = comparison.compare(request(tour.id(), DATE, Map.of()));
        var local = comparisonBefore.journey().sections().get(0);
        var comparisonAfter = comparison.compare(request(tour.id(), DATE, Map.of(local.key(), new Leg(Mode.TAXI, taxiFare))));
        assertNotEquals(comparisonBefore.independent().cost().totalKrw(), comparisonAfter.independent().cost().totalKrw());
        assertNull(comparisonAfter.independent().travelMinutes());
        assertNull(comparisonAfter.tourMinusIndependentKrw());
    }

    /** 투어와 자유여행 양쪽의 견적에서 관광지 입장료 항목을 제외합니다. */
    @Test
    void excludesAdmissionsFromBothSidesOfTourComparison() {
        var result = comparison.compare(request(registerDatedTour().id(), DATE, Map.of()));
        assertTrue(result.tour().details().admissionIncluded());
        assertTrue(result.guided().items().stream().noneMatch(item -> item.label().endsWith("입장료")));
        assertTrue(result.independent().items().stream().noneMatch(item -> item.label().endsWith("입장료")));
        assertEquals(result.journey().cost().totalKrw(), result.independent().cost().totalKrw());
    }

    @Test
    void itineraryCostDoesNotIncludeCatalogAdmission() {
        var trip = new Trip("seoul", "gyeongnam", DATE, DATE, PARTY, null, null, TripType.OTHER);
        var day = new Day(DATE, List.of("domestic-gyeongnam-b-1"), Map.of());
        var quote = plans.quote(new Plan(trip, List.of(day)));
        amount("0", quote.days().get(0).total().totalKrw());
        amount("0", quote.total().totalKrw());
        assertTrue(quote.total().missing().isEmpty());
    }

    /** 제주행 공항 연결을 유지하고 조회하지 않은 항공편의 요금과 시간을 비워 두는지 검증합니다. */
    @Test
    void addsAllAirportLegsAndCalculatesRoundTrip() {
        var result = routes.compare(new RouteRequest(TRIP, null));
        assertEquals(3, result.selected().sections().size());
        assertEquals(4, result.alternatives().size());
        assertEquals(List.of("gmp", "cju", "jeju"), result.selected().sections().stream()
                .map(section -> section.chosen().to().id()).toList());
        var flight = result.selected().sections().get(1).chosen();
        assertEquals(Mode.FLIGHT, flight.mode());
        assertNull(flight.fare());
        assertNull(flight.minutes());
        var amounts = result.selected().sections().stream().map(leg -> leg.cost().totalKrw())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        amount(amounts.toPlainString(), result.selected().cost().totalKrw());
        amount(amounts.multiply(BigDecimal.valueOf(2)).toPlainString(), result.roundTripKrw());
    }

    /** API 키가 없는 육상 경로에 가상 열차나 버스 운임이 추가되지 않는지 검증합니다. */
    @Test
    void keepsUnconfirmedRoadOptionsWhenProviderKeysAreAbsent() {
        var trip = new Trip("서울", "부산", DATE, DATE, PARTY, null, null, TripType.FAMILY);
        var result = routes.compare(new RouteRequest(trip, null));
        assertEquals(1, result.selected().sections().size());
        var options = result.selected().sections().get(0).options();
        assertEquals(List.of(Mode.CAR, Mode.TAXI), options.stream().map(Segment::mode).toList());
        assertTrue(options.stream().allMatch(segment -> segment.fare() == null && segment.minutes() == null
                && segment.kilometers() == null && segment.transfers() == null));
        assertFalse(result.selected().cost().isComplete());
        assertTrue(result.recommendations().isEmpty());
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
        var tour = registerDatedTour();
        var result = comparison.compare(request(tour.id(), DATE, Map.of()));
        assertEquals(result.tourStops(), result.independentStops());
        amount("179000", result.guided().cost().totalKrw());
        assertNull(result.tourMinusIndependentKrw());
        var april = comparison.compare(request(tour.id(), LocalDate.of(2027, 4, 1), Map.of()));
        amount("196000", april.guided().cost().totalKrw());
        var recommendations = tours.recommend(new TourRecommendationRequest("jeju", LocalDate.of(2027, 4, 1),
                PARTY, List.of("dongmun")));
        var registered = recommendations.stream().filter(item -> item.tour().id().equals(tour.id())).findFirst().orElseThrow();
        amount("72000", registered.fare().adult());
        var unmatched = comparison.compare(new CompareRequest(tour.id(), DATE, PARTY,
                List.of("dongmun"), Map.of(), null, null, null));
        assertTrue(unmatched.notices().stream().anyMatch(notice -> notice.startsWith("투어만 방문")));
        assertTrue(unmatched.recommendation().contains("보류"));
        var unavailable = comparison.compare(request(tour.id(), LocalDate.of(2040, 1, 1), Map.of()));
        assertFalse(unavailable.guided().cost().isComplete());
        assertTrue(tours.recommend(new TourRecommendationRequest("jeju", LocalDate.of(2040, 1, 1),
                PARTY, List.of("dongmun"))).stream().noneMatch(item -> item.tour().id().equals(tour.id())));
    }

    /** PlanService의 일정 등록, 조회, 수정, 목록, 삭제 흐름을 검증합니다. */
    @Test
    void savesEditsListsAndDeletesTheItinerary() {
        var plan = plans.generate(TRIP).plan();
        assertTrue(plan.days().stream().allMatch(day -> day.stops().isEmpty()));
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

    /** 의견의 순서와 교통수단은 기본 일정과 별도로 저장하고, 견적 합계는 기본 일정으로 유지합니다. */
    @Test
    void quotesAndStoresOpinionsSeparatelyFromTheMainItinerary() throws Exception {
        var trip = new Trip("seoul", "jeju", DATE, DATE.plusDays(1), PARTY, null, null, TripType.OTHER);
        var base = List.of(new Day(DATE, List.of("gimnyeong"), Map.of()),
                new Day(DATE.plusDays(1), List.of(), Map.of()));
        var place = new SearchedPlace("kakao-321", "제주목 관아", "제주특별자치도 제주시 관덕로 25", 33.513, 126.522);
        var stops = List.of("seongsan", place.id(), "seongsan");
        var legs = Map.of("seongsan~kakao-321", new Leg(Mode.TAXI, null));
        var draft = new Opinion(null, "바다 코스", stops, legs, List.of(place));
        var request = new OpinionRequest(trip, DATE, draft);
        var response = http.perform(post("/api/itineraries/opinions/quote").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.opinion.id").isString())
                .andExpect(jsonPath("$.day.stops[1].id").value(place.id()))
                .andExpect(jsonPath("$.day.journey.sections[0].chosen.mode").value("TAXI"))
                .andReturn().getResponse().getContentAsByteArray();
        var opinion = mapper.treeToValue(mapper.readTree(response).path("opinion"), Opinion.class);
        var another = plans.quoteOpinion(new OpinionRequest(trip, DATE,
                new Opinion(null, "시장 코스", List.of("dongmun"), Map.of(), List.of()))).opinion();
        var plan = new Plan(trip, List.of(new Day(DATE, base.get(0).stops(), Map.of(),
                List.of(), List.of(opinion, another)), base.get(1)));

        assertEquals(plans.quote(new Plan(trip, base)).total(), plans.quote(plan).total());
        var saved = plans.save(null, plan);
        try {
            var restored = plans.get(saved.id()).plan();
            assertEquals(base.get(0).stops(), restored.days().get(0).stops());
            assertEquals(List.of(opinion, another), restored.days().get(0).opinions());
            assertTrue(restored.days().get(1).opinions().isEmpty());
            assertEquals(stops, plans.quoteOpinion(new OpinionRequest(trip, DATE, opinion))
                    .day().stops().stream().map(Attraction::id).toList());
            assertThrows(IllegalArgumentException.class,
                    () -> plans.quoteOpinion(new OpinionRequest(trip, DATE.minusDays(1), opinion)));
        } finally {
            plans.delete(saved.id());
        }
        assertTrue(mapper.readValue("{\"date\":\"2027-03-10\",\"stops\":[],\"legs\":{}}", Day.class)
                .opinions().isEmpty());
    }

    @Test
    void savesRepeatedVisitsAndAppliesSeparateTransportChoices() {
        var place = new SearchedPlace("kakao-123", "제주목 관아", "제주특별자치도 제주시 관덕로 25", 33.513, 126.522);
        var trip = new Trip("seoul", "jeju", DATE, DATE, PARTY, null, null, TripType.OTHER);
        var stops = List.of(place.id(), place.id(), "seongsan", place.id(), "seongsan");
        var legs = Map.of("kakao-123~seongsan", new Leg(Mode.CAR, null),
                "kakao-123~seongsan~2", new Leg(Mode.TAXI, null));
        var saved = plans.save(null, new Plan(trip, List.of(new Day(DATE, stops, legs, List.of(place)))));
        try {
            var restored = plans.get(saved.id()).plan();
            assertEquals(stops, restored.days().get(0).stops());
            assertEquals(List.of(place), restored.days().get(0).places());
            var quote = plans.quote(restored).days().get(0);
            assertEquals(stops, quote.stops().stream().map(Attraction::id).toList());
            var sections = quote.journey().sections();
            assertEquals(List.of("kakao-123~kakao-123", "kakao-123~seongsan", "seongsan~kakao-123", "kakao-123~seongsan~2"),
                    sections.stream().map(JourneyService.Section::key).toList());
            assertEquals(0, sections.get(0).chosen().minutes());
            amount("0", sections.get(0).cost().totalKrw());
            assertTrue(sections.get(0).cost().missing().isEmpty());
            assertEquals(Mode.CAR, sections.get(1).chosen().mode());
            assertEquals(Mode.TAXI, sections.get(3).chosen().mode());
            var comparisonResult = comparison.compare(new CompareRequest("domestic-jeju-a", DATE, PARTY,
                    stops, legs, null, null, null, List.of(place)));
            assertEquals(stops, comparisonResult.independentStops().stream().map(Attraction::id).toList());
            assertThrows(IllegalArgumentException.class, () -> catalog.stops("jeju", List.of("seongsan", "seongsan")));
        } finally {
            plans.delete(saved.id());
        }
    }

    /** 검색한 장소의 좌표를 유지하고, 장소 한 곳에는 일정 교통비가 생기지 않는지 확인합니다. */
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
            amount("0", quote.total().totalKrw());
            assertTrue(quote.total().missing().isEmpty());

            days.set(0, new Day(DATE, List.of(address.id(), "seongsan"),
                    Map.of(address.id() + "~seongsan", new Leg(Mode.TAXI, null)), List.of(address)));
            var journey = plans.quote(new Plan(trip, days)).days().get(0).journey();
            assertEquals(1, journey.sections().size());
            assertEquals(address.longitude(), journey.sections().get(0).chosen().from().longitude());
            assertEquals("seongsan", journey.sections().get(0).chosen().to().id());
            assertEquals(Mode.TAXI, journey.sections().get(0).chosen().mode());

            var result = comparison.compare(new CompareRequest("domestic-jeju-a", DATE, PARTY,
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
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("TravelPlanner")))
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("샘플"))));
        }
        http.perform(get("/")).andExpect(content().string(org.hamcrest.Matchers.allOf(
                org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("name=\"budget\"")),
                org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("name=\"type\"")),
                org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("name=\"preferredMode\"")),
                org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("id=\"destinations-heading\"")),
                org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("id=\"find-origin\"")),
                org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("id=\"find-destination\"")),
                org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<datalist")))));
        http.perform(get("/api/tours/jeju-day")).andExpect(status().isNotFound());
        http.perform(post("/api/routes/compare").contentType(MediaType.APPLICATION_JSON).content("""
                {"trip":{"origin":"서울","destination":"jeju","start":"2027-03-10","end":"2027-03-12",
                 "party":{"adults":0,"children":-1},"type":"FAMILY"}}
                """)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields['trip.party.adults']").exists());
        assertThrows(IllegalArgumentException.class, () -> catalog.validateTrip(new Trip(
                "seoul", "jeju", DATE, DATE.minusDays(1), PARTY, null, null, TripType.FAMILY)));
        http.perform(get("/api/destinations/search")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(17));
        http.perform(get("/api/tours/search").param("destination", "jeju").param("date", "bad"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").isString());
    }

    @Test
    void routesAndSavedPlansPreserveSelectedPlacesWithinTheSameProvince() throws Exception {
        String tripJson = """
                {"origin":"수원역","destination":"성남시청","start":"2027-03-10","end":"2027-03-10",
                 "party":{"adults":1,"children":0},"type":"SOLO",
                 "originPlace":{"id":"kakao-101","name":"수원역","address":"경기도 수원시 팔달구 덕영대로 924",
                    "latitude":37.265,"longitude":127.000},
                 "destinationPlace":{"id":"kakao-102","name":"성남시청","address":"경기 성남시 중원구 성남대로 997",
                    "latitude":37.421,"longitude":127.126}}
                """;
        http.perform(post("/api/routes/compare").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trip\":" + tripJson + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trip.origin").value("gyeonggi"))
                .andExpect(jsonPath("$.trip.destination").value("gyeonggi"))
                .andExpect(jsonPath("$.selected.sections[0].chosen.from.latitude").value(37.265))
                .andExpect(jsonPath("$.selected.sections[0].chosen.to.id").value("kakao-102"));
        var from = new SearchedPlace("kakao-101", "수원역", "경기 수원시 팔달구 덕영대로 924", 37.265, 127.000);
        var to = new SearchedPlace("kakao-102", "성남시청", "경기도 성남시 중원구 성남대로 997", 37.421, 127.126);
        var trip = new Trip(from.name(), to.name(), DATE, DATE, PARTY, null, null, TripType.FAMILY, from, to);
        var generated = plans.generate(trip).plan();
        assertEquals(List.of(to.id()), generated.days().get(0).stops());
        var saved = plans.save(null, generated);
        try {
            assertEquals(generated, plans.get(saved.id()).plan());
        } finally {
            plans.delete(saved.id());
        }
    }

    @Test
    void newlySupportedRegionsCanRouteToJejuUsingAnAirportGateway() {
        var trip = new Trip("대전", "제주", DATE, DATE, PARTY, null, null, TripType.FAMILY);
        var sections = routes.compare(new RouteRequest(trip, null)).selected().sections();
        assertEquals("대전역", sections.get(0).chosen().from().name());
        assertEquals(Mode.FLIGHT, sections.get(1).chosen().mode());
        assertEquals("청주국제공항", sections.get(1).chosen().from().name());
    }

    /** 외부 투어 제공자가 실패해도 카탈로그 투어 검색이 유지되는지 검증합니다. */
    @Test
    void servesVersionedHomeAssetsAndRevalidatesModules() throws Exception {
        String html = http.perform(get("/")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var script = java.util.regex.Pattern.compile("src=\"(/[^/\"]+/js/)app\\.js\"").matcher(html);
        var css = java.util.regex.Pattern.compile("href=\"(/css/site-[0-9a-f]+\\.css)\"").matcher(html);
        assertTrue(script.find(), "JavaScript 진입점에 버전이 필요합니다.");
        assertTrue(css.find(), "스타일 경로에 내용 해시가 필요합니다.");
        http.perform(get(script.group(1) + "app.js")).andExpect(status().isOk());
        http.perform(get(script.group(1) + "common.js")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-cache"));
        http.perform(get(css.group(1))).andExpect(status().isOk());
    }

    @Test
    void recommendsToursBySharedStopsWithApplicablePrices() throws Exception {
        http.perform(get("/tours")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("id=\"tour-form\""))));
        http.perform(post("/api/tours/recommend").contentType(MediaType.APPLICATION_JSON).content("""
                {"destination":"seoul","date":"2027-03-10","party":{"adults":2,"children":1},
                 "stops":["domestic-seoul-a-2","kakao-123"]}
                """)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].tour.id").value("domestic-seoul-a"))
                .andExpect(jsonPath("$[0].stops[0].name").value("경복궁"))
                .andExpect(jsonPath("$[0].stops[1].name").value("북촌한옥마을"))
                .andExpect(jsonPath("$[0].fare.adult").value(27000));

        var busanStops = List.of("domestic-busan-a-2", "haeundae");
        var group = tours.recommend(new TourRecommendationRequest("busan", DATE, new Party(6, 0), busanStops));
        assertEquals(List.of("domestic-busan-a", "domestic-busan-c"),
                group.stream().map(result -> result.tour().id()).toList());
        amount("59000", group.get(0).fare().adult());
        amount("59000", tours.recommend(new TourRecommendationRequest("busan", DATE, PARTY, busanStops)).get(0).fare().child());
        assertTrue(tours.recommend(new TourRecommendationRequest("busan", DATE, PARTY,
                List.of("domestic-seoul-a-2"))).isEmpty());
    }

    @Test
    void keepsCatalogToursWhenAPartnerProviderFails() throws Exception {
        TravelProviders.Tours failing = mock(TravelProviders.Tours.class);
        when(failing.platform()).thenReturn(Platform.NOL);
        when(failing.search("jeju")).thenThrow(new TravelProviders.Unavailable("timeout", null));
        var repository = mock(com.example.travelplanner.repository.TourRepository.class);
        when(repository.findByDestinationOrderByCheckedAtDesc("jeju")).thenReturn(List.of());
        var fallback = new TourService(catalog, repository,
                List.of(failing, new com.example.travelplanner.integration.DomesticTours(new ObjectMapper())), null);
        var result = fallback.search("jeju", DATE, null);
        assertEquals(1, result.notices().size());
        assertEquals(List.of("domestic-jeju-a", "domestic-jeju-b", "domestic-jeju-c"),
                result.tours().stream().map(Tour::id).toList());
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
        assertTrue(result.guided().items().stream().noneMatch(item -> item.label().endsWith("입장료")));
    }

    /** 날짜별 요금이 필요한 비용 비교 테스트의 상품을 인메모리 DB에 등록합니다. */
    private Tour registerDatedTour() {
        return tours.register(new TourInput("jeju", Platform.OTHER, "날짜별 요금 검증", null, "https://example.com/tour",
                java.time.LocalTime.of(9, 0), java.time.LocalTime.of(17, 0), "제주시 중심", "제주시 중심", null, Mode.CITY_BUS,
                true, true, false, true, List.of("dongmun", "doldam", "gimnyeong", "seongsan"),
                List.of(new Price(DATE, DATE.plusDays(3), fare(65000, 49000)),
                        new Price(LocalDate.of(2027, 4, 1), LocalDate.of(2027, 4, 30), fare(72000, 52000))), Pricing.free(), null));
    }

    private static CompareRequest request(String id, LocalDate date, Map<String, Leg> legs) {
        return new CompareRequest(id, date, PARTY, null, legs, null, null, Pricing.free());
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
