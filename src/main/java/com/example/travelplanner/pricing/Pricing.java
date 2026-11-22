package com.example.travelplanner.pricing;

import com.example.travelplanner.dto.Travel.*;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;

@Service
public class Pricing {
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    public record Item(String label, Fare original, Source source,
                       BigDecimal adultKrw, BigDecimal childKrw, BigDecimal sharedKrw,
                       BigDecimal totalKrw, List<String> missing) {}

    public record Total(BigDecimal totalKrw, BigDecimal perPersonKrw, BigDecimal adultPerPersonKrw,
                        BigDecimal childPerPersonKrw, BigDecimal sharedKrw, List<String> missing) {
        /** 비용 비교와 추천에서 사용할 수 있도록 합계에 미확인 항목이 있는지 확인합니다. */
        public boolean isComplete() { return missing.isEmpty(); }
    }

    /** 무료 이동과 무료 항목 계산에 사용할 0원 요금을 만듭니다. */
    public static Fare free() { return new Fare(ZERO, ZERO, null); }

    /** 같은 항목에 인원별 요금과 일행 공동 요금이 함께 들어왔는지 검사합니다. */
    public static void validate(Fare fare) {
        if (fare != null && fare.shared() != null && (fare.adult() != null || fare.child() != null)) {
            throw new IllegalArgumentException("공동 요금과 성인·아동 요금은 한 항목에 함께 입력할 수 없습니다.");
        }
    }

    /**
     * 원화 운임·입장료·투어비 한 건을 인원수에 맞춰 계산하고 미확인 항목을 정리합니다.
     *
     * fare의 성인·아동 1인 요금에 각각 인원수를 곱합니다. 공동 요금이 있다면 일행에 한 번만 더합니다.
     *
     * 알 수 없는 금액은 부분 합계에 넣지 않고 missing 목록에 남깁니다. 성인·아동별 소계와 출처도 함께 반환해 화면에서 비용을 설명할 수 있게 합니다.
     */
    public Item item(String label, Fare fare, Source source, Party party) {
        validate(fare);
        List<String> missing = new ArrayList<>();
        if (fare == null) return unknown(label, null, source, "가격");
        BigDecimal adult = ZERO;
        BigDecimal child = ZERO;
        BigDecimal shared = ZERO;
        if (fare.shared() == null) {
            if (fare.adult() == null) missing.add(label + " 성인 요금");
            if (party.children() > 0 && fare.child() == null) missing.add(label + " 아동 요금");
            adult = Objects.requireNonNullElse(fare.adult(), ZERO);
            child = Objects.requireNonNullElse(fare.child(), ZERO);
        } else {
            shared = fare.shared();
        }
        BigDecimal total = adult.multiply(BigDecimal.valueOf(party.adults()))
                .add(child.multiply(BigDecimal.valueOf(party.children()))).add(shared);
        return new Item(label, fare, source, adult, child, shared, total, List.copyOf(missing));
    }

    /** 요금이 빠진 항목을 0원 부분 합계와 미확인 사유로 표시합니다. */
    private static Item unknown(String label, Fare fare, Source source, String reason) {
        return new Item(label, fare, source, ZERO, ZERO, ZERO, ZERO, List.of(label + " " + reason));
    }

    /**
     * 비용 항목을 합산하고 공동 비용을 인원수로 나누어 개인별 표시 금액을 계산합니다.
     *
     * 경로·일정·투어에서 만들어진 비용 항목을 합합니다. 일행 공동 비용은 성인·아동 구분 없이 인원수로 나눈 뒤 1인당 예상 비용에 더합니다.
     *
     * missing에는 각 항목의 미확인 사유를 중복 없이 모읍니다. 호출자는 이 목록으로 합계가 완전한지 판단합니다.
     */
    public Total total(List<Item> items, Party party) {
        BigDecimal total = items.stream().map(Item::totalKrw).reduce(ZERO, BigDecimal::add);
        BigDecimal adults = items.stream().map(Item::adultKrw).reduce(ZERO, BigDecimal::add);
        BigDecimal children = items.stream().map(Item::childKrw).reduce(ZERO, BigDecimal::add);
        BigDecimal shared = items.stream().map(Item::sharedKrw).reduce(ZERO, BigDecimal::add);
        BigDecimal share = divide(shared, party.size());
        return new Total(total, divide(total, party.size()), adults.add(share).setScale(2, RoundingMode.HALF_UP),
                party.children() == 0 ? null : children.add(share).setScale(2, RoundingMode.HALF_UP), shared,
                items.stream().flatMap(item -> item.missing().stream()).distinct().toList());
    }

    /** 일행 공동 비용과 평균 비용 표시를 위해 값을 인원수로 나누어 소수 둘째 자리로 반올림합니다. */
    private static BigDecimal divide(BigDecimal amount, int size) {
        return amount.divide(BigDecimal.valueOf(size), 2, RoundingMode.HALF_UP);
    }
}
