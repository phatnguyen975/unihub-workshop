package com.unihub.workshop.workers.notifications.inbound.dto;

import com.unihub.workshop.workers.notifications.core.ports.in.ConsumedNotificationEvent;
import java.time.Instant;
import java.util.UUID;

public record WorkshopCanceledEventDto(
    UUID workshopId,
    UUID organizationId,
    String title,
    String slug,
    Instant startsAt,
    Instant endsAt,
    Instant canceledAt,
    String reason,
    Instant occurredAt
) implements ConsumedNotificationEvent {
    @Override
    public String eventType() {
        return "WorkshopCanceledEvent";
    }

    @Override
    public String idempotencyKey() {
        return "WORKSHOP_CANCELED:" + workshopId;
    }
}
