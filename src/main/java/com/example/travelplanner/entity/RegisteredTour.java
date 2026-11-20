package com.example.travelplanner.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tour_product")
public class RegisteredTour {
    @Id
    private UUID id;
    @Column(nullable = false, length = 40)
    private String destination;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;

    protected RegisteredTour() {}

    public RegisteredTour(UUID id, String destination, String payload, Instant checkedAt) {
        this.id = id;
        this.destination = destination;
        this.payload = payload;
        this.checkedAt = checkedAt;
    }

    public UUID id() { return id; }
    public String payload() { return payload; }
    public Instant checkedAt() { return checkedAt; }
}
