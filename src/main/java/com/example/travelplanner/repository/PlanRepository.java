package com.example.travelplanner.repository;

import com.example.travelplanner.entity.SavedPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PlanRepository extends JpaRepository<SavedPlan, UUID> {
    /** Spring Data JPA가 메서드 이름을 기준으로 전체 일정을 수정 시각 내림차순으로 조회합니다. */
    List<SavedPlan> findAllByOrderByUpdatedAtDesc();
}
