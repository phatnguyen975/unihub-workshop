package com.unihub.workshop.workshops.application.usecases;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record RejectArtifactUrlResult(
    UUID jobId,
    String artifactType,
    String downloadUrl,
    Instant expiresAt
) {}
