package com.example.travelplanner.service;

import com.example.travelplanner.dto.Travel.*;
import com.example.travelplanner.entity.RegisteredTour;
import com.example.travelplanner.exception.NotFound;
import com.example.travelplanner.integration.TravelProviders;
import com.example.travelplanner.pricing.Pricing;
import com.example.travelplanner.repository.TourRepository;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
public class TourService {
    private final CatalogService catalog;
    private final TourRepository repository;
    private final List<TravelProviders.Tours> providers;
    private final ObjectMapper json;

    public record SearchResult(List<Tour> tours, List<String> notices) {}

    /** 카탈로그, 직접 등록 저장소, 투어 제공자와 JSON 변환기를 연결합니다. */
    public TourService(CatalogService catalog, TourRepository repository, List<TravelProviders.Tours> providers,
                       ObjectMapper json) {
        this.catalog = catalog;
        this.repository = repository;
        this.providers = providers;
        this.json = json;
    }

    /**
     * 제공자 상품과 직접 등록 상품을 모아 날짜·플랫폼 조건으로 필터링하고 조회 실패 안내를 덧붙입니다.
     *
     * 일일투어 화면이 보낸 도시를 표준 ID로 바꿔 DB의 직접 등록 상품과 제공자 상품을 모읍니다.
     *
     * 플랫폼 조건은 상품별로 적용합니다. 직접 등록 상품은 날짜별 요금표로 거르고, 카탈로그는 조사 기준가를 계속 표시합니다.
     * 제공자 조회가 실패하면 notices에 기록하고 다른 상품은 그대로 반환합니다.
     */
    public SearchResult search(String destination, LocalDate date, Platform platform) {
        String city = catalog.destination(destination).id();
        List<Tour> found = new ArrayList<>(repository.findByDestinationOrderByCheckedAtDesc(city)
                .stream().map(this::from).toList());
        List<String> notices = new ArrayList<>();
        for (TravelProviders.Tours provider : providers) {
            if (platform != null && !provider.supports(platform)) continue;
            try {
                found.addAll(provider.search(city));
            } catch (TravelProviders.Unavailable error) {
                notices.add(provider.platform() + " 상품 데이터를 일시적으로 불러올 수 없습니다. 저장된 상품은 이용할 수 있습니다.");
            }
        }
        return new SearchResult(found.stream()
                .filter(tour -> platform == null || tour.details().platform() == platform)
                .filter(tour -> date == null || fare(tour, date) != null).toList(), List.copyOf(notices));
    }

    /** 방문지가 겹치는 투어를 많이 겹치는 순으로 반환합니다. 날짜 요금과 인원별 추정에는 기존 비교 계산을 사용합니다. */
    public List<TourRecommendation> recommend(TourRecommendationRequest request) {
        Set<String> planned = new HashSet<>(request.stops());
        return search(request.destination(), request.date(), null).tours().stream()
                .filter(tour -> tour.details().stops().stream().anyMatch(planned::contains))
                .sorted(Comparator.<Tour>comparingLong(tour ->
                        tour.details().stops().stream().distinct().filter(planned::contains).count()).reversed())
                .map(tour -> new TourRecommendation(tour, catalog.stops(tour.details().destination(), tour.details().stops()),
                        fare(tour, request.date(), request.party())))
                .toList();
    }

    /**
     * 비용 비교나 상세 조회에 사용할 투어 ID를 직접 등록 상품과 제공자 상품에서 찾습니다.
     *
     * UUID 형식의 ID는 등록 상품 저장소에서 찾고, 카탈로그 ID는 도시별 검색 결과에서 찾습니다.
     *
     * 비용 비교는 이 메서드의 상품으로 방문지와 해당 날짜의 가격을 판단합니다.
     */
    public Tour get(String id) {
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException exception) {
            for (Destination city : catalog.search("")) {
                Optional<Tour> match = search(city.id(), null, null).tours().stream()
                        .filter(tour -> tour.id().equals(id)).findFirst();
                if (match.isPresent()) return match.get();
            }
            throw new NotFound("일일투어를 찾을 수 없습니다.");
        }
        return repository.findById(uuid).map(this::from).orElseThrow(() -> new NotFound("일일투어를 찾을 수 없습니다."));
    }

    /**
     * 등록 폼의 링크, 방문지, 가격 정보를 확인한 뒤 사용자 투어를 DB에 저장합니다.
     *
     * 직접 등록 폼에서 보낸 방문지·예약 링크·가격을 검증합니다. 날짜 범위를 시작일 순으로 정렬한 뒤 종료일이 앞서거나 기간이 겹치면 저장을 거절합니다.
     *
     * 가격 범위마다 성인 1인 요금이 필요합니다. 정리된 입력은 DB에 JSON으로 저장하고 상품 ID와 등록 시각을 반환합니다.
     */
    public Tour register(TourInput input) {
        String city = catalog.destination(input.destination()).id();
        catalog.stops(city, input.stops());
        if (input.arrival().equals(input.departure())) throw new IllegalArgumentException("출발 시간과 종료 시간이 같습니다.");
        checkUrl(input.bookingUrl());
        checkUrl(input.imageUrl());
        if (input.bookingUrl() == null || input.bookingUrl().isBlank()) {
            throw new IllegalArgumentException("예약 URL을 입력해 주세요.");
        }
        List<Price> prices = input.prices().stream().sorted(Comparator.comparing(Price::from)).toList();
        LocalDate previous = null;
        for (Price price : prices) {
            if (price.to().isBefore(price.from()) || (previous != null && !price.from().isAfter(previous))) {
                throw new IllegalArgumentException("가격 적용 기간이 올바르지 않거나 겹칩니다.");
            }
            Pricing.validate(price.fare());
            if (price.fare().adult() == null || price.fare().shared() != null) {
                throw new IllegalArgumentException("투어 상품에는 성인 1인 요금을 입력해야 합니다.");
            }
            previous = price.to();
        }
        Pricing.validate(input.requiredExtras());
        Pricing.validate(input.excludedTransport());
        TourInput normalized = new TourInput(city, input.platform(), input.name().strip(),
                input.imageUrl(), input.bookingUrl(), input.departure(), input.arrival(), input.meetingPlace(),
                input.endPlace(), input.cancellation(), input.mode(), input.transportIncluded(), input.admissionIncluded(),
                input.mealIncluded(), input.guideIncluded(), input.stops(), prices, input.requiredExtras(), input.excludedTransport());
        return from(repository.save(new RegisteredTour(UUID.randomUUID(), city, json.writeValueAsString(normalized), Instant.now())));
    }

    /**
     * 직접 등록 상품은 요청 날짜의 요금을 반환합니다. 가격 범위에는 시작일과 종료일이 모두 포함됩니다.
     *
     * 해당 기간이 없으면 null을 반환합니다. 카탈로그는 운영 여부와 별개로 공개 금액 또는 1인 예상가를 반환합니다.
     */
    public static Fare fare(Tour tour, LocalDate date) {
        if (tour.reference() != null) return tour.details().prices().get(0).fare();
        return tour.details().prices().stream()
                .filter(price -> !date.isBefore(price.from()) && !date.isAfter(price.to()))
                .map(Price::fare).findFirst().orElse(null);
    }

    /** 공개 단체 할인 조건을 충족하면 성인 1인 요금을 바꿉니다. 그 외 상품에는 기본 1인 요금을 사용합니다. */
    public static Fare fare(Tour tour, LocalDate date, Party party) {
        Fare base = fare(tour, date);
        TourReference reference = tour.reference();
        if (reference == null) return base;
        GroupDiscount discount = reference.discount();
        if (discount != null && party.size() >= discount.minimumPeople()) {
            return new Fare(BigDecimal.valueOf(discount.adultKrw()), base.child(), null);
        }
        return base;
    }

    /** 투어 등록 과정에서 예약·이미지 링크가 유효한 HTTPS 주소인지 확인합니다. */
    private static void checkUrl(String url) {
        if (url == null || url.isBlank()) return;
        URI uri = URI.create(url);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("상품 링크와 이미지는 유효한 HTTPS URL을 사용해 주세요.");
        }
    }

    /** DB에 보관한 직접 등록 투어 JSON을 검색·상세 화면에 전달할 상품 객체로 복원합니다. */
    private Tour from(RegisteredTour entity) {
        return new Tour(entity.id().toString(), json.readValue(entity.payload(), TourInput.class),
                Source.MANUAL, entity.checkedAt());
    }
}
