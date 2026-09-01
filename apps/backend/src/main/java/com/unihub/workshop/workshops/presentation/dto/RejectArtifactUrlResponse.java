package com.unihub.workshop.workshops.presentation.dto;

import java.time.Instant;
import java.util.UUID;

public record RejectArtifactUrlResponse(
        UUID jobId,
        String artifactType,
        String downloadUrl,
        Instant expiresAt) {
}
