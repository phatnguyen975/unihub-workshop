package com.unihub.workshop.workshops.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkshopBrochureJpaRepository
    extends JpaRepository<WorkshopBrochureJpaEntity, UUID>
{
    Optional<WorkshopBrochureJpaEntity> findByWorkshopIdAndIsActiveTrue(
        UUID workshopId
    );

    @Modifying
    @Query(
        "UPDATE WorkshopBrochureJpaEntity b SET b.isActive = false, b.updatedAt = :now WHERE b.workshopId = :workshopId AND b.isActive = true"
    )
    void deactivateAllByWorkshopId(
        @Param("workshopId") UUID workshopId,
        @Param("now") Instant now
    );
}
