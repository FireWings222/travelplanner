package com.example.travelplanner.integration;

import com.example.travelplanner.dto.Travel.*;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public final class TravelProviders {
    private TravelProviders() {}

    public interface Catalog {
        List<Destination> destinations();
        List<Attraction> attractions();
        Place center(String destination);
    }

    public interface Routes {
        List<List<Segment>> options(String origin, String destination, LocalDate date);
        List<Segment> local(Place from, Place to, String country, LocalDate date);
    }

    public interface Tours {
        Platform platform();
        List<Tour> search(String destination);
    }

    public interface Exchange {
        Optional<Rate> rate(Currency currency, LocalDate travelDate);
    }

    public record Rate(BigDecimal krwPerUnit, LocalDate date, Source source) {}

    public static class Unavailable extends RuntimeException {
        public Unavailable(String message, Throwable cause) { super(message, cause); }
    }
}
