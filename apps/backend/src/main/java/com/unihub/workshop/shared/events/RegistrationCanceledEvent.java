package com.unihub.workshop.shared.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.modulith.events.Externalized;

/**
 * Public integration event published when a workshop registration is canceled.
 * Externalized to RabbitMQ exchange unihub.events via routing key "registration.canceled".
 * <p>
 * Self-contained fat event carrying student and workshop snapshots, optional reason,
 * canceling actor role, and refund details for worker consumption.
 */
@Externalized("unihub.events::registration.canceled")
public record RegistrationCanceledEvent(
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
) {
    public RegistrationCanceledEvent(
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
        String currency
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
            reason,
            canceledByRole,
            refundAmount,
            currency,
            Instant.now()
        );
    }
}
