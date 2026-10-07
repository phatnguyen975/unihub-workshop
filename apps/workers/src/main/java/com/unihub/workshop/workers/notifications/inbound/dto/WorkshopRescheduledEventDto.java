package com.unihub.workshop.workers.notifications.inbound.dto;

import com.unihub.workshop.workers.notifications.core.ports.in.ConsumedNotificationEvent;
import java.time.Instant;
import java.util.UUID;

public record WorkshopRescheduledEventDto(
    UUID workshopId,
    UUID organizationId,
    String title,
    String slug,
    String location,
    String timezone,
    Instant previousStartsAt,
    Instant previousEndsAt,
    Instant newStartsAt,
    Instant newEndsAt,
    Instant updatedAt,
    Instant occurredAt
) implements ConsumedNotificationEvent {
    @Override
    public String eventType() {
        return "WorkshopRescheduledEvent";
    }

    @Override
    public String idempotencyKey() {
        return (
            "WORKSHOP_RESCHEDULED:" +
            workshopId +
            ":" +
            (newStartsAt != null ? newStartsAt : "")
        );
    }
}
