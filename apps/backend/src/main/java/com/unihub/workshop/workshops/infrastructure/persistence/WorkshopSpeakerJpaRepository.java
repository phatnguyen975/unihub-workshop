package com.unihub.workshop.workshops.infrastructure.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkshopSpeakerJpaRepository
    extends JpaRepository<WorkshopSpeakerJpaEntity, UUID>
{
    List<WorkshopSpeakerJpaEntity> findByWorkshopIdOrderBySortOrderAsc(
        UUID workshopId
    );

    @Modifying
    @Query(
        "DELETE FROM WorkshopSpeakerJpaEntity s WHERE s.workshopId = :workshopId"
    )
    void deleteByWorkshopId(@Param("workshopId") UUID workshopId);

    @Query(
        """
        SELECT DISTINCT LOWER(s.email)
        FROM WorkshopSpeakerJpaEntity s, WorkshopJpaEntity w
        WHERE s.workshopId = w.id
            AND LOWER(s.email) IN :emails
            AND w.status IN ('PUBLISHED', 'COMPLETED')
            AND w.startsAt IS NOT NULL
            AND w.endsAt IS NOT NULL
            AND w.startsAt < :endsAt
            AND w.endsAt > :startsAt
        """
    )
    List<String> findConflictingSpeakerEmails(
        @Param("emails") Collection<String> emails,
        @Param("startsAt") Instant startsAt,
        @Param("endsAt") Instant endsAt
    );

    @Query(
        """
        SELECT DISTINCT LOWER(s.email)
        FROM WorkshopSpeakerJpaEntity s, WorkshopJpaEntity w
        WHERE s.workshopId = w.id
            AND LOWER(s.email) IN :emails
            AND w.status IN ('PUBLISHED', 'COMPLETED')
            AND w.id != :excludeWorkshopId
            AND w.startsAt IS NOT NULL
            AND w.endsAt IS NOT NULL
            AND w.startsAt < :endsAt
            AND w.endsAt > :startsAt
        """
    )
    List<String> findConflictingSpeakerEmailsExcludingWorkshop(
        @Param("emails") Collection<String> emails,
        @Param("startsAt") Instant startsAt,
        @Param("endsAt") Instant endsAt,
        @Param("excludeWorkshopId") UUID excludeWorkshopId
    );

    @Query(
        """
        SELECT s.workshopId, COUNT(s.id)
        FROM WorkshopSpeakerJpaEntity s
        WHERE s.workshopId IN :workshopIds
        GROUP BY s.workshopId
        """
    )
    List<Object[]> countSpeakersByWorkshopIds(
        @Param("workshopIds") Collection<UUID> workshopIds
    );
}
