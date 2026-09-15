package com.unihub.workshop.workshops.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public record RejectArtifactUrlResponse(
    UUID jobId,
    String artifactType,
    String downloadUrl,
    Instant expiresAt
) {}
