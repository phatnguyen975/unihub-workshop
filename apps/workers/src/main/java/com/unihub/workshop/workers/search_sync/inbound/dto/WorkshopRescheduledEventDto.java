package com.unihub.workshop.workers.search_sync.inbound.dto;

import java.util.UUID;

public record WorkshopRescheduledEventDto(
    UUID workshopId,
    UUID organizationId,
    String title,
    String slug,
    String location,
    String timezone,
    String startsAt,
    String endsAt,
    String previousStartsAt,
    String previousEndsAt,
    String newStartsAt,
    String newEndsAt,
    String updatedAt,
    String occurredAt
) {
    public String effectiveStartsAt() {
        if (newStartsAt != null && !newStartsAt.isBlank()) {
            return newStartsAt;
        }
        return startsAt;
    }

    public String effectiveEndsAt() {
        if (newEndsAt != null && !newEndsAt.isBlank()) {
            return newEndsAt;
        }
        return endsAt;
    }
}
