package com.example.travelplanner.controller;

import com.example.travelplanner.dto.Travel.*;
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

    public TravelApi(CatalogService catalog, JourneyService journeys, PlanService plans,
                     TourService tours, ComparisonService comparison) {
        this.catalog = catalog;
        this.journeys = journeys;
        this.plans = plans;
        this.tours = tours;
        this.comparison = comparison;
    }

    @GetMapping("/destinations/search")
    public List<Destination> destinations(@RequestParam(defaultValue = "") String q) {
        return catalog.search(q);
    }

    @GetMapping("/attractions")
    public List<Attraction> attractions(@RequestParam(required = false) String destination,
                                        @RequestParam(defaultValue = "") String q) {
        return catalog.attractions(destination, q);
    }

    @GetMapping("/attractions/{id}")
    public Attraction attraction(@PathVariable String id) { return catalog.attraction(id); }

    @PostMapping("/routes/compare")
    public JourneyService.RouteResults routes(@Valid @RequestBody RouteRequest request) {
        return journeys.compare(request);
    }

    @PostMapping("/itineraries/generate")
    public PlanService.PlanQuote generate(@Valid @RequestBody Trip trip) { return plans.generate(trip); }

    @PostMapping("/itineraries/quote")
    public PlanService.PlanQuote quote(@Valid @RequestBody Plan plan) { return plans.quote(plan); }

    @GetMapping("/travel-plans")
    public List<PlanService.PlanSummary> plans() { return plans.list(); }

    @PostMapping("/travel-plans")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanService.StoredPlan save(@Valid @RequestBody Plan plan) { return plans.save(null, plan); }

    @GetMapping("/travel-plans/{id}")
    public PlanService.StoredPlan plan(@PathVariable UUID id) { return plans.get(id); }

    @PutMapping("/travel-plans/{id}")
    public PlanService.StoredPlan update(@PathVariable UUID id, @Valid @RequestBody Plan plan) {
        return plans.save(id, plan);
    }

    @DeleteMapping("/travel-plans/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { plans.delete(id); }

    @GetMapping("/tours/search")
    public TourService.SearchResult tourSearch(@RequestParam String destination,
                                               @RequestParam(required = false) LocalDate date,
                                               @RequestParam(required = false) Platform platform) {
        return tours.search(destination, date, platform);
    }

    @PostMapping("/tours")
    @ResponseStatus(HttpStatus.CREATED)
    public Tour register(@Valid @RequestBody TourInput tour) { return tours.register(tour); }

    @GetMapping("/tours/{id}")
    public Tour tour(@PathVariable String id) { return tours.get(id); }

    @PostMapping({"/compare", "/compare/recalculate"})
    public ComparisonService.Result compare(@Valid @RequestBody CompareRequest request) {
        return comparison.compare(request);
    }
}
