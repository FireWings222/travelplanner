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

    /** JPA가 DB에서 일정 레코드를 복원할 때 쓰는 기본 생성자입니다. */
    protected SavedPlan() {}

    /** PlanService가 처음 저장하는 일정에 새 식별자를 부여합니다. */
    public SavedPlan(UUID id) { this.id = id; }

    /** PlanService의 저장·수정 과정에서 목적지, 일정 JSON, 수정 시각을 갱신합니다. */
    public void update(String destination, String payload) {
        this.destination = destination;
        this.payload = payload;
        this.updatedAt = Instant.now();
    }

    /** 저장 일정 응답과 이후 조회·수정 요청에 쓰는 일정 식별자를 반환합니다. */
    public UUID id() { return id; }
    /** PlanService가 원래 계획으로 복원할 일정 JSON을 반환합니다. */
    public String payload() { return payload; }
    /** 저장 목록에 표시하는 마지막 수정 시각을 반환합니다. */
    public Instant updatedAt() { return updatedAt; }
}
