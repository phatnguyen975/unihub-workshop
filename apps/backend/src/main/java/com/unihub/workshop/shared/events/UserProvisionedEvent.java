package com.unihub.workshop.shared.events;

import java.time.Instant;
import java.util.UUID;
import org.springframework.modulith.events.Externalized;

/**
 * Event published when a user account is created with a system-generated temporary password.
 * Externalized to RabbitMQ queue q.notifications via routing key "notifications".
 */
@Externalized("unihub.events::notifications")
public record UserProvisionedEvent(
    UUID userId,
    UUID organizationId,
    String orgSlug,
    String email,
    String fullName,
    String role,
    String temporaryPassword,
    Instant occurredAt
) {
    public UserProvisionedEvent(
        UUID userId,
        UUID organizationId,
        String orgSlug,
        String email,
        String fullName,
        String role,
        String temporaryPassword
    ) {
        this(
            userId,
            organizationId,
            orgSlug,
            email,
            fullName,
            role,
            temporaryPassword,
            Instant.now()
        );
    }
}
