package com.unihub.workshop.workshops.domain.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.unihub.workshop.workshops.domain.model.Brochure;

public interface BrochureRepository extends JpaRepository<Brochure, UUID> {

    List<Brochure> findByWorkshopId(UUID workshopId);
}
