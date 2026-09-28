package com.unihub.workshop.workshops.domain.model;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder(toBuilder = true)
public record Speaker(
    UUID id,
    UUID workshopId,
    String name,
    String email,
    String bio,
    String avatarUrl,
    String linkedinUrl,
    Integer sortOrder,
    Instant createdAt,
    Instant updatedAt,
    UUID createdBy,
    UUID updatedBy
) {
    public static Speaker create(
        UUID workshopId,
        String name,
        String email,
        String bio,
        String avatarUrl,
        String linkedinUrl,
        Integer sortOrder,
        UUID createdBy
    ) {
        Instant now = Instant.now();
        return Speaker.builder()
            .id(UUID.randomUUID())
            .workshopId(workshopId)
            .name(name != null ? name.trim() : null)
            .email(email != null ? email.toLowerCase().trim() : null)
            .bio(bio != null && !bio.isBlank() ? bio.trim() : null)
            .avatarUrl(
                avatarUrl != null && !avatarUrl.isBlank()
                    ? avatarUrl.trim()
                    : null
            )
            .linkedinUrl(
                linkedinUrl != null && !linkedinUrl.isBlank()
                    ? linkedinUrl.trim()
                    : null
            )
            .sortOrder(sortOrder != null ? sortOrder : 0)
            .createdAt(now)
            .updatedAt(now)
            .createdBy(createdBy)
            .updatedBy(createdBy)
            .build();
    }
}
