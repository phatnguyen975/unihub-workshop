package com.unihub.workshop.workers.notifications.inbound.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.unihub.workshop.workers.notifications.core.ports.in.ConsumedNotificationEvent;
import java.time.Instant;
import java.util.UUID;

public record StaffAssignedEventDto(
    UUID assignmentId,
    UUID workshopId,
    UUID staffUserId,
    UUID organizationId,
    UUID assignedBy,
    String workshopTitle,
    Instant startsAt,
    Instant endsAt,
    String location,
    @JsonAlias({ "email", "staffEmail" }) String staffEmail,
    Instant assignedAt
) implements ConsumedNotificationEvent {
    @Override
    public String eventType() {
        return "StaffAssignedToWorkshopEvent";
    }

    @Override
    public String idempotencyKey() {
        return "STAFF_ASSIGNED:" + staffUserId + ":" + workshopId;
    }
}
