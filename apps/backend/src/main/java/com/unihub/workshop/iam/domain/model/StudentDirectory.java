package com.unihub.workshop.iam.domain.model;

import java.time.Instant;
import java.util.UUID;

public record StudentDirectory(
    UUID id,
    UUID organizationId,
    String studentNumber,
    String email,
    String fullName,
    String faculty,
    String status,
    String sourceFileKey,
    Instant importedAt,
    Instant createdAt
) {}
