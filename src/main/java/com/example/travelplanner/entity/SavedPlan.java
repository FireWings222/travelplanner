package com.example.travelplanner.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "travel_plan")
public class SavedPlan {
    @Id
    private UUID id;

    @Column(nullable = false, length = 40)
    private String destination;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected SavedPlan() {}

    public SavedPlan(UUID id) { this.id = id; }

    public void update(String destination, String payload) {
        this.destination = destination;
        this.payload = payload;
        this.updatedAt = Instant.now();
    }

    public UUID id() { return id; }
    public String payload() { return payload; }
    public Instant updatedAt() { return updatedAt; }
}
