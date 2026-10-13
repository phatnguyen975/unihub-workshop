package com.unihub.workshop.registrations.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegistrationJpaRepository
    extends JpaRepository<RegistrationJpaEntity, UUID>
{
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
}
