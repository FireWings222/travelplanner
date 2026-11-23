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

@Component
public class DomesticTours implements TravelProviders.Tours {
    private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 11, 23);
    private static final Instant CHECKED_AT = Instant.parse("2026-11-22T15:00:00Z");

    private final Map<String, List<Tour>> byDestination;
    private final List<Attraction> attractions;

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

    @Override public boolean supports(Platform requested) {
        return requested == Platform.OTHER || requested == Platform.KLOOK || requested == Platform.KKDAY;
    }

    @Override public List<Tour> search(String destination) {
        return byDestination.getOrDefault(destination, List.of());
    }

    public List<Attraction> attractions() { return attractions; }

    private static Attraction attraction(String line) {
        String[] fields = line.split("\t", -1);
        Fare admission = fields[5].equals("-") ? null : new Fare(new BigDecimal(fields[5]),
                fields[5].equals("0") ? BigDecimal.ZERO : null, null);
        return new Attraction(fields[0], fields[1], fields[2], "코스 참고 좌표 · 체류 약 45분", 45, admission,
                Double.parseDouble(fields[3]), Double.parseDouble(fields[4]));
    }

    private record Entry(String id, String destination, String name, List<String> stops, Mode mode,
                         boolean guideIncluded, Platform platform, Integer adultKrw, GroupEstimate estimate,
                         GroupDiscount discount, Boolean fromPrice, String sourceUrl, String priceUrl,
                         String notes, Integer durationMinutes, List<String> coveredAdmissionStops) {
        Tour tour() {
            BigDecimal adult = estimate == null ? BigDecimal.valueOf(adultKrw) : estimate.perPerson(GroupEstimate.DEFAULT_PEOPLE);
            Fare fare = new Fare(adult, adult.signum() == 0 || estimate != null ? adult : null, null);
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
