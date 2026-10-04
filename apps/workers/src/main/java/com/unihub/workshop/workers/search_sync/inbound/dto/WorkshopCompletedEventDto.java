package com.unihub.workshop.workers.search_sync.inbound.dto;

import java.util.UUID;

public record WorkshopCompletedEventDto(
    UUID workshopId,
    UUID organizationId,
    String title,
    String slug,
    String startsAt,
    String endsAt,
    String completedAt,
    String occurredAt
) {}
