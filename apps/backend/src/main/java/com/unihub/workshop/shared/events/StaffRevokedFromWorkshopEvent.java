package com.unihub.workshop.shared.events;

import java.time.Instant;
import java.util.UUID;
import org.springframework.modulith.events.Externalized;

/**
 * Public integration event published when a staff member's assignment to a workshop is revoked.
 * Externalized to RabbitMQ queue q.notifications via routing key "notifications".
 */
@Externalized("unihub.events::notifications")
public record StaffRevokedFromWorkshopEvent(
    UUID workshopId,
    UUID staffUserId,
    UUID organizationId,
    UUID revokedBy,
    String workshopTitle,
    Instant startsAt,
    Instant endsAt,
    String location,
    Instant revokedAt
) {
    public StaffRevokedFromWorkshopEvent(
        UUID workshopId,
        UUID staffUserId,
        UUID organizationId,
        UUID revokedBy,
        String workshopTitle,
        Instant startsAt,
        Instant endsAt,
        String location
    ) {
        this(
            workshopId,
            staffUserId,
            organizationId,
            revokedBy,
            workshopTitle,
            startsAt,
            endsAt,
            location,
            Instant.now()
        );
    }
}
