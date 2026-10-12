package com.unihub.workshop.workers.ai_processing.core.ports.out;

import java.util.UUID;

public interface RealtimePublisherPort {
    void publishSummaryCompleted(
        UUID organizerId,
        UUID jobId,
        UUID workshopId,
        String resultText
    );

    void publishSummaryFailed(
        UUID organizerId,
        UUID jobId,
        UUID workshopId,
        String errorMessage
    );
}
