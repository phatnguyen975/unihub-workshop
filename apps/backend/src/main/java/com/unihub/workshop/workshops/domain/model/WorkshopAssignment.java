package com.unihub.workshop.workshops.domain.model;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder(toBuilder = true)
public record WorkshopAssignment(
    UUID id,
    UUID workshopId,
    UUID staffUserId,
    UUID assignedBy,
    Instant assignedAt,
    Instant createdAt,
    Instant updatedAt,
    UUID createdBy,
    UUID updatedBy
) {
    public static WorkshopAssignment create(
        UUID workshopId,
        UUID staffUserId,
        UUID assignedBy
    ) {
        Instant now = Instant.now();
        return WorkshopAssignment.builder()
            .id(UUID.randomUUID())
            .workshopId(workshopId)
            .staffUserId(staffUserId)
            .assignedBy(assignedBy)
            .assignedAt(now)
            .createdAt(now)
            .updatedAt(now)
            .createdBy(assignedBy)
            .updatedBy(assignedBy)
            .build();
    }
}
