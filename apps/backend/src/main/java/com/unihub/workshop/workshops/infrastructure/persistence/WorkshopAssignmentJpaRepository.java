package com.unihub.workshop.workshops.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkshopAssignmentJpaRepository
    extends JpaRepository<WorkshopAssignmentJpaEntity, UUID>
{
    boolean existsByWorkshopIdAndStaffUserId(UUID workshopId, UUID staffUserId);

    Optional<WorkshopAssignmentJpaEntity> findByWorkshopIdAndStaffUserId(
        UUID workshopId,
        UUID staffUserId
    );

    void deleteByWorkshopIdAndStaffUserId(UUID workshopId, UUID staffUserId);
}
