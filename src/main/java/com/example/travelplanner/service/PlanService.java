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

    /** 관광지, 여정, 요금 계산, 저장소와 JSON 변환을 일정 기능에 연결합니다. */
    public PlanService(CatalogService catalog, JourneyService journeys, Pricing pricing,
                       PlanRepository repository, ObjectMapper json) {
        this.catalog = catalog;
        this.journeys = journeys;
        this.pricing = pricing;
        this.repository = repository;
        this.json = json;
    }

    /**
     * 여행 날짜별로 목적지 관광지를 나누어 기본 일정을 만들고 견적을 계산합니다.
     *
     * TravelApi의 일정 생성 API가 홈에서 입력한 여행 조건을 넘깁니다. 도시 ID와 기간을 확인한 뒤 관광지를 날짜별로 나누고 quote로 견적을 계산합니다.
     *
     * 관광지가 배정되지 않는 날도 날짜 목록에 남습니다. 이 시점의 일정은 화면 편집에 사용되며 저장은 save에서 처리합니다.
     */
    public PlanQuote generate(Trip rawTrip) {
        Trip trip = catalog.validateTrip(rawTrip);
        List<String> sights = catalog.attractions(trip.destination(), "").stream().map(Attraction::id).toList();
        List<LocalDate> dates = dates(trip);
        List<Day> days = new ArrayList<>();
        int perDay = Math.max(1, (sights.size() + dates.size() - 1) / dates.size());
        for (int i = 0; i < dates.size(); i++) {
            int from = Math.min(sights.size(), i * perDay);
            int to = Math.min(sights.size(), from + perDay);
            List<String> stops = sights.subList(from, to);
            if (i == 0 && trip.destinationPlace() != null) {
                List<String> firstDayStops = new ArrayList<>();
                firstDayStops.add(trip.destinationPlace().id());
                firstDayStops.addAll(stops);
                days.add(new Day(dates.get(i), firstDayStops, Map.of(), List.of(trip.destinationPlace())));
            } else {
                days.add(new Day(dates.get(i), stops, Map.of()));
            }
        }
        return quote(new Plan(trip, days));
    }

    /**
     * 날짜와 방문지를 확인한 뒤 일차별 교통비·입장료와 전체 일정 합계를 계산합니다.
     *
     * plan.trip으로 기본 여행 조건을 검증하고 plan.days의 날짜가 시작일부터 종료일까지 순서대로 있는지 확인합니다.
     *
     * 방문지 간 이동은 JourneyService에서, 입장료는 admissions에서 계산합니다. 반환되는 PlanQuote에는 정규화된 계획, 일차별 견적, 전체 합계가 들어 있습니다.
     */
    public PlanQuote quote(Plan plan) {
        Trip trip = catalog.validateTrip(plan.trip());
        List<LocalDate> dates = dates(trip);
        if (!plan.days().stream().map(Day::date).toList().equals(dates)) {
            throw new IllegalArgumentException("일정 날짜는 여행 시작일부터 종료일까지 순서대로 입력해야 합니다.");
        }
        List<Item> all = new ArrayList<>();
        List<DayQuote> days = new ArrayList<>();
        List<Day> normalizedDays = new ArrayList<>();
        for (Day day : plan.days()) {
            List<Attraction> stops = catalog.stops(trip.destination(), day.stops(), day.places());
            var legs = journeys.visitLegs(trip.destination(), stops, day.legs());
            Journey journey = journeys.visits(stops, legs, trip.preferredMode(), trip.party());
            normalizedDays.add(new Day(day.date(), day.stops(), legs, day.places()));
            List<Item> admissions = admissions(stops, trip.party());
            List<Item> costs = new ArrayList<>(admissions);
            costs.addAll(journey.sections().stream().map(JourneyService.Section::cost).toList());
            all.addAll(costs);
            days.add(new DayQuote(day.date(), stops, journey, admissions, pricing.total(costs, trip.party()),
                    stops.stream().mapToInt(Attraction::visitMinutes).sum()));
        }
        return new PlanQuote(new Plan(trip, List.copyOf(normalizedDays)), List.copyOf(days), pricing.total(all, trip.party()));
    }

    /**
     * 방문 관광지별 입장료를 인원에 맞춰 계산합니다. 일정과 투어 비교에서 함께 사용합니다.
     *
     * stops는 방문 순서가 확인된 관광지이고 party는 성인·아동 인원입니다. 반환 목록도 방문 순서를 유지해 화면이 관광지와 입장료를 같은 위치에서 읽을 수 있습니다.
     */
    public List<Item> admissions(List<Attraction> stops, Party party) {
        return stops.stream().map(stop -> pricing.item(stop.name() + " 입장료", stop.admission(),
                stop.id().startsWith("kakao-") ? Source.KAKAO_MAP : Source.SAMPLE, party)).toList();
    }

    /**
     * 견적을 다시 계산해 일정이 유효한지 확인하고, 새 계획을 등록하거나 기존 기록을 갱신합니다.
     *
     * id가 비어 있으면 새 UUID를 만들고, 기존 id가 들어오면 해당 레코드를 조회합니다.
     *
     * quote를 통해 확인한 계획의 JSON을 저장합니다. API 응답에는 다음 수정·삭제에 쓸 ID와 수정 시각이 포함됩니다.
     */
    @Transactional
    public StoredPlan save(UUID id, Plan plan) {
        Plan normalized = quote(plan).plan();
        SavedPlan entity = id == null ? new SavedPlan(UUID.randomUUID()) : require(id);
        entity.update(normalized.trip().destination(), json.writeValueAsString(normalized));
        return from(repository.save(entity));
    }

    /** 저장 목록에서 요청한 일정 ID를 조회해 화면에 전달할 전체 계획을 반환합니다. */
    public StoredPlan get(UUID id) { return from(require(id)); }

    /** DB에서 저장 일정을 최근 수정 순으로 읽어 목록 화면용 요약으로 변환합니다. */
    public List<PlanSummary> list() {
        return repository.findAllByOrderByUpdatedAtDesc().stream().map(this::from)
                .map(saved -> new PlanSummary(saved.id(), saved.plan().trip(), saved.updatedAt())).toList();
    }

    /** 요청된 일정이 있는지 확인하고 저장소에서 삭제합니다. */
    @Transactional
    public void delete(UUID id) { repository.delete(require(id)); }

    /** 조회·수정·삭제가 공유하는 일정 확인 함수입니다. ID가 없으면 404 오류로 이어집니다. */
    private SavedPlan require(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NotFound("저장된 여행 계획을 찾을 수 없습니다."));
    }

    /**
     * DB에 저장된 일정 JSON을 계획 객체로 복원하고 ID와 수정 시각을 함께 반환합니다.
     *
     * 일정 저장소는 계획을 JSON payload로 보관합니다. 이 함수는 저장 목록과 상세 조회에서 payload를 Plan으로 읽어 공통 응답 형태로 변환합니다.
     */
    private StoredPlan from(SavedPlan entity) {
        return new StoredPlan(entity.id(), json.readValue(entity.payload(), Plan.class), entity.updatedAt());
    }

    /** 여행 시작일과 종료일을 포함한 날짜 목록을 만듭니다. 일정 생성과 유효성 검사에 사용합니다. */
    private static List<LocalDate> dates(Trip trip) {
        int count = (int) ChronoUnit.DAYS.between(trip.start(), trip.end()) + 1;
        return IntStream.range(0, count).mapToObj(trip.start()::plusDays).toList();
    }
}
