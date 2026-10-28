package com.unihub.workshop.registrations.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegistrationJpaRepository
    extends JpaRepository<RegistrationJpaEntity, UUID>
{
    @Query(
        """
        SELECT r FROM RegistrationJpaEntity r
        WHERE r.status = 'PENDING' AND r.expiresAt < :now
        ORDER BY r.expiresAt ASC
        """
    )
    List<RegistrationJpaEntity> findExpiredPendingRegistrations(
        @Param("now") Instant now,
        Pageable pageable
    );

    @Query(
        value = """
        SELECT COUNT(r.id) > 0 FROM registrations r
        WHERE r.student_user_id = :studentUserId
            AND r.workshop_id = :workshopId
            AND r.status IN ('PENDING', 'CONFIRMED')
        """,
        nativeQuery = true
    )
    boolean existsActiveRegistration(
        @Param("studentUserId") UUID studentUserId,
        @Param("workshopId") UUID workshopId
    );

    @Query(
        value = """
        SELECT COUNT(r.id) > 0 FROM registrations r
        JOIN workshops w ON r.workshop_id = w.id
        WHERE r.student_user_id = :studentUserId
            AND r.status IN ('PENDING', 'CONFIRMED')
            AND w.status = 'PUBLISHED'
            AND w.starts_at < :newEndsAt
            AND w.ends_at > :newStartsAt
        """,
        nativeQuery = true
    )
    boolean hasOverlappingActiveRegistration(
        @Param("studentUserId") UUID studentUserId,
        @Param("newStartsAt") Instant newStartsAt,
        @Param("newEndsAt") Instant newEndsAt
    );

    @Query(
        value = """
        SELECT COUNT(r.id) FROM registrations r
        WHERE r.workshop_id = :workshopId
            AND r.status IN ('PENDING', 'CONFIRMED')
        """,
        nativeQuery = true
    )
    long countActiveRegistrations(@Param("workshopId") UUID workshopId);

    @Modifying
    @Query(
        """
        UPDATE RegistrationJpaEntity r
        SET r.status = 'CANCELED', r.updatedAt = :now, r.updatedBy = :updatedBy
        WHERE r.id = :id AND r.status = 'CONFIRMED'
        """
    )
    int cancelRegistration(
        @Param("id") UUID id,
        @Param("updatedBy") UUID updatedBy,
        @Param("now") Instant now
    );

    @Query(
        """
        SELECT r.status, COUNT(r.id)
        FROM RegistrationJpaEntity r
        WHERE r.workshopId = :workshopId
        GROUP BY r.status
        """
    )
    List<Object[]> countRegistrationsByStatusGrouped(
        @Param("workshopId") UUID workshopId
    );

    @Query(
        """
        SELECT MAX(r.updatedAt)
        FROM RegistrationJpaEntity r
        WHERE r.workshopId = :workshopId
        """
    )
    Optional<Instant> findMaxUpdatedAtByWorkshopId(
        @Param("workshopId") UUID workshopId
    );
}
