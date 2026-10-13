package com.unihub.workshop.workers.ai_processing.core.ports.out;

import com.unihub.workshop.workers.ai_processing.core.models.AiSummaryJobModel;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AiSummaryJobRepositoryPort {
    Optional<AiSummaryJobModel> findById(UUID jobId);

    void updateCompleted(UUID jobId, String resultText, Instant completedAt);

    void updateFailed(UUID jobId, String errorMessage, Instant completedAt);

    void updateFailed(
        UUID jobId,
        int attempts,
        String errorMessage,
        Instant completedAt
    );

    void updateRetry(UUID jobId, int attempts, Instant nextRetryAt);
}
