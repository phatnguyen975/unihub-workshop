package com.unihub.workshop.workshops.domain.model;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder(toBuilder = true)
public record WorkshopBrochure(
    UUID id,
    UUID workshopId,
    String storageKey,
    String fileName,
    String contentType,
    long byteSize,
    String checksumSha256,
    boolean isActive,
    Instant uploadedAt,
    Instant createdAt,
    Instant updatedAt,
    UUID createdBy,
    UUID updatedBy
) {
    public WorkshopBrochure deactivate() {
        return this.toBuilder()
            .isActive(false)
            .updatedAt(Instant.now())
            .build();
    }
}
