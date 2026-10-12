package com.unihub.workshop.workers.ai_processing.outbound.persistence;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaAiSummaryJobRepository
    extends JpaRepository<AiSummaryJobJpaEntity, UUID>
{
    @Modifying
    @Query(
        "UPDATE AiSummaryJobJpaEntity j SET j.status = :status, j.resultText = :resultText, j.completedAt = :completedAt, j.updatedAt = :now WHERE j.id = :id"
    )
    void updateCompleted(
        @Param("id") UUID id,
        @Param("status") String status,
        @Param("resultText") String resultText,
        @Param("completedAt") Instant completedAt,
        @Param("now") Instant now
    );

    @Modifying
    @Query(
        "UPDATE AiSummaryJobJpaEntity j SET j.status = :status, j.errorMessage = :errorMessage, j.completedAt = :completedAt, j.updatedAt = :now WHERE j.id = :id"
    )
    void updateFailed(
        @Param("id") UUID id,
        @Param("status") String status,
        @Param("errorMessage") String errorMessage,
        @Param("completedAt") Instant completedAt,
        @Param("now") Instant now
    );
}
