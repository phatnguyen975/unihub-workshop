package com.unihub.workshop.registrations.repository;

import com.unihub.workshop.registrations.domain.Registration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface RegistrationRepository extends JpaRepository<Registration, UUID> {

    @Query("select r from Registration r join r.workshop w where r.userId = :userId and r.status in :statuses and (w.startTime < :targetEnd and w.endTime > :targetStart)")
    List<Registration> findOverlappingByUserIdAndStatuses(
            @Param("userId") UUID userId,
            @Param("statuses") List<String> statuses,
            @Param("targetStart") OffsetDateTime targetStart,
            @Param("targetEnd") OffsetDateTime targetEnd
    );

    long countByWorkshopIdAndStatus(UUID workshopId, String status);

    List<Registration> findByWorkshopId(UUID workshopId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Registration r where r.id = :id")
    java.util.Optional<Registration> findByIdForUpdate(@Param("id") UUID id);

    @Query(value = "SELECT * FROM registrations WHERE status = 'PENDING' AND created_at < (NOW() - INTERVAL '15 minutes') ORDER BY created_at ASC FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<Registration> findNextExpiredPendingForUpdateSkipLocked();

    Page<Registration> findByUserId(UUID userId, Pageable pageable);
}
