package com.unihub.workshop.workers.ai_processing.outbound.metrics;

import com.unihub.workshop.workers.ai_processing.core.ports.out.AiSummarizationMetricsPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MicrometerAiSummarizationMetricsAdapter
    implements AiSummarizationMetricsPort
{

    public static final String METRIC_JOBS_STARTED =
        "ai.summarization.jobs.started";
    public static final String METRIC_JOBS_COMPLETED =
        "ai.summarization.jobs.completed";
    public static final String METRIC_JOBS_FAILED =
        "ai.summarization.jobs.failed";
    public static final String METRIC_JOBS_RETRIED =
        "ai.summarization.jobs.retried";

    private final Counter jobsStartedCounter;
    private final Counter jobsCompletedCounter;
    private final Counter jobsFailedCounter;
    private final Counter jobsRetriedCounter;

    public MicrometerAiSummarizationMetricsAdapter(
        Optional<MeterRegistry> meterRegistry
    ) {
        MeterRegistry registry =
            meterRegistry != null ? meterRegistry.orElse(null) : null;
        if (registry != null) {
            this.jobsStartedCounter = Counter.builder(METRIC_JOBS_STARTED)
                .description(
                    "Total number of brochure AI summarization jobs started"
                )
                .register(registry);
            this.jobsCompletedCounter = Counter.builder(METRIC_JOBS_COMPLETED)
                .description(
                    "Total number of brochure AI summarization jobs successfully completed"
                )
                .register(registry);
            this.jobsFailedCounter = Counter.builder(METRIC_JOBS_FAILED)
                .description(
                    "Total number of brochure AI summarization jobs failed permanently"
                )
                .register(registry);
            this.jobsRetriedCounter = Counter.builder(METRIC_JOBS_RETRIED)
                .description(
                    "Total number of brochure AI summarization job transient retries"
                )
                .register(registry);
        } else {
            this.jobsStartedCounter = null;
            this.jobsCompletedCounter = null;
            this.jobsFailedCounter = null;
            this.jobsRetriedCounter = null;
        }
    }

    @Override
    public void recordJobStarted() {
        if (jobsStartedCounter != null) {
            jobsStartedCounter.increment();
        }
        log.debug("Metric incremented: {}", METRIC_JOBS_STARTED);
    }

    @Override
    public void recordJobCompleted() {
        if (jobsCompletedCounter != null) {
            jobsCompletedCounter.increment();
        }
        log.debug("Metric incremented: {}", METRIC_JOBS_COMPLETED);
    }

    @Override
    public void recordJobFailed() {
        if (jobsFailedCounter != null) {
            jobsFailedCounter.increment();
        }
        log.debug("Metric incremented: {}", METRIC_JOBS_FAILED);
    }

    @Override
    public void recordJobRetried() {
        if (jobsRetriedCounter != null) {
            jobsRetriedCounter.increment();
        }
        log.debug("Metric incremented: {}", METRIC_JOBS_RETRIED);
    }
}
