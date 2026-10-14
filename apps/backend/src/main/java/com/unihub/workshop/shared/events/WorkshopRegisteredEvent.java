package com.unihub.workshop.shared.events;

import java.time.Instant;
import java.util.UUID;
import org.springframework.modulith.events.Externalized;

/**
 * Public integration event published when a workshop registration is confirmed.
 * Externalized to RabbitMQ exchange unihub.events via routing key "workshop.registered".
 * <p>
 * Self-contained fat event carrying student and workshop snapshots so workers require zero
 * synchronous callbacks to backend.
 */
@Externalized("unihub.events::workshop.registered")
public record WorkshopRegisteredEvent(
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
) {
    public WorkshopRegisteredEvent(
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
        Instant qrIssuedAt
    ) {
        this(
            registrationId,
            workshopId,
            studentId,
            studentEmail,
            studentName,
            workshopTitle,
            workshopSlug,
            startsAt,
            endsAt,
            location,
            qrPayload,
            qrSignature,
            qrIssuedAt,
            Instant.now()
        );
    }
}
