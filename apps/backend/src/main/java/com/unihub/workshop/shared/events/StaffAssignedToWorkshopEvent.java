package com.unihub.workshop.shared.events;

import java.time.Instant;
import java.util.UUID;
import org.springframework.modulith.events.Externalized;

/**
 * Public integration event published when a staff member is assigned to a workshop.
 * Externalized to RabbitMQ queue q.notifications via routing key "notifications".
 */
@Externalized("unihub.events::notifications")
public record StaffAssignedToWorkshopEvent(
    UUID assignmentId,
    UUID workshopId,
    UUID staffUserId,
    UUID organizationId,
    UUID assignedBy,
    String workshopTitle,
    Instant startsAt,
    Instant endsAt,
    String location,
    Instant assignedAt
) {
    public StaffAssignedToWorkshopEvent(
        UUID assignmentId,
        UUID workshopId,
        UUID staffUserId,
        UUID organizationId,
        UUID assignedBy,
        String workshopTitle,
        Instant startsAt,
        Instant endsAt,
        String location
    ) {
        this(
            assignmentId,
            workshopId,
            staffUserId,
            organizationId,
            assignedBy,
            workshopTitle,
            startsAt,
            endsAt,
            location,
            Instant.now()
        );
    }
}
