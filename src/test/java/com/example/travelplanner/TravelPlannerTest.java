package com.example.travelplanner;

import com.example.travelplanner.dto.Travel.*;
import com.example.travelplanner.integration.TravelProviders;
import com.example.travelplanner.pricing.Pricing;
import com.example.travelplanner.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:travelplanner-tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class TravelPlannerTest {
    private static final LocalDate DATE = LocalDate.of(2027, 3, 10);
    private static final Party PARTY = new Party(2, 1);
    private static final Trip TRIP = new Trip("서울", "Osaka", DATE, DATE.plusDays(3), PARTY, null, null, TripType.FAMILY);
    @Autowired Pricing pricing;
    @Autowired CatalogService catalog;
    @Autowired JourneyService routes;
    @Autowired PlanService plans;
    @Autowired TourService tours;
    @Autowired ComparisonService comparison;
    @Autowired MockMvc http;

    @Test
    void multipliesAdultAndChildFaresByHeadcount() {
        var fare = fare(Currency.KRW, 65000, 49000);
        var cost = pricing.item("투어", fare, Source.SAMPLE, PARTY, DATE);
        amount("179000", cost.totalKrw());
        amount("65000", pricing.total(List.of(cost), PARTY).adultPerPersonKrw());
        amount("49000", pricing.total(List.of(cost), PARTY).childPerPersonKrw());
    }

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

    @Test
    void includedAdmissionIsOnlyChargedToIndependentVisitors() {
        var result = comparison.compare(request(DATE, Map.of()));
        assertTrue(result.tour().details().admissionIncluded());
        assertTrue(result.guided().items().stream().noneMatch(item -> item.label().endsWith("입장료")));
        assertEquals(4, result.independent().items().stream().filter(item -> item.label().endsWith("입장료")).count());
    }

    @Test
    void addsAllAirportLegsAndCalculatesRoundTrip() {
        var result = routes.compare(new RouteRequest(TRIP, null));
        assertEquals(3, result.selected().sections().size());
        assertEquals(9, result.alternatives().size());
        var amounts = result.selected().sections().stream().map(leg -> leg.cost().totalKrw())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        amount(amounts.toPlainString(), result.selected().cost().totalKrw());
        amount(amounts.multiply(BigDecimal.valueOf(2)).toPlainString(), result.roundTripKrw());
    }

    @Test
    void sharedTaxiIsOnlyChargedOnceAndSplitEqually() {
        var fare = new Fare(Currency.KRW, null, null, new BigDecimal("12000"));
        var cost = pricing.total(List.of(pricing.item("택시", fare, Source.MANUAL, PARTY, DATE)), PARTY);
        amount("12000", cost.totalKrw());
        amount("4000", cost.adultPerPersonKrw());
        amount("4000", cost.childPerPersonKrw());
    }

    @Test
    void exchangeRateHasADateAndUnknownRatesAndChildFaresAreFlagged() {
        var item = pricing.item("입장료", fare(Currency.JPY, 500, 250), Source.SAMPLE, PARTY, DATE);
        amount("11500", item.totalKrw());
        assertEquals(LocalDate.of(2026, 11, 20), item.rate().date());
        Pricing noRates = new Pricing((currency, date) -> Optional.empty());
        assertEquals(List.of("입장료 환율"), noRates.item("입장료", fare(Currency.JPY, 500, 250),
                Source.MANUAL, PARTY, DATE).missing());
        assertEquals(List.of("입장료 아동 요금"), pricing.item("입장료",
                new Fare(Currency.KRW, BigDecimal.ONE, null, null), Source.MANUAL, PARTY, DATE).missing());
    }

    @Test
    void comparesMatchingStopsAndAppliesDatedTourPricing() {
        var result = comparison.compare(request(DATE, Map.of()));
        assertEquals(result.tourStops(), result.independentStops());
        amount("179000", result.guided().cost().totalKrw());
        amount(result.guided().cost().totalKrw().subtract(result.independent().cost().totalKrw()).toPlainString(),
                result.tourMinusIndependentKrw());
        var april = comparison.compare(request(LocalDate.of(2027, 4, 1), Map.of()));
        amount("196000", april.guided().cost().totalKrw());
        var unmatched = comparison.compare(new CompareRequest("kyoto-day", DATE, "kyoto", PARTY,
                List.of("fushimi"), Map.of(), null, null, null));
        assertTrue(unmatched.notices().stream().anyMatch(notice -> notice.startsWith("투어만 방문")));
        assertTrue(unmatched.recommendation().contains("보류"));
        var unavailable = comparison.compare(request(LocalDate.of(2040, 1, 1), Map.of()));
        assertNull(unavailable.tourMinusIndependentKrw());
    }

    @Test
    void savesEditsListsAndDeletesTheItinerary() {
        var plan = plans.generate(TRIP).plan();
        assertEquals(List.of("fushimi", "kiyomizu", "arashiyama", "kinkaku"), plan.days().get(1).stops());
        var saved = plans.save(null, plan);
        assertEquals(plan, plans.get(saved.id()).plan());
        assertTrue(plans.list().stream().anyMatch(item -> item.id().equals(saved.id())));
        List<Day> changed = new ArrayList<>(plan.days());
        changed.set(0, new Day(DATE, List.of("umeda"), Map.of()));
        var update = new Plan(plan.trip(), changed);
        plans.save(saved.id(), update);
        assertEquals(update, plans.get(saved.id()).plan());
        plans.delete(saved.id());
        assertThrows(com.example.travelplanner.exception.NotFound.class, () -> plans.get(saved.id()));
    }

    @Test
    void rejectsInvalidInputsAndRendersPages() throws Exception {
        for (String path : List.of("/", "/search", "/itinerary", "/tours", "/compare")) {
            http.perform(get(path)).andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("TravelPlanner")));
        }
        http.perform(post("/api/routes/compare").contentType(MediaType.APPLICATION_JSON).content("""
                {"trip":{"origin":"서울","destination":"osaka","start":"2027-03-10","end":"2027-03-12",
                 "party":{"adults":0,"children":-1},"type":"FAMILY"}}
                """)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields['trip.party.adults']").exists());
        assertThrows(IllegalArgumentException.class, () -> catalog.validateTrip(new Trip(
                "seoul", "osaka", DATE, DATE.minusDays(1), PARTY, null, null, TripType.FAMILY)));
        http.perform(get("/api/tours/search").param("destination", "kyoto").param("date", "bad"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").isString());
    }

    @Test
    void keepsSampleToursWhenAPartnerProviderFails() {
        TravelProviders.Tours failing = mock(TravelProviders.Tours.class);
        when(failing.platform()).thenReturn(Platform.NOL);
        when(failing.search("kyoto")).thenThrow(new TravelProviders.Unavailable("timeout", null));
        var repository = mock(com.example.travelplanner.repository.TourRepository.class);
        when(repository.findByDestinationOrderByCheckedAtDesc("kyoto")).thenReturn(List.of());
        var fallback = new TourService(catalog, repository,
                List.of(failing, new com.example.travelplanner.integration.SampleTours()), null);
        var result = fallback.search("kyoto", DATE, null);
        assertEquals(1, result.notices().size());
        assertEquals("kyoto-day", result.tours().get(0).id());
    }

    @Test
    void registersManualTourInAnotherCurrencyWithUnconfirmedOptionalCosts() throws Exception {
        http.perform(post("/api/tours").contentType(MediaType.APPLICATION_JSON).content("""
                {"destination":"kyoto","platform":"MYREALTRIP","name":"직접 등록","bookingUrl":"https://example.com/tour",
                 "departure":"09:00","arrival":"15:00","meetingPlace":"별도 집합장","endPlace":"교토역","mode":"CITY_BUS",
                 "transportIncluded":true,"admissionIncluded":false,"mealIncluded":false,"guideIncluded":true,
                 "stops":["kiyomizu"],"prices":[{"from":"2027-03-10","to":"2027-03-12",
                 "fare":{"currency":"JPY","adult":3000,"child":2000}}],
                 "requiredExtras":{"currency":"KRW","shared":0}}
                """)).andExpect(status().isCreated()).andExpect(jsonPath("$.source").value("MANUAL"));
        var saved = tours.search("kyoto", DATE, Platform.MYREALTRIP).tours().stream()
                .filter(tour -> tour.details().name().equals("직접 등록")).findFirst().orElseThrow();
        assertEquals(Source.MANUAL, tours.get(saved.id()).source());
        var result = comparison.compare(new CompareRequest(saved.id(), DATE, "kyoto", PARTY, null, null, null, null, null));
        assertEquals(List.of("숙소 → 집합 장소 가격"), result.guided().cost().missing());
        assertTrue(result.guided().items().stream().anyMatch(item -> item.label().equals("기요미즈데라 입장료")));
    }

    private static CompareRequest request(LocalDate date, Map<String, Leg> legs) {
        return new CompareRequest("kyoto-day", date, "kyoto", PARTY, null, legs, null, null, Pricing.free());
    }

    private static Fare fare(Currency currency, int adult, int child) {
        return new Fare(currency, BigDecimal.valueOf(adult), BigDecimal.valueOf(child), null);
    }

    private static void amount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> expected + " != " + actual);
    }
}
