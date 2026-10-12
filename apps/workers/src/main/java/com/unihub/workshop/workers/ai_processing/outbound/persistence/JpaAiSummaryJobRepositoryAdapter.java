package com.unihub.workshop.workers.ai_processing.outbound.persistence;

import com.unihub.workshop.workers.ai_processing.core.models.AiSummaryJobModel;
import com.unihub.workshop.workers.ai_processing.core.models.AiSummaryJobStatusModel;
import com.unihub.workshop.workers.ai_processing.core.ports.out.AiSummaryJobRepositoryPort;
import com.unihub.workshop.workers.ai_processing.outbound.persistence.mapper.AiSummaryJobWorkerMapper;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class JpaAiSummaryJobRepositoryAdapter
    implements AiSummaryJobRepositoryPort
{

    private final JpaAiSummaryJobRepository jpaRepository;
    private final AiSummaryJobWorkerMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public Optional<AiSummaryJobModel> findById(UUID jobId) {
        return jpaRepository.findById(jobId).map(mapper::toModel);
    }

    @Override
    @Transactional
    public void updateCompleted(
        UUID jobId,
        String resultText,
        Instant completedAt
    ) {
        Instant now = Instant.now();
        jpaRepository.updateCompleted(
            jobId,
            AiSummaryJobStatusModel.COMPLETED.name(),
            resultText,
            completedAt,
            now
        );
    }

    @Override
    @Transactional
    public void updateFailed(
        UUID jobId,
        String errorMessage,
        Instant completedAt
    ) {
        Instant now = Instant.now();
        jpaRepository.updateFailed(
            jobId,
            AiSummaryJobStatusModel.FAILED.name(),
            errorMessage,
            completedAt,
            now
        );
    }

    @Override
    @Transactional
    public void updateFailed(
        UUID jobId,
        int attempts,
        String errorMessage,
        Instant completedAt
    ) {
        Instant now = Instant.now();
        jpaRepository.updateFailedWithAttempts(
            jobId,
            AiSummaryJobStatusModel.FAILED.name(),
            attempts,
            errorMessage,
            completedAt,
            now
        );
    }

    @Override
    @Transactional
    public void updateRetry(UUID jobId, int attempts, Instant nextRetryAt) {
        Instant now = Instant.now();
        jpaRepository.updateRetry(jobId, attempts, nextRetryAt, now);
    }
}
