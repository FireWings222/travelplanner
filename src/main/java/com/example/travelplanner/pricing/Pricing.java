package com.example.travelplanner.pricing;

import com.example.travelplanner.dto.Travel.*;
import com.example.travelplanner.integration.TravelProviders;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;

@Service
public class Pricing {
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private final TravelProviders.Exchange exchange;

    public Pricing(TravelProviders.Exchange exchange) { this.exchange = exchange; }

    public record Item(String label, Fare original, Source source, TravelProviders.Rate rate,
                       BigDecimal adultKrw, BigDecimal childKrw, BigDecimal sharedKrw,
                       BigDecimal totalKrw, List<String> missing) {}

    public record Total(BigDecimal totalKrw, BigDecimal perPersonKrw, BigDecimal adultPerPersonKrw,
                        BigDecimal childPerPersonKrw, BigDecimal sharedKrw, List<String> missing) {
        public boolean isComplete() { return missing.isEmpty(); }
    }

    public static Fare free() { return new Fare(Currency.KRW, ZERO, ZERO, null); }

    public static void validate(Fare fare) {
        if (fare != null && fare.shared() != null && (fare.adult() != null || fare.child() != null)) {
            throw new IllegalArgumentException("공동 요금과 성인·아동 요금은 한 항목에 함께 입력할 수 없습니다.");
        }
    }

    public Item item(String label, Fare fare, Source source, Party party, LocalDate date) {
        validate(fare);
        List<String> missing = new ArrayList<>();
        if (fare == null) return unknown(label, null, source, "가격");
        TravelProviders.Rate rate = fare.currency() == Currency.KRW
                ? new TravelProviders.Rate(BigDecimal.ONE, date, source)
                : exchange.rate(fare.currency(), date).orElse(null);
        if (rate == null) return unknown(label, fare, source, "환율");
        BigDecimal adult = ZERO;
        BigDecimal child = ZERO;
        BigDecimal shared = ZERO;
        if (fare.shared() == null) {
            if (fare.adult() == null) missing.add(label + " 성인 요금");
            if (party.children() > 0 && fare.child() == null) missing.add(label + " 아동 요금");
            adult = Objects.requireNonNullElse(fare.adult(), ZERO).multiply(rate.krwPerUnit());
            child = Objects.requireNonNullElse(fare.child(), ZERO).multiply(rate.krwPerUnit());
        } else {
            shared = fare.shared().multiply(rate.krwPerUnit());
        }
        BigDecimal total = adult.multiply(BigDecimal.valueOf(party.adults()))
                .add(child.multiply(BigDecimal.valueOf(party.children()))).add(shared);
        return new Item(label, fare, source, rate, adult, child, shared, total, List.copyOf(missing));
    }

    private static Item unknown(String label, Fare fare, Source source, String reason) {
        return new Item(label, fare, source, null, ZERO, ZERO, ZERO, ZERO, List.of(label + " " + reason));
    }

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

    private static BigDecimal divide(BigDecimal amount, int size) {
        return amount.divide(BigDecimal.valueOf(size), 2, RoundingMode.HALF_UP);
    }
}
