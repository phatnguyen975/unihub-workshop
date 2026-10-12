package com.unihub.workshop.workshops.presentation.dto;

import java.time.Instant;
import java.util.UUID;

public record BrochureUploadResponse(
    UUID jobId,
    UUID brochureId,
    String status,
    Instant createdAt
) {}
