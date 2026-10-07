package com.unihub.workshop.workers.notifications.inbound.dto;

import com.unihub.workshop.workers.notifications.core.ports.in.ConsumedNotificationEvent;
import java.time.Instant;
import java.util.UUID;

public record UserStatusChangedEventDto(
    UUID userId,
    UUID organizationId,
    String email,
    String fullName,
    Boolean isActive,
    Instant occurredAt
) implements ConsumedNotificationEvent {
    @Override
    public String eventType() {
        return "UserStatusChangedEvent";
    }

    @Override
    public String idempotencyKey() {
        return (
            "USER_STATUS_CHANGED:" +
            userId +
            ":" +
            (Boolean.TRUE.equals(isActive) ? "ACTIVE" : "INACTIVE")
        );
    }
}
