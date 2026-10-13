package com.unihub.workshop.workshops.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiSummaryJobJpaRepository
    extends JpaRepository<AiSummaryJobJpaEntity, UUID>
{
    boolean existsByWorkshopIdAndStatus(UUID workshopId, String status);

    Optional<AiSummaryJobJpaEntity> findFirstByWorkshopIdOrderByCreatedAtDesc(
        UUID workshopId
    );
}
