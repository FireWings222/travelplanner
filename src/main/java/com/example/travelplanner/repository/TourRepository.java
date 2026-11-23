package com.example.travelplanner.repository;

import com.example.travelplanner.entity.RegisteredTour;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface TourRepository extends JpaRepository<RegisteredTour, UUID> {
    /** Spring Data JPA가 목적지 ID로 직접 등록 상품을 찾고 확인 시각 내림차순으로 조회합니다. */
    List<RegisteredTour> findByDestinationOrderByCheckedAtDesc(String destination);
}
