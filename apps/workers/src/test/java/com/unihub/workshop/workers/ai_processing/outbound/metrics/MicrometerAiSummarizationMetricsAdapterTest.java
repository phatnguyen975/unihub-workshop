package com.unihub.workshop.workers.ai_processing.outbound.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MicrometerAiSummarizationMetricsAdapterTest {

    @Test
    @DisplayName("Should increment respective counters on job lifecycle events")
    void shouldIncrementCountersOnEvents() {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        MicrometerAiSummarizationMetricsAdapter adapter =
            new MicrometerAiSummarizationMetricsAdapter(
                Optional.of(meterRegistry)
            );

        adapter.recordJobStarted();
        adapter.recordJobCompleted();
        adapter.recordJobRetried();
        adapter.recordJobFailed();

        assertThat(
            meterRegistry
                .get(
                    MicrometerAiSummarizationMetricsAdapter.METRIC_JOBS_STARTED
                )
                .counter()
                .count()
        ).isEqualTo(1.0);
        assertThat(
            meterRegistry
                .get(
                    MicrometerAiSummarizationMetricsAdapter.METRIC_JOBS_COMPLETED
                )
                .counter()
                .count()
        ).isEqualTo(1.0);
        assertThat(
            meterRegistry
                .get(
                    MicrometerAiSummarizationMetricsAdapter.METRIC_JOBS_RETRIED
                )
                .counter()
                .count()
        ).isEqualTo(1.0);
        assertThat(
            meterRegistry
                .get(MicrometerAiSummarizationMetricsAdapter.METRIC_JOBS_FAILED)
                .counter()
                .count()
        ).isEqualTo(1.0);
    }

    @Test
    @DisplayName(
        "Should operate gracefully without throwing when MeterRegistry is absent"
    )
    void shouldOperateGracefullyWhenRegistryAbsent() {
        MicrometerAiSummarizationMetricsAdapter adapter =
            new MicrometerAiSummarizationMetricsAdapter(Optional.empty());

        adapter.recordJobStarted();
        adapter.recordJobCompleted();
        adapter.recordJobRetried();
        adapter.recordJobFailed();
    }
}
