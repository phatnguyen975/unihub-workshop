package com.unihub.workshop.workers.search_sync.inbound.dto;

import java.util.UUID;

public record WorkshopCanceledEventDto(
    UUID workshopId,
    UUID organizationId,
    String title,
    String slug,
    String startsAt,
    String endsAt,
    String canceledAt,
    String reason,
    String occurredAt
) {}
