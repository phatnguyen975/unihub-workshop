package com.unihub.workshop.workshops.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RejectArtifactUrlResponse(
    UUID jobId,
    String artifactType,
    String downloadUrl,
    Instant expiresAt
) {}
