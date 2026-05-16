package com.unihub.workshop.workshops.domain.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.domain.model.WorkshopStatus;

public interface WorkshopRepository extends JpaRepository<Workshop, UUID>, WorkshopRepositoryCustom {

	Page<Workshop> findByOrganizerId(UUID organizerId, Pageable pageable);

	Page<Workshop> findByOrganizerIdAndStatus(UUID organizerId, WorkshopStatus status, Pageable pageable);
}
