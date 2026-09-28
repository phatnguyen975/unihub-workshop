package com.unihub.workshop.workshops.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionJobReportJpaRepository
    extends JpaRepository<IngestionJobReportJpaEntity, UUID>
{
    Optional<IngestionJobReportJpaEntity> findByIdAndOrganizationId(
        UUID id,
        UUID organizationId
    );
}
