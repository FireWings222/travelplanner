package com.example.travelplanner.repository;

import com.example.travelplanner.entity.SavedPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PlanRepository extends JpaRepository<SavedPlan, UUID> {
    /** PlanService의 저장 목록에 사용할 일정 레코드를 최근 수정 순으로 조회합니다. */
    List<SavedPlan> findAllByOrderByUpdatedAtDesc();
}
