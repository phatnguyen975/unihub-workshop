package com.unihub.workshop.workshops.infrastructure.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkshopAssignmentJpaRepository
    extends JpaRepository<WorkshopAssignmentJpaEntity, UUID>
{
    boolean existsByWorkshopIdAndStaffUserId(UUID workshopId, UUID staffUserId);

    Optional<WorkshopAssignmentJpaEntity> findByWorkshopIdAndStaffUserId(
        UUID workshopId,
        UUID staffUserId
    );

    void deleteByWorkshopIdAndStaffUserId(UUID workshopId, UUID staffUserId);

    @Query(
        "SELECT wa.staffUserId FROM WorkshopAssignmentJpaEntity wa WHERE wa.workshopId = :workshopId"
    )
    Set<UUID> findAssignedStaffUserIds(@Param("workshopId") UUID workshopId);

    @Query(
        """
        SELECT wa.staffUserId
        FROM WorkshopAssignmentJpaEntity wa
        WHERE wa.workshopId = :workshopId
            AND wa.staffUserId IN :staffUserIds
        """
    )
    Set<UUID> findAlreadyAssignedUserIds(
        @Param("workshopId") UUID workshopId,
        @Param("staffUserIds") Collection<UUID> staffUserIds
    );

    @Query(
        """
        SELECT DISTINCT wa.staffUserId
        FROM WorkshopAssignmentJpaEntity wa, WorkshopJpaEntity w
        WHERE wa.workshopId = w.id
            AND w.organizationId = :organizationId
            AND w.id != :targetWorkshopId
            AND w.status != 'CANCELED'
            AND w.startsAt < :endsAt
            AND w.endsAt > :startsAt
            AND wa.staffUserId IN :staffUserIds
        """
    )
    Set<UUID> findScheduleConflictingUserIds(
        @Param("organizationId") UUID organizationId,
        @Param("targetWorkshopId") UUID targetWorkshopId,
        @Param("startsAt") Instant startsAt,
        @Param("endsAt") Instant endsAt,
        @Param("staffUserIds") Collection<UUID> staffUserIds
    );

    @Query(
        """
        SELECT DISTINCT wa.staffUserId
        FROM WorkshopAssignmentJpaEntity wa, WorkshopJpaEntity w
        WHERE wa.workshopId = w.id
            AND w.organizationId = :organizationId
            AND w.id != :targetWorkshopId
            AND w.status != 'CANCELED'
            AND w.startsAt < :endsAt
            AND w.endsAt > :startsAt
        """
    )
    Set<UUID> findScheduleConflictingStaffUserIds(
        @Param("organizationId") UUID organizationId,
        @Param("targetWorkshopId") UUID targetWorkshopId,
        @Param("startsAt") Instant startsAt,
        @Param("endsAt") Instant endsAt
    );

    @Query(
        """
        SELECT wa.staffUserId, COUNT(wa.id)
        FROM WorkshopAssignmentJpaEntity wa, WorkshopJpaEntity w
        WHERE wa.workshopId = w.id
            AND w.organizationId = :organizationId
            AND w.status != 'CANCELED'
            AND wa.staffUserId IN :staffUserIds
        GROUP BY wa.staffUserId
        """
    )
    List<Object[]> countActiveAssignmentsForUsers(
        @Param("organizationId") UUID organizationId,
        @Param("staffUserIds") Collection<UUID> staffUserIds
    );
}
