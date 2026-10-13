package com.unihub.workshop.workers.ai_processing.core.ports.out;

public interface AiSummarizationMetricsPort {
    void recordJobStarted();

    void recordJobCompleted();

    void recordJobFailed();

    void recordJobRetried();
}
