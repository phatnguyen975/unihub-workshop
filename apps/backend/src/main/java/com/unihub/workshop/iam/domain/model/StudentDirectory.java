package com.unihub.workshop.iam.domain.model;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder(toBuilder = true)
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
    Instant createdAt,
    Instant updatedAt,
    UUID createdBy,
    UUID updatedBy
) {
    public StudentDirectory(
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
    ) {
        this(
            id,
            organizationId,
            studentNumber,
            email,
            fullName,
            faculty,
            status,
            sourceFileKey,
            importedAt,
            createdAt,
            createdAt != null ? createdAt : Instant.now(),
            null,
            null
        );
    }
}
