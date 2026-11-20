package com.example.travelplanner.service;

import com.example.travelplanner.dto.Travel.*;
import com.example.travelplanner.entity.SavedPlan;
import com.example.travelplanner.exception.NotFound;
import com.example.travelplanner.pricing.Pricing;
import com.example.travelplanner.pricing.Pricing.Item;
import com.example.travelplanner.pricing.Pricing.Total;
import com.example.travelplanner.repository.PlanRepository;
import com.example.travelplanner.service.JourneyService.Journey;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.IntStream;

@Service
public class PlanService {
    private final CatalogService catalog;
    private final JourneyService journeys;
    private final Pricing pricing;
    private final PlanRepository repository;
    private final ObjectMapper json;

    public record DayQuote(LocalDate date, List<Attraction> stops, Journey journey,
                           List<Item> admissions, Total total, int visitMinutes) {}
    public record PlanQuote(Plan plan, List<DayQuote> days, Total total) {}
    public record StoredPlan(UUID id, Plan plan, Instant updatedAt) {}
    public record PlanSummary(UUID id, Trip trip, Instant updatedAt) {}

    public PlanService(CatalogService catalog, JourneyService journeys, Pricing pricing,
                       PlanRepository repository, ObjectMapper json) {
        this.catalog = catalog;
        this.journeys = journeys;
        this.pricing = pricing;
        this.repository = repository;
        this.json = json;
    }

    public PlanQuote generate(Trip rawTrip) {
        Trip trip = catalog.validateTrip(rawTrip);
        List<String> sights = catalog.attractions(trip.destination(), "").stream().map(Attraction::id).toList();
        List<LocalDate> dates = dates(trip);
        List<Day> days = new ArrayList<>();
        if (trip.destination().equals("osaka") && dates.size() >= 3) {
            List<String> kyoto = catalog.attractions("kyoto", "").stream().map(Attraction::id).toList();
            for (int i = 0; i < dates.size(); i++) {
                List<String> stops = switch (i) {
                    case 0 -> sights.stream().limit(1).toList();
                    case 1 -> kyoto;
                    case 2 -> sights.stream().skip(1).toList();
                    default -> List.of();
                };
                days.add(new Day(dates.get(i), stops, Map.of()));
            }
            return quote(new Plan(trip, days));
        }
        int perDay = Math.max(1, (sights.size() + dates.size() - 1) / dates.size());
        for (int i = 0; i < dates.size(); i++) {
            int from = Math.min(sights.size(), i * perDay);
            int to = Math.min(sights.size(), from + perDay);
            days.add(new Day(dates.get(i), sights.subList(from, to), Map.of()));
        }
        return quote(new Plan(trip, days));
    }

    public PlanQuote quote(Plan plan) {
        Trip trip = catalog.validateTrip(plan.trip());
        List<LocalDate> dates = dates(trip);
        if (!plan.days().stream().map(Day::date).toList().equals(dates)) {
            throw new IllegalArgumentException("일정 날짜는 여행 시작일부터 종료일까지 순서대로 입력해야 합니다.");
        }
        List<Item> all = new ArrayList<>();
        List<DayQuote> days = new ArrayList<>();
        for (Day day : plan.days()) {
            List<Attraction> stops = catalog.stops(trip.destination(), day.stops());
            Journey journey = journeys.loop(trip.destination(), stops, day.legs(), trip.preferredMode(), trip.party(), day.date());
            List<Item> admissions = admissions(stops, trip.party(), day.date());
            List<Item> costs = new ArrayList<>(admissions);
            costs.addAll(journey.sections().stream().map(JourneyService.Section::cost).toList());
            all.addAll(costs);
            days.add(new DayQuote(day.date(), stops, journey, admissions, pricing.total(costs, trip.party()),
                    stops.stream().mapToInt(Attraction::visitMinutes).sum()));
        }
        return new PlanQuote(new Plan(trip, plan.days()), List.copyOf(days), pricing.total(all, trip.party()));
    }

    public List<Item> admissions(List<Attraction> stops, Party party, LocalDate date) {
        return stops.stream().map(stop -> pricing.item(stop.name() + " 입장료", stop.admission(), Source.SAMPLE, party, date)).toList();
    }

    @Transactional
    public StoredPlan save(UUID id, Plan plan) {
        Plan normalized = quote(plan).plan();
        SavedPlan entity = id == null ? new SavedPlan(UUID.randomUUID()) : require(id);
        entity.update(normalized.trip().destination(), json.writeValueAsString(normalized));
        return from(repository.save(entity));
    }

    public StoredPlan get(UUID id) { return from(require(id)); }

    public List<PlanSummary> list() {
        return repository.findAllByOrderByUpdatedAtDesc().stream().map(this::from)
                .map(saved -> new PlanSummary(saved.id(), saved.plan().trip(), saved.updatedAt())).toList();
    }

    @Transactional
    public void delete(UUID id) { repository.delete(require(id)); }

    private SavedPlan require(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NotFound("저장된 여행 계획을 찾을 수 없습니다."));
    }

    private StoredPlan from(SavedPlan entity) {
        return new StoredPlan(entity.id(), json.readValue(entity.payload(), Plan.class), entity.updatedAt());
    }

    private static List<LocalDate> dates(Trip trip) {
        int count = (int) ChronoUnit.DAYS.between(trip.start(), trip.end()) + 1;
        return IntStream.range(0, count).mapToObj(trip.start()::plusDays).toList();
    }
}
