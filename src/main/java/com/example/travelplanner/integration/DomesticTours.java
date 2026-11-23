package com.example.travelplanner.integration;

import com.example.travelplanner.dto.Travel.*;
import com.example.travelplanner.pricing.Pricing;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 프로젝트에 포함된 투어와 방문지 파일을 시작할 때 읽어 검색에 제공하는 카탈로그입니다. */
@Component
public class DomesticTours implements TravelProviders.Tours {
    // 공개 요금을 조사한 기준일입니다. 기준일의 한국 표준시 자정을 UTC 시각으로 기록합니다.
    private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 11, 23);
    private static final Instant CHECKED_AT = Instant.parse("2026-11-22T15:00:00Z");

    private final Map<String, List<Tour>> byDestination;
    private final List<Attraction> attractions;

    /** 투어는 지역별로 묶고, 방문지 TSV는 헤더를 건너뛰어 카탈로그에서 함께 조회할 수 있게 읽습니다. */
    public DomesticTours(ObjectMapper json) throws IOException {
        try (var input = new ClassPathResource("catalog/domestic-tours.json").getInputStream()) {
            List<Entry> entries = json.readValue(input, new TypeReference<List<Entry>>() {});
            byDestination = entries.stream().map(Entry::tour)
                    .collect(Collectors.groupingBy(tour -> tour.details().destination(), Collectors.toUnmodifiableList()));
        }
        try (var input = new ClassPathResource("catalog/domestic-attractions.tsv").getInputStream();
             var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            attractions = reader.lines().skip(1).map(DomesticTours::attraction).toList();
        }
    }

    @Override public Platform platform() { return Platform.OTHER; }

    /** 한 제공자가 여러 플랫폼의 코스를 담습니다. 개별 상품의 플랫폼은 TourService가 확인합니다. */
    @Override public boolean supports(Platform requested) {
        return requested == Platform.OTHER || requested == Platform.KLOOK || requested == Platform.KKDAY;
    }

    @Override public List<Tour> search(String destination) {
        return byDestination.getOrDefault(destination, List.of());
    }

    public List<Attraction> attractions() { return attractions; }

    /** TSV의 입장료 열에서 '-'는 미확인, '0'은 무료입니다. 양수는 성인 기준이며 아동 요금은 미확인으로 둡니다. */
    private static Attraction attraction(String line) {
        String[] fields = line.split("\t", -1);
        Fare admission = fields[5].equals("-") ? null : new Fare(new BigDecimal(fields[5]),
                fields[5].equals("0") ? BigDecimal.ZERO : null, null);
        return new Attraction(fields[0], fields[1], fields[2], "코스 참고 위치", null, admission,
                Double.parseDouble(fields[3]), Double.parseDouble(fields[4]));
    }

    private record Entry(String id, String destination, String name, List<String> stops, Mode mode,
                         boolean guideIncluded, Platform platform, Integer adultKrw, GroupEstimate estimate,
                         GroupDiscount discount, Boolean fromPrice, String sourceUrl, String priceUrl,
                         String notes, Integer durationMinutes, List<String> coveredAdmissionStops) {
        /** 검색 카드의 기본 요금과 비용 비교에 필요한 출처, 인원별 계산 정보를 함께 구성합니다. */
        Tour tour() {
            BigDecimal adult = estimate == null ? BigDecimal.valueOf(adultKrw) : estimate.perPerson(GroupEstimate.DEFAULT_PEOPLE);
            Fare fare = new Fare(adult, adult.signum() == 0 || estimate != null ? adult : null, null);
            // Price에는 기준일과 표시 금액을 담습니다. 카탈로그에는 해당 날짜의 예약 가능 여부와 집합 시각 정보가 없습니다.
            TourInput details = new TourInput(destination, platform == null ? Platform.OTHER : platform, name,
                    null, null, null, null, null, null, null, mode, true, false, false,
                    guideIncluded, stops, List.of(new Price(REFERENCE_DATE, REFERENCE_DATE, fare)), Pricing.free(), null);
            return new Tour(id, details, estimate == null ? Source.PUBLISHED : Source.ESTIMATED,
                    estimate == null ? CHECKED_AT : null,
                    new TourReference(sourceUrl, priceUrl, notes, Boolean.TRUE.equals(fromPrice), durationMinutes, estimate, discount,
                            coveredAdmissionStops == null ? List.of() : List.copyOf(coveredAdmissionStops)));
        }
    }
}
