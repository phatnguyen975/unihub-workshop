package com.unihub.workshop.workshops.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.unihub.workshop.workshops.domain.model.WorkshopSpeaker;

public interface WorkshopSpeakerRepository extends JpaRepository<WorkshopSpeaker, UUID> {

    List<WorkshopSpeaker> findByWorkshopIdOrderBySortOrder(UUID workshopId);

    Optional<WorkshopSpeaker> findByIdAndWorkshopId(UUID id, UUID workshopId);
}
