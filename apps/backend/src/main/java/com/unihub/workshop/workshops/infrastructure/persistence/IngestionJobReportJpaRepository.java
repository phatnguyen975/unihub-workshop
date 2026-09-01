package com.unihub.workshop.workshops.infrastructure.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface IngestionJobReportJpaRepository extends JpaRepository<IngestionJobReportJpaEntity, UUID> {

    @Query("SELECT r FROM IngestionJobReportJpaEntity r WHERE r.organizationId = :orgId " +
            "AND (:cursorTime IS NULL OR r.completedAt < :cursorTime OR (r.completedAt = :cursorTime AND r.id < :cursorId)) "
            + "ORDER BY r.completedAt DESC, r.id DESC")
    List<IngestionJobReportJpaEntity> findNextPage(
            @Param("orgId") UUID orgId,
            @Param("cursorTime") Instant cursorTime,
            @Param("cursorId") UUID cursorId,
            Pageable pageable);
}
