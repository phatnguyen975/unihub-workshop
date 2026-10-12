package com.unihub.workshop.workers.ai_processing.core.models;

import java.util.UUID;

public record BrochureSummarizationRequest(
    UUID jobId,
    UUID workshopId,
    UUID brochureId,
    String storageKey,
    UUID organizerId
) {}
