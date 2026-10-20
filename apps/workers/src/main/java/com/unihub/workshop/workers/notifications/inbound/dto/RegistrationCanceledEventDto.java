package com.unihub.workshop.workers.notifications.inbound.dto;

import com.unihub.workshop.workers.notifications.core.ports.in.ConsumedNotificationEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record RegistrationCanceledEventDto(
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
    String reason,
    String canceledByRole,
    BigDecimal refundAmount,
    String currency,
    Instant occurredAt
) implements ConsumedNotificationEvent {
    @Override
    public String eventType() {
        return "RegistrationCanceledEvent";
    }

    @Override
    public String idempotencyKey() {
        return "REGISTRATION_CANCELED:" + registrationId;
    }
}
