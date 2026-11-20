package com.example.travelplanner.service;

import com.example.travelplanner.dto.Travel.*;
import com.example.travelplanner.pricing.Pricing;
import com.example.travelplanner.pricing.Pricing.Item;
import com.example.travelplanner.pricing.Pricing.Total;
import com.example.travelplanner.service.JourneyService.Journey;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class ComparisonService {
    private final TourService tours;
    private final CatalogService catalog;
    private final JourneyService journeys;
    private final Pricing pricing;
    private final PlanService plans;

    public record Side(List<Item> items, Total cost, Integer travelMinutes, int scheduledMinutes, String convenience) {}
    public record Result(Tour tour, List<Attraction> tourStops, List<Attraction> independentStops, Side guided,
                         Side independent, Journey journey, BigDecimal tourMinusIndependentKrw,
                         List<String> notices, String recommendation) {}

    public ComparisonService(TourService tours, CatalogService catalog, JourneyService journeys, Pricing pricing,
                             PlanService plans) {
        this.tours = tours;
        this.catalog = catalog;
        this.journeys = journeys;
        this.pricing = pricing;
        this.plans = plans;
    }

    public Result compare(CompareRequest request) {
        Tour tour = tours.get(request.tourId());
        TourInput detail = tour.details();
        String base = request.lodgingCity() == null || request.lodgingCity().isBlank()
                ? detail.destination() : catalog.destination(request.lodgingCity()).id();
        if (!catalog.destination(base).country().equals(catalog.destination(detail.destination()).country())) {
            throw new IllegalArgumentException("투어와 숙소는 같은 국가에서 선택해 주세요.");
        }
        List<Attraction> tourStops = catalog.stops(base, detail.stops());
        List<Attraction> independentStops = request.stops() == null ? tourStops : catalog.stops(base, request.stops());
        if (independentStops.isEmpty()) throw new IllegalArgumentException("자유여행 관광지를 한 곳 이상 선택해 주세요.");
        Journey local = journeys.loop(base, independentStops, request.legs(), null, request.party(), request.date());
        List<Item> own = new ArrayList<>(local.sections().stream().map(JourneyService.Section::cost).toList());
        own.addAll(plans.admissions(independentStops, request.party(), request.date()));
        if (request.independentExtras() != null) {
            own.add(item("자유여행 필수 추가비", request.independentExtras(), Source.MANUAL, request));
        }

        List<Item> guided = new ArrayList<>();
        guided.add(item("투어 상품 가격", TourService.fare(tour, request.date()), tour.source(), request));
        String tourCity = detail.destination();
        String centerName = catalog.center(tourCity).name();
        Integer outboundMinutes = access(guided, "숙소 → 집합 장소", request.tourOutbound(), detail.meetingPlace(),
                centerName, base, tourCity, request);
        Integer returnMinutes = access(guided, "종료 장소 → 숙소", request.tourReturn(), detail.endPlace(),
                centerName, tourCity, base, request);
        if (!detail.admissionIncluded()) guided.addAll(plans.admissions(tourStops, request.party(), request.date()));
        if (!detail.transportIncluded()) {
            guided.add(item("투어 불포함 관광지 이동비", detail.excludedTransport(), tour.source(), request));
        }
        guided.add(item("투어 기타 필수비", detail.requiredExtras(), tour.source(), request));

        Total tourCost = pricing.total(guided, request.party());
        Total soloCost = pricing.total(own, request.party());
        int duration = (int) Duration.between(detail.departure(), detail.arrival()).toMinutes();
        if (duration < 0) duration += 24 * 60;
        Integer accessMinutes = outboundMinutes != null && returnMinutes != null ? outboundMinutes + returnMinutes : null;
        Integer tourTravel = tour.id().equals("kyoto-day") && accessMinutes != null ? 120 + accessMinutes : null;
        Side tourSide = new Side(List.copyOf(guided), tourCost, tourTravel, duration + (accessMinutes == null ? 0 : accessMinutes),
                "정해진 집합 시간과 방문 순서" + (detail.guideIncluded() ? " · 가이드 포함" : ""));
        Side soloSide = new Side(List.copyOf(own), soloCost, local.minutes(),
                local.minutes() + independentStops.stream().mapToInt(Attraction::visitMinutes).sum(),
                "방문 순서와 체류 시간을 직접 조정");
        List<String> notices = new ArrayList<>();
        List<String> tourOnly = tourStops.stream().filter(stop -> !independentStops.contains(stop)).map(Attraction::name).toList();
        List<String> soloOnly = independentStops.stream().filter(stop -> !tourStops.contains(stop)).map(Attraction::name).toList();
        if (!tourOnly.isEmpty()) notices.add("투어만 방문: " + String.join(", ", tourOnly));
        if (!soloOnly.isEmpty()) notices.add("자유여행만 방문: " + String.join(", ", soloOnly));
        if (!tourCost.isComplete() || !soloCost.isComplete()) notices.add("미확인 항목은 부분 합계에서 제외됩니다.");
        if (request.independentExtras() == null) notices.add("자유여행 추가비는 입력한 경우 합산합니다.");
        if (accessMinutes == null) notices.add("투어 일정 시간에는 이동 시간이 확인되지 않은 집합·종료 구간이 포함되지 않습니다.");
        BigDecimal difference = tourCost.isComplete() && soloCost.isComplete()
                ? tourCost.totalKrw().subtract(soloCost.totalKrw()) : null;
        String recommendation = "비용 추천 보류 · 같은 관광지와 확인 가능한 비용이 필요합니다.";
        if (difference != null && tourOnly.isEmpty() && soloOnly.isEmpty()) {
            recommendation = switch (difference.signum()) {
                case 1 -> "비용 우선 · 자유여행 예상 총액이 더 낮습니다.";
                case -1 -> "비용 우선 · 일일투어 예상 총액이 더 낮습니다.";
                default -> "두 방식의 예상 비용이 같습니다. 방문 순서와 가이드 포함 여부를 비교해 보세요.";
            };
        }
        return new Result(tour, tourStops, independentStops, tourSide, soloSide, local, difference,
                List.copyOf(notices), recommendation);
    }

    private Integer access(List<Item> costs, String name, Fare override, String place, String center,
                           String from, String to, CompareRequest request) {
        if (!center.equals(place)) {
            costs.add(item(name, override, Source.MANUAL, request));
            return null;
        }
        if (from.equals(to)) {
            costs.add(item(name, override == null ? Pricing.free() : override,
                    override == null ? Source.SAMPLE : Source.MANUAL, request));
            return 0;
        }
        Journey access = journeys.betweenCities(from, to, request.party(), request.date());
        if (override != null) {
            costs.add(item(name, override, Source.MANUAL, request));
        } else {
            for (JourneyService.Section section : access.sections()) {
                Segment chosen = section.chosen();
                costs.add(item(name + " · " + section.cost().label(), chosen.fare(), chosen.source(), request));
            }
        }
        return access.minutes();
    }

    private Item item(String label, Fare fare, Source source, CompareRequest request) {
        return pricing.item(label, fare, source, request.party(), request.date());
    }
}
