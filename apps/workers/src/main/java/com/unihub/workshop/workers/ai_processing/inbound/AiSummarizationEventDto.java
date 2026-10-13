package com.unihub.workshop.workers.ai_processing.inbound;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiSummarizationEventDto(
    UUID jobId,
    UUID workshopId,
    UUID brochureId,
    String storageKey,
    UUID organizerId,
    Instant occurredAt
) {}
