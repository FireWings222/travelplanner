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

    public record Side(List<Item> items, Total cost, Integer travelMinutes, Integer scheduledMinutes, String convenience) {}
    public record Result(Tour tour, List<Attraction> tourStops, List<Attraction> independentStops, Side guided,
                         Side independent, Journey journey, BigDecimal tourMinusIndependentKrw,
                         List<String> notices, String recommendation) {}

    /** 투어, 카탈로그, 이동, 입장료와 요금 계산을 비용 비교 기능에 연결합니다. */
    public ComparisonService(TourService tours, CatalogService catalog, JourneyService journeys, Pricing pricing,
                             PlanService plans) {
        this.tours = tours;
        this.catalog = catalog;
        this.journeys = journeys;
        this.pricing = pricing;
        this.plans = plans;
    }

    /**
     * 투어와 자유여행 양쪽의 비용·시간을 계산하고 방문지나 미확인 요금 차이를 응답에 포함합니다.
     *
     * 비용 비교 API에서 투어 ID, 날짜, 여행 인원과 자유여행 경로 변경값을 전달합니다. 투어가 속한 도시 중심을 숙소 기준으로 사용합니다.
     *
     * 자유여행은 구간 이동비와 입장료를 합하고, 투어는 날짜별 상품 가격에 집합·종료 이동비와 불포함 비용을 반영합니다.
     *
     * 필수 금액이 빠지면 차액을 확정하지 않습니다. 방문지가 서로 다르면 어느 쪽에만 있는지 안내하고 비용 추천을 보류합니다.
     */
    public Result compare(CompareRequest request) {
        Tour tour = tours.get(request.tourId());
        TourInput detail = tour.details();
        TourReference reference = tour.reference();
        String base = detail.destination();
        List<Attraction> tourStops = catalog.stops(base, detail.stops());
        List<Attraction> independentStops = request.stops() == null
                ? tourStops : catalog.stops(base, request.stops(), request.places());
        if (independentStops.isEmpty()) throw new IllegalArgumentException("자유여행 관광지를 한 곳 이상 선택해 주세요.");
        Journey local = request.stops() == null && reference == null
                ? journeys.loop(base, independentStops, request.legs(), null, request.party())
                : journeys.visits(independentStops, journeys.visitLegs(base, independentStops, request.legs()),
                    null, request.party());
        List<Item> own = new ArrayList<>(local.sections().stream().map(JourneyService.Section::cost).toList());
        own.addAll(plans.admissions(independentStops, request.party()));
        if (request.independentExtras() != null) {
            own.add(item("자유여행 필수 추가비", request.independentExtras(), Source.MANUAL, request));
        }

        List<Item> guided = new ArrayList<>();
        String priceLabel = reference == null ? "투어 상품 가격"
                : reference.estimate() == null ? "공개 투어 요금" : "인원별 투어 예상 요금";
        guided.add(item(priceLabel, TourService.fare(tour, request.date(), request.party()), tour.source(), request));
        String centerName = catalog.center(base).name();
        Integer outboundMinutes = access(guided, "숙소 → 집합 장소", request.tourOutbound(), detail.meetingPlace(),
                centerName, request);
        Integer returnMinutes = access(guided, "종료 장소 → 숙소", request.tourReturn(), detail.endPlace(),
                centerName, request);
        if (!detail.admissionIncluded()) {
            List<Attraction> extraAdmission = reference == null ? tourStops : tourStops.stream()
                    .filter(stop -> !reference.coveredAdmissionStops().contains(stop.id())).toList();
            guided.addAll(plans.admissions(extraAdmission, request.party()));
        }
        if (!detail.transportIncluded()) {
            guided.add(item("투어 불포함 관광지 이동비", detail.excludedTransport(), tour.source(), request));
        }
        guided.add(item("투어 기타 필수비", detail.requiredExtras(), tour.source(), request));

        Total tourCost = pricing.total(guided, request.party());
        Total soloCost = pricing.total(own, request.party());
        Integer duration = reference == null
                ? Integer.valueOf(Math.floorMod((int) Duration.between(detail.departure(), detail.arrival()).toMinutes(), 24 * 60))
                : reference.durationMinutes();
        Integer accessMinutes = outboundMinutes != null && returnMinutes != null ? outboundMinutes + returnMinutes : null;
        Integer tourTravel = tour.id().equals("jeju-day") && accessMinutes != null ? 120 + accessMinutes : null;
        Integer scheduledMinutes = duration == null ? null : duration + (accessMinutes == null ? 0 : accessMinutes);
        String itinerary = reference == null ? "정해진 집합 시간과 방문 순서" : "카탈로그에 등록된 방문 순서";
        Side tourSide = new Side(List.copyOf(guided), tourCost, tourTravel, scheduledMinutes,
                itinerary + (detail.guideIncluded() ? " · 가이드 포함" : ""));
        Side soloSide = new Side(List.copyOf(own), soloCost, local.minutes(),
                local.minutes() + independentStops.stream().mapToInt(Attraction::visitMinutes).sum(),
                "방문 순서와 체류 시간을 직접 조정");
        List<String> notices = new ArrayList<>();
        if (reference != null) {
            notices.add("카탈로그 방문지는 참고 좌표입니다. 실제 승하차 위치와 집합·종료 정보를 확인하세요.");
            notices.add("자유여행 교통비는 첫 방문지부터 마지막 방문지까지의 이동을 기준으로 합니다.");
            if (reference.estimate() == null) {
                notices.add("공개 요금은 확인일 기준입니다. 선택한 날짜의 운행과 최종 요금은 운영사에서 확인하세요.");
            } else if (reference.estimate().supports(request.party().size())) {
                notices.add("6~20인 단체 예상가입니다. 성인과 아동에게 같은 1인 예상가를 적용했습니다.");
            } else {
                notices.add("이 투어의 예상가를 계산하려면 성인과 아동을 합해 6~20명으로 입력하세요.");
            }
            if (reference.notes() != null) notices.add(reference.notes());
        }
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

    /**
     * 비용 비교에서 숙소와 투어 집합·종료 장소 사이의 이동비를 직접 입력값이나 경로 견적으로 계산합니다.
     *
     * 집합·종료 장소의 이름이 도시 중심의 이름과 같으면 기본 비용과 이동 시간을 0으로 봅니다. 직접 입력한 금액이 있으면 그 금액을 우선합니다.
     *
     * 그 밖의 장소는 직접 요금을 비용 항목에 추가하고 이동 시간은 확인할 수 없음을 null로 반환합니다.
     */
    private Integer access(List<Item> costs, String name, Fare override, String place, String center, CompareRequest request) {
        if (!center.equals(place)) {
            costs.add(item(name, override, Source.MANUAL, request));
            return null;
        }
        costs.add(item(name, override == null ? Pricing.free() : override,
                override == null ? Source.SAMPLE : Source.MANUAL, request));
        return 0;
    }

    /** 비교 요청의 인원수를 사용해 한 비용 항목을 Pricing에 전달합니다. */
    private Item item(String label, Fare fare, Source source, CompareRequest request) {
        return pricing.item(label, fare, source, request.party());
    }
}
