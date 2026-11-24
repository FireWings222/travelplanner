package com.example.travelplanner.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Travel {
    /** 요청·응답 자료형을 모아 둔 클래스의 직접 생성을 막습니다. */
    private Travel() {}

    public enum Mode {
        CAR, EXPRESS_BUS, INTERCITY_BUS, KTX, SRT, TRAIN, SUBWAY, CITY_BUS,
        TAXI, WALK, FLIGHT, AIRPORT_BUS, AIRPORT_RAIL, RENTAL_CAR, BUS_SUBWAY
    }

    public enum TripType { SOLO, COUPLE, FAMILY, FRIENDS, OTHER }
    public enum Platform { KLOOK, MYREALTRIP, NOL, KKDAY, OTHER }
    public enum Source { UNCONFIRMED, CATALOG, MANUAL, PROVIDER, ODSAY, KAKAO_MOBILITY, KAKAO_MAP, KAKAO_ROUTING, PUBLISHED, ESTIMATED }

    public record Destination(String id, String name, String keywords) {}
    public record Attraction(String id, String destinationId, String name, String description,
                             Integer visitMinutes, Fare admission, double latitude, double longitude) {}
    public record Place(String id, String name, double latitude, double longitude) {}

    public record Party(@Min(1) @Max(30) int adults, @Min(0) @Max(30) int children) {
        /** Pricing이 공동 비용을 나눌 때 사용하는 전체 여행 인원 수를 반환합니다. */
        public int size() { return adults + children; }
    }

    public record Trip(@NotBlank String origin, @NotBlank String destination,
                       @NotNull LocalDate start, @NotNull LocalDate end,
                       @Valid @NotNull Party party,
                       @PositiveOrZero @Digits(integer = 12, fraction = 0) BigDecimal budget,
                       Mode preferredMode, @NotNull TripType type,
                       @Valid SearchedPlace originPlace, @Valid SearchedPlace destinationPlace) {
        public Trip(String origin, String destination, LocalDate start, LocalDate end, Party party,
                    BigDecimal budget, Mode preferredMode, TripType type) {
            this(origin, destination, start, end, party, budget, preferredMode, type, null, null);
        }
    }

    public record Fare(@PositiveOrZero @Digits(integer = 9, fraction = 2) BigDecimal adult,
                       @PositiveOrZero @Digits(integer = 9, fraction = 2) BigDecimal child,
                       @PositiveOrZero @Digits(integer = 9, fraction = 2) BigDecimal shared) {
        /** 이전 JSON의 원화 요금을 읽고, 외화 금액은 미확인으로 처리합니다. */
        @JsonCreator
        public static Fare fromJson(@JsonProperty("adult") BigDecimal adult,
                                    @JsonProperty("child") BigDecimal child,
                                    @JsonProperty("shared") BigDecimal shared,
                                    @JsonProperty("currency") String legacyCurrency) {
            if (legacyCurrency != null && !"KRW".equals(legacyCurrency)) return new Fare(null, null, null);
            return new Fare(adult, child, shared);
        }
    }

    public record Price(@NotNull LocalDate from, @NotNull LocalDate to, @Valid @NotNull Fare fare) {}
    /** path는 실제 경로 좌표입니다. 시외버스는 별도의 승차·하차 터미널 좌표를 제공합니다. */
    public record Boarding(Mode mode, List<String> lines, String start, String end, Integer minutes,
                           Integer meters, List<RoutePoint> path, RoutePoint walkFrom, RoutePoint walkTo,
                           RoutePoint terminalFrom, RoutePoint terminalTo) {
        public Boarding(Mode mode, List<String> lines, String start, String end, Integer minutes,
                        Integer meters, List<RoutePoint> path, RoutePoint walkFrom, RoutePoint walkTo) {
            this(mode, lines, start, end, minutes, meters, path, walkFrom, walkTo, null, null);
        }

        public Boarding(Mode mode, List<String> lines, String start, String end, Integer minutes,
                        Integer meters, List<RoutePoint> path) {
            this(mode, lines, start, end, minutes, meters, path, null, null);
        }

        public Boarding(Mode mode, List<String> lines, String start, String end, Integer minutes) {
            this(mode, lines, start, end, minutes, null, List.of());
        }
    }

    /** API 공급자에서 받은 경로 좌표를 지도에서 쓰는 위도, 경도 순서로 표현합니다. */
    public record RoutePoint(double latitude, double longitude) {
        public boolean valid() {
            return Double.isFinite(latitude) && Double.isFinite(longitude)
                    && Math.abs(latitude) <= 90 && Math.abs(longitude) <= 180;
        }
    }

    /** geometry는 독립된 선들의 목록입니다. ODsay는 mapObject를 이용해 별도로 좌표를 읽습니다. */
    public record Segment(Place from, Place to, Mode mode, BigDecimal kilometers, Integer minutes,
                          Integer transfers, Fare fare, Source source, Instant checkedAt, String mapObject,
                          List<Boarding> boardings, List<List<RoutePoint>> geometry) {
        public Segment(Place from, Place to, Mode mode, BigDecimal kilometers, Integer minutes,
                       Integer transfers, Fare fare, Source source, Instant checkedAt, String mapObject,
                       List<Boarding> boardings) {
            this(from, to, mode, kilometers, minutes, transfers, fare, source, checkedAt, mapObject, boardings, List.of());
        }
        /** 상세 노선이 없는 구간을 구성합니다. 확인하지 못한 거리, 시간, 환승은 null로 둡니다. */
        public Segment(Place from, Place to, Mode mode, BigDecimal kilometers, Integer minutes,
                       Integer transfers, Fare fare, Source source, Instant checkedAt) {
            this(from, to, mode, kilometers, minutes, transfers, fare, source, checkedAt, null, List.of(), List.of());
        }
    }

    public record Leg(@NotNull Mode mode, @Valid Fare override) {}
    public record RouteRequest(@Valid @NotNull Trip trip,
                               @Size(max = 10) Map<String, @Valid @NotNull Leg> legs) {}

    public record Plan(@Valid @NotNull Trip trip, @NotNull @Size(min = 1, max = 30)
                       List<@NotNull @Valid Day> days) {}
    /** 카카오맵에서 선택한 장소입니다. 일정 JSON에 함께 저장해 다시 열 때 재검색할 필요가 없습니다. */
    public record SearchedPlace(@NotBlank @Pattern(regexp = SearchedPlace.ID_PATTERN) String id,
                                @NotBlank @Size(max = 200) String name,
                                @NotBlank @Size(max = 200) String address,
                                double latitude, double longitude) {
        public static final String ID_PATTERN = "kakao-(?:[0-9]{1,24}|address-[0-9a-f]{32})";
    }

    public record Day(@NotNull LocalDate date, @NotNull @Size(max = 15) List<@NotBlank String> stops,
                      @Size(max = 16) Map<String, @Valid @NotNull Leg> legs,
                      @Size(max = 15) List<@Valid @NotNull SearchedPlace> places,
                      List<@Valid @NotNull Opinion> opinions) {
        public Day {
            places = places == null ? List.of() : List.copyOf(places);
            opinions = opinions == null ? List.of() : List.copyOf(opinions);
        }

        public Day(LocalDate date, List<String> stops, Map<String, Leg> legs, List<SearchedPlace> places) {
            this(date, stops, legs, places, List.of());
        }

        public Day(LocalDate date, List<String> stops, Map<String, Leg> legs) {
            this(date, stops, legs, List.of());
        }
    }

    /** 같은 날짜 안의 대안 코스입니다. 새 의견의 ID는 최초 견적에서 발급합니다. */
    public record Opinion(UUID id, @NotBlank @Size(max = 40) String name,
                          @NotNull @Size(max = 15) List<@NotBlank String> stops,
                          @Size(max = 16) Map<String, @Valid @NotNull Leg> legs,
                          @Size(max = 15) List<@Valid @NotNull SearchedPlace> places) {
        public Opinion {
            places = places == null ? List.of() : List.copyOf(places);
        }
    }

    public record OpinionRequest(@Valid @NotNull Trip trip, @NotNull LocalDate date,
                                 @Valid @NotNull Opinion opinion) {}

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

    public record GroupDiscount(int minimumPeople, int adultKrw) {}

    /** 카탈로그 투어의 출처와 요금 조건입니다. 포함 입장료는 방문지 ID로 기록해 비용 비교에서 중복 합산을 막습니다. */
    public record TourReference(String sourceUrl, String priceUrl, String notes, boolean fromPrice,
                                Integer durationMinutes, GroupDiscount discount,
                                List<String> coveredAdmissionStops) {}

    public record Tour(String id, TourInput details, Source source, Instant checkedAt, TourReference reference) {
        public Tour(String id, TourInput details, Source source, Instant checkedAt) {
            this(id, details, source, checkedAt, null);
        }
    }

    /** 현지 일정에서 선택한 날짜, 일행, 방문지로 일일투어를 찾습니다. */
    public record TourRecommendationRequest(@NotBlank String destination, @NotNull LocalDate date,
                                            @Valid @NotNull Party party,
                                            @NotEmpty @Size(max = 15) List<@NotBlank String> stops) {}

    /** 투어 방문 순서와 선택 날짜 및 인원에 적용되는 기본 요금을 담습니다. */
    public record TourRecommendation(Tour tour, List<Attraction> stops, Fare fare) {}

    @JsonIgnoreProperties("lodgingCity")
    public record CompareRequest(@NotBlank String tourId, @NotNull LocalDate date,
                                 @Valid @NotNull Party party,
                                 @Size(max = 15) List<@NotBlank String> stops,
                                 @Size(max = 16) Map<String, @Valid @NotNull Leg> legs,
                                 @Valid Fare tourOutbound, @Valid Fare tourReturn,
                                 @Valid Fare independentExtras,
                                 @Size(max = 15) List<@Valid @NotNull SearchedPlace> places) {
        public CompareRequest(String tourId, LocalDate date, Party party, List<String> stops, Map<String, Leg> legs,
                              Fare tourOutbound, Fare tourReturn, Fare independentExtras) {
            this(tourId, date, party, stops, legs, tourOutbound, tourReturn, independentExtras, List.of());
        }
    }
}
