package com.example.travelplanner.repository;

import com.example.travelplanner.entity.RegisteredTour;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface TourRepository extends JpaRepository<RegisteredTour, UUID> {
    List<RegisteredTour> findByDestinationOrderByCheckedAtDesc(String destination);
}
