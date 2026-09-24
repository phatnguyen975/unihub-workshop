package com.unihub.workshop.workshops.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IngestionJobReportJpaRepository
    extends JpaRepository<IngestionJobReportJpaEntity, UUID>
{
    Optional<IngestionJobReportJpaEntity> findByIdAndOrganizationId(
        UUID id,
        UUID organizationId
    );

    @Query(
        """
        SELECT r FROM IngestionJobReportJpaEntity r
        WHERE r.organizationId = :orgId
        ORDER BY r.completedAt DESC, r.id DESC
        """
    )
    List<IngestionJobReportJpaEntity> findFirstPage(
        @Param("orgId") UUID orgId,
        Pageable pageable
    );

    @Query(
        """
        SELECT r FROM IngestionJobReportJpaEntity r
        WHERE r.organizationId = :orgId
            AND (
                r.completedAt < :cursorTime
                OR (r.completedAt = :cursorTime AND r.id < :cursorId)
            )
        ORDER BY r.completedAt DESC, r.id DESC
        """
    )
    List<IngestionJobReportJpaEntity> findNextPage(
        @Param("orgId") UUID orgId,
        @Param("cursorTime") Instant cursorTime,
        @Param("cursorId") UUID cursorId,
        Pageable pageable
    );
}
