package com.unihub.workshop.registrations.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder(toBuilder = true)
public record Registration(
    UUID id,
    UUID workshopId,
    UUID studentUserId,
    RegistrationStatus status,
    BigDecimal priceAtRegistration,
    String currency,
    UUID idempotencyKeyId,
    QrTicket qrTicket,
    String paymentClientSecret,
    Instant expiresAt,
    Instant createdAt,
    Instant updatedAt,
    UUID createdBy,
    UUID updatedBy
) {
    public static Registration createConfirmedFreeRegistration(
        UUID id,
        UUID workshopId,
        UUID studentUserId,
        UUID idempotencyKeyId,
        QrTicket qrTicket
    ) {
        Instant now = Instant.now();
        return Registration.builder()
            .id(id != null ? id : UUID.randomUUID())
            .workshopId(workshopId)
            .studentUserId(studentUserId)
            .status(RegistrationStatus.CONFIRMED)
            .priceAtRegistration(BigDecimal.ZERO)
            .currency("USD")
            .idempotencyKeyId(idempotencyKeyId)
            .qrTicket(qrTicket)
            .expiresAt(null)
            .createdAt(now)
            .updatedAt(now)
            .createdBy(studentUserId)
            .updatedBy(studentUserId)
            .build();
    }

    public static Registration createPendingPaidRegistration(
        UUID id,
        UUID workshopId,
        UUID studentUserId,
        BigDecimal price,
        String currency,
        UUID idempotencyKeyId,
        String paymentClientSecret,
        Instant expiresAt
    ) {
        Instant now = Instant.now();
        return Registration.builder()
            .id(id != null ? id : UUID.randomUUID())
            .workshopId(workshopId)
            .studentUserId(studentUserId)
            .status(RegistrationStatus.PENDING)
            .priceAtRegistration(price)
            .currency(currency != null ? currency : "USD")
            .idempotencyKeyId(idempotencyKeyId)
            .paymentClientSecret(paymentClientSecret)
            .qrTicket(null)
            .expiresAt(expiresAt)
            .createdAt(now)
            .updatedAt(now)
            .createdBy(studentUserId)
            .updatedBy(studentUserId)
            .build();
    }
}
