package com.unihub.workshop.workshops.domain.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.unihub.workshop.workshops.domain.model.AiSummary;

public interface AiSummaryRepository extends JpaRepository<AiSummary, UUID> {

    Optional<AiSummary> findByWorkshopId(UUID workshopId);
}
