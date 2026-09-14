package com.unihub.workshop.workers.batch_ingestion.outbound.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrganizationRepository
    extends JpaRepository<OrganizationEntity, UUID>
{
    Optional<OrganizationEntity> findBySlugAndStatus(
        String slug,
        String status
    );
}
