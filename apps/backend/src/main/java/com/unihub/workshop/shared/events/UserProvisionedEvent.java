package com.unihub.workshop.shared.events;

import org.springframework.modulith.events.Externalized;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published when a user account (e.g. root ORGANIZER or provisioned
 * STAFF/ORGANIZER) is created with a system-generated temporary password.
 * Externalized to RabbitMQ queue q.notifications via routing key
 * "notifications".
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
        Instant occurredAt) {

    public UserProvisionedEvent(
            UUID userId,
            UUID organizationId,
            String orgSlug,
            String email,
            String fullName,
            String role,
            String temporaryPassword) {
        this(userId, organizationId, orgSlug, email, fullName, role, temporaryPassword, Instant.now());
    }
}
