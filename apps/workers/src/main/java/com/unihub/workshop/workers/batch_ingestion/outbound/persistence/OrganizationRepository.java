package com.unihub.workshop.workers.batch_ingestion.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganizationRepository extends JpaRepository<OrganizationEntity, UUID> {
    Optional<OrganizationEntity> findBySlug(String slug);
}
