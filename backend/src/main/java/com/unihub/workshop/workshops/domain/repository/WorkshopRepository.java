package com.unihub.workshop.workshops.domain.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.unihub.workshop.workshops.domain.model.Workshop;

public interface WorkshopRepository extends JpaRepository<Workshop, UUID> {
}
