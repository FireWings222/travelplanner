package com.example.travelplanner.repository;

import com.example.travelplanner.entity.RegisteredTour;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface TourRepository extends JpaRepository<RegisteredTour, UUID> {
    /** TourService가 도시별로 직접 등록한 상품을 최근 확인 순으로 조회합니다. */
    List<RegisteredTour> findByDestinationOrderByCheckedAtDesc(String destination);
}
