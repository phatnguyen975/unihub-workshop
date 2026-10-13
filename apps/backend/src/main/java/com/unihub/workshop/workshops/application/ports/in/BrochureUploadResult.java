package com.unihub.workshop.workshops.application.ports.in;

import java.time.Instant;
import java.util.UUID;

public record BrochureUploadResult(
    UUID jobId,
    UUID brochureId,
    String status,
    Instant createdAt
) {}
