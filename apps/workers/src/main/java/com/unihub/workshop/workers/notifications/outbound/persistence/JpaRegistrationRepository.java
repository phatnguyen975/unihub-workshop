package com.unihub.workshop.workers.notifications.outbound.persistence;

import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaRegistrationRepository
    extends JpaRepository<RegistrationEntity, UUID>
{
    @Query(
        """
        SELECT new com.unihub.workshop.workers.notifications.outbound.persistence.AttendeeProjection(
            u.id, u.email, u.fullName)
        FROM RegistrationEntity r
        JOIN UserEntity u ON r.studentUserId = u.id
        WHERE r.workshopId = :workshopId AND r.status = 'CONFIRMED'
        ORDER BY r.id ASC
        """
    )
    Slice<AttendeeProjection> findConfirmedAttendeesByWorkshopId(
        @Param("workshopId") UUID workshopId,
        Pageable pageable
    );
}
