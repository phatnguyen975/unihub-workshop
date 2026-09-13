package com.unihub.workshop.shared.events;

import java.time.Instant;
import java.util.UUID;
import org.springframework.modulith.events.Externalized;

/**
 * Event published when an organization member active status is updated (ban or
 * unban). Externalized to RabbitMQ queue q.notifications via routing key
 * "notifications".
 */
@Externalized("unihub.events::notifications")
public record UserStatusChangedEvent(
    UUID userId,
    UUID organizationId,
    String email,
    String fullName,
    boolean isActive,
    Instant occurredAt
) {
    public UserStatusChangedEvent(
        UUID userId,
        UUID organizationId,
        String email,
        String fullName,
        boolean isActive
    ) {
        this(userId, organizationId, email, fullName, isActive, Instant.now());
    }
}
