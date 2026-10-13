package com.unihub.workshop.workers.ai_processing.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unihub.workshop.workers.ai_processing.core.models.AiSummaryJobModel;
import com.unihub.workshop.workers.ai_processing.core.models.AiSummaryJobStatusModel;
import com.unihub.workshop.workers.ai_processing.outbound.persistence.mapper.AiSummaryJobWorkerMapper;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class JpaAiSummaryJobRepositoryAdapterTest {

    private JpaAiSummaryJobRepository jpaRepository;
    private AiSummaryJobWorkerMapper mapper;
    private JpaAiSummaryJobRepositoryAdapter adapter;

    private static final UUID JOB_ID = UUID.randomUUID();
    private static final UUID WORKSHOP_ID = UUID.randomUUID();
    private static final UUID BROCHURE_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jpaRepository = mock(JpaAiSummaryJobRepository.class);
        mapper = Mappers.getMapper(AiSummaryJobWorkerMapper.class);
        adapter = new JpaAiSummaryJobRepositoryAdapter(jpaRepository, mapper);
    }

    @Test
    @DisplayName("Should find job by ID and map to model")
    void shouldFindById() {
        AiSummaryJobJpaEntity entity = AiSummaryJobJpaEntity.builder()
            .id(JOB_ID)
            .workshopId(WORKSHOP_ID)
            .brochureId(BROCHURE_ID)
            .status("PROCESSING")
            .attempts(0)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .createdBy(UUID.randomUUID())
            .updatedBy(UUID.randomUUID())
            .build();

        when(jpaRepository.findById(JOB_ID)).thenReturn(Optional.of(entity));

        Optional<AiSummaryJobModel> result = adapter.findById(JOB_ID);

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(JOB_ID);
        assertThat(result.get().status()).isEqualTo(
            AiSummaryJobStatusModel.PROCESSING
        );
    }

    @Test
    @DisplayName("Should update job status to COMPLETED")
    void shouldUpdateCompleted() {
        Instant completedAt = Instant.now();
        adapter.updateCompleted(JOB_ID, "Summary result", completedAt);

        verify(jpaRepository).updateCompleted(
            eq(JOB_ID),
            eq("COMPLETED"),
            eq("Summary result"),
            eq(completedAt),
            any(Instant.class)
        );
    }

    @Test
    @DisplayName("Should update job status to FAILED")
    void shouldUpdateFailed() {
        Instant completedAt = Instant.now();
        adapter.updateFailed(JOB_ID, "Error message", completedAt);

        verify(jpaRepository).updateFailed(
            eq(JOB_ID),
            eq("FAILED"),
            eq("Error message"),
            eq(completedAt),
            any(Instant.class)
        );
    }

    @Test
    @DisplayName("Should update job status to FAILED with attempts")
    void shouldUpdateFailedWithAttempts() {
        Instant completedAt = Instant.now();
        adapter.updateFailed(JOB_ID, 3, "Max retries", completedAt);

        verify(jpaRepository).updateFailedWithAttempts(
            eq(JOB_ID),
            eq("FAILED"),
            eq(3),
            eq("Max retries"),
            eq(completedAt),
            any(Instant.class)
        );
    }

    @Test
    @DisplayName("Should update job retry attempts and nextRetryAt")
    void shouldUpdateRetry() {
        Instant nextRetryAt = Instant.now().plusSeconds(10);
        adapter.updateRetry(JOB_ID, 2, nextRetryAt);

        verify(jpaRepository).updateRetry(
            eq(JOB_ID),
            eq(2),
            eq(nextRetryAt),
            any(Instant.class)
        );
    }
}
