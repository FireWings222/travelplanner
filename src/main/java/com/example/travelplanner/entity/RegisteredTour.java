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

    /** JPA가 DB에서 등록 투어를 복원할 때 쓰는 기본 생성자입니다. */
    protected RegisteredTour() {}

    /** TourService가 직접 등록한 상품의 ID, 목적지, JSON, 확인 시각을 저장할 객체를 만듭니다. */
    public RegisteredTour(UUID id, String destination, String payload, Instant checkedAt) {
        this.id = id;
        this.destination = destination;
        this.payload = payload;
        this.checkedAt = checkedAt;
    }

    /** 투어 상세 및 비용 비교 요청에 쓰는 직접 등록 상품 식별자를 반환합니다. */
    public UUID id() { return id; }
    /** TourService가 상품 상세 정보로 복원할 등록 투어 JSON을 반환합니다. */
    public String payload() { return payload; }
    /** 직접 등록 투어의 출처와 함께 표시할 등록 시각을 반환합니다. */
    public Instant checkedAt() { return checkedAt; }
}
