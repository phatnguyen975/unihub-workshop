package com.unihub.workshop.workers.notifications.inbound.dto;

import com.unihub.workshop.workers.notifications.core.ports.in.ConsumedNotificationEvent;
import java.time.Instant;
import java.util.UUID;

public record UserProvisionedEventDto(
    UUID userId,
    UUID organizationId,
    String orgSlug,
    String email,
    String fullName,
    String role,
    String temporaryPassword,
    Instant occurredAt
) implements ConsumedNotificationEvent {
    @Override
    public String eventType() {
        return "UserProvisionedEvent";
    }

    @Override
    public String idempotencyKey() {
        return "USER_PROVISIONED:" + userId;
    }
}
