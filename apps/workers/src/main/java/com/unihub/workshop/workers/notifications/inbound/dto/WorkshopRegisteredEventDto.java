package com.unihub.workshop.workers.notifications.inbound.dto;

import com.unihub.workshop.workers.notifications.core.ports.in.ConsumedNotificationEvent;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record WorkshopRegisteredEventDto(
    UUID registrationId,
    UUID workshopId,
    UUID studentId,
    String studentEmail,
    String studentName,
    String workshopTitle,
    String workshopSlug,
    Instant startsAt,
    Instant endsAt,
    String location,
    String qrPayload,
    String qrSignature,
    Instant qrIssuedAt,
    Instant occurredAt
) implements ConsumedNotificationEvent {
    @Override
    public String eventType() {
        return "WorkshopRegisteredEvent";
    }

    @Override
    public String idempotencyKey() {
        return "WORKSHOP_REGISTERED:" + registrationId;
    }
}
