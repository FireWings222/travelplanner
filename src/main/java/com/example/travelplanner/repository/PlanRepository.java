package com.example.travelplanner.repository;

import com.example.travelplanner.entity.SavedPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PlanRepository extends JpaRepository<SavedPlan, UUID> {
    List<SavedPlan> findAllByOrderByUpdatedAtDesc();
}
