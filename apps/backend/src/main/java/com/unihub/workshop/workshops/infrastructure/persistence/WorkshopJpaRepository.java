package com.unihub.workshop.workshops.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkshopJpaRepository
    extends JpaRepository<WorkshopJpaEntity, UUID>
{
    Optional<WorkshopJpaEntity> findByIdAndOrganizationId(
        UUID id,
        UUID organizationId
    );

    boolean existsBySlug(String slug);
}
