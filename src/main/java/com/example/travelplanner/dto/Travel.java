package com.example.travelplanner.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

public final class Travel {
    private Travel() {}

    public enum Mode {
        CAR, EXPRESS_BUS, INTERCITY_BUS, KTX, SRT, TRAIN, SUBWAY, CITY_BUS,
        TAXI, WALK, FLIGHT, AIRPORT_BUS, AIRPORT_RAIL, RENTAL_CAR
    }

    public enum TripType { SOLO, COUPLE, FAMILY, FRIENDS, OTHER }
    public enum Platform { KLOOK, MYREALTRIP, NOL, OTHER }
    public enum Currency { KRW, JPY, USD, EUR }
    public enum Source { SAMPLE, MANUAL, PROVIDER }

    public record Destination(String id, String name, String country, String keywords) {}
    public record Attraction(String id, String destinationId, String name, String description,
                             int visitMinutes, Fare admission, double latitude, double longitude) {}
    public record Place(String id, String name, double latitude, double longitude) {}

    public record Party(@Min(1) @Max(30) int adults, @Min(0) @Max(30) int children) {
        public int size() { return adults + children; }
    }

    public record Trip(@NotBlank String origin, @NotBlank String destination,
                       @NotNull LocalDate start, @NotNull LocalDate end,
                       @Valid @NotNull Party party,
                       @PositiveOrZero @Digits(integer = 12, fraction = 0) BigDecimal budget,
                       Mode preferredMode, @NotNull TripType type) {}

    public record Fare(@NotNull Currency currency,
                       @PositiveOrZero @Digits(integer = 9, fraction = 2) BigDecimal adult,
                       @PositiveOrZero @Digits(integer = 9, fraction = 2) BigDecimal child,
                       @PositiveOrZero @Digits(integer = 9, fraction = 2) BigDecimal shared) {}

    public record Price(@NotNull LocalDate from, @NotNull LocalDate to, @Valid @NotNull Fare fare) {}
    public record Segment(Place from, Place to, Mode mode, BigDecimal kilometers, int minutes,
                          int transfers, Fare fare, Source source, Instant checkedAt) {}

    public record Leg(@NotNull Mode mode, @Valid Fare override) {}
    public record RouteRequest(@Valid @NotNull Trip trip,
                               @Size(max = 10) Map<String, @Valid @NotNull Leg> legs) {}

    public record Plan(@Valid @NotNull Trip trip, @NotNull @Size(min = 1, max = 30)
                       List<@NotNull @Valid Day> days) {}
    public record Day(@NotNull LocalDate date, @NotNull @Size(max = 15) List<@NotBlank String> stops,
                      @Size(max = 16) Map<String, @Valid @NotNull Leg> legs) {}

    public record TourInput(@NotBlank @Size(max = 40) String destination,
                            @NotNull Platform platform, @NotBlank @Size(max = 150) String name,
                            @Size(max = 1000) String imageUrl, @Size(max = 1000) String bookingUrl,
                            @NotNull LocalTime departure, @NotNull LocalTime arrival,
                            @NotBlank @Size(max = 150) String meetingPlace,
                            @NotBlank @Size(max = 150) String endPlace,
                            @Size(max = 1000) String cancellation,
                            @NotNull Mode mode,
                            boolean transportIncluded, boolean admissionIncluded,
                            boolean mealIncluded, boolean guideIncluded,
                            @NotEmpty @Size(max = 15) List<@NotBlank String> stops,
                            @NotEmpty @Size(max = 50) List<@NotNull @Valid Price> prices,
                            @Valid Fare requiredExtras,
                            @Valid Fare excludedTransport) {}

    public record Tour(String id, TourInput details, Source source, Instant checkedAt) {}

    public record CompareRequest(@NotBlank String tourId, @NotNull LocalDate date, String lodgingCity,
                                 @Valid @NotNull Party party,
                                 @Size(max = 15) List<@NotBlank String> stops,
                                 @Size(max = 16) Map<String, @Valid @NotNull Leg> legs,
                                 @Valid Fare tourOutbound, @Valid Fare tourReturn,
                                 @Valid Fare independentExtras) {}
}
