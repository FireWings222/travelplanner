package com.example.travelplanner.integration;

import com.example.travelplanner.dto.Travel.*;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

@Component
public class SampleExchange implements TravelProviders.Exchange {
    private static final Map<Currency, BigDecimal> RATES = Map.of(
            Currency.KRW, BigDecimal.ONE, Currency.JPY, new BigDecimal("9.20"),
            Currency.USD, new BigDecimal("1400"), Currency.EUR, new BigDecimal("1510"));

    @Override
    public Optional<TravelProviders.Rate> rate(Currency currency, LocalDate date) {
        return Optional.ofNullable(RATES.get(currency))
                .map(rate -> new TravelProviders.Rate(rate, LocalDate.of(2026, 11, 20), Source.SAMPLE));
    }
}
