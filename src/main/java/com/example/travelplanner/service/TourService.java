package com.example.travelplanner.service;

import com.example.travelplanner.dto.Travel.*;
import com.example.travelplanner.entity.RegisteredTour;
import com.example.travelplanner.exception.NotFound;
import com.example.travelplanner.integration.TravelProviders;
import com.example.travelplanner.pricing.Pricing;
import com.example.travelplanner.repository.TourRepository;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
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

    public TourService(CatalogService catalog, TourRepository repository, List<TravelProviders.Tours> providers,
                       ObjectMapper json) {
        this.catalog = catalog;
        this.repository = repository;
        this.providers = providers;
        this.json = json;
    }

    public SearchResult search(String destination, LocalDate date, Platform platform) {
        String city = catalog.destination(destination).id();
        List<Tour> found = new ArrayList<>(repository.findByDestinationOrderByCheckedAtDesc(city)
                .stream().map(this::from).toList());
        List<String> notices = new ArrayList<>();
        for (TravelProviders.Tours provider : providers) {
            if (platform != null && provider.platform() != platform) continue;
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

    public static Fare fare(Tour tour, LocalDate date) {
        return tour.details().prices().stream()
                .filter(price -> !date.isBefore(price.from()) && !date.isAfter(price.to()))
                .map(Price::fare).findFirst().orElse(null);
    }

    private static void checkUrl(String url) {
        if (url == null || url.isBlank()) return;
        URI uri = URI.create(url);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("상품 링크와 이미지는 유효한 HTTPS URL을 사용해 주세요.");
        }
    }

    private Tour from(RegisteredTour entity) {
        return new Tour(entity.id().toString(), json.readValue(entity.payload(), TourInput.class),
                Source.MANUAL, entity.checkedAt());
    }
}
