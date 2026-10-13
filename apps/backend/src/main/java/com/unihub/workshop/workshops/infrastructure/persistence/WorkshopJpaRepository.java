package com.unihub.workshop.workshops.infrastructure.persistence;

import com.unihub.workshop.workshops.domain.model.ActiveWorkshopCapacity;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkshopJpaRepository
    extends JpaRepository<WorkshopJpaEntity, UUID>
{
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM WorkshopJpaEntity w WHERE w.id = :id")
    Optional<WorkshopJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    Optional<WorkshopJpaEntity> findByIdAndOrganizationId(
        UUID id,
        UUID organizationId
    );

    boolean existsBySlug(String slug);

    Optional<WorkshopJpaEntity> findBySlug(String slug);

    @Query(
        """
        SELECT w.id
        FROM WorkshopJpaEntity w
        WHERE LOWER(TRIM(w.location)) = LOWER(TRIM(:location))
            AND w.status IN ('PUBLISHED', 'COMPLETED')
            AND w.id != :excludeWorkshopId
            AND w.startsAt IS NOT NULL
            AND w.endsAt IS NOT NULL
            AND w.startsAt < :endsAt
            AND w.endsAt > :startsAt
        """
    )
    List<UUID> findCollidingWorkshopIdsAtLocationExcludingWorkshop(
        @Param("location") String location,
        @Param("startsAt") Instant startsAt,
        @Param("endsAt") Instant endsAt,
        @Param("excludeWorkshopId") UUID excludeWorkshopId
    );

    @Query(
        """
        SELECT w.id
        FROM WorkshopJpaEntity w
        WHERE LOWER(TRIM(w.location)) = LOWER(TRIM(:location))
            AND w.status IN ('PUBLISHED', 'COMPLETED')
            AND w.startsAt IS NOT NULL
            AND w.endsAt IS NOT NULL
            AND w.startsAt < :endsAt
            AND w.endsAt > :startsAt
        """
    )
    List<UUID> findCollidingWorkshopIdsAtLocation(
        @Param("location") String location,
        @Param("startsAt") Instant startsAt,
        @Param("endsAt") Instant endsAt
    );

    @Query(
        """
        SELECT new com.unihub.workshop.workshops.domain.model.ActiveWorkshopCapacity(w.id, w.capacity)
        FROM WorkshopJpaEntity w
        WHERE w.status = 'PUBLISHED'
            AND w.endsAt IS NOT NULL
            AND w.endsAt > :now
        """
    )
    List<ActiveWorkshopCapacity> findActivePublishedWorkshops(
        @Param("now") Instant now
    );
}
