package com.unihub.workshop.workers.notifications.inbound.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.unihub.workshop.workers.notifications.core.ports.in.ConsumedNotificationEvent;
import java.time.Instant;
import java.util.UUID;

public record StaffRevokedEventDto(
    UUID workshopId,
    UUID staffUserId,
    UUID organizationId,
    UUID revokedBy,
    String workshopTitle,
    Instant startsAt,
    Instant endsAt,
    String location,
    @JsonAlias({ "email", "staffEmail" }) String staffEmail,
    Instant revokedAt
) implements ConsumedNotificationEvent {
    @Override
    public String eventType() {
        return "StaffRevokedFromWorkshopEvent";
    }

    @Override
    public String idempotencyKey() {
        return "STAFF_REVOKED:" + staffUserId + ":" + workshopId;
    }
}
