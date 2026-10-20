package com.unihub.workshop.registrations.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RegistrationResponse(
    UUID id,
    UUID workshopId,
    UUID studentId,
    String status,
    BigDecimal priceAtRegistration,
    String currency,
    String paymentClientSecret,
    String qrPayload,
    String qrSignature,
    Instant qrIssuedAt,
    Instant createdAt,
    Instant updatedAt,
    RefundDto refund
) {
    public record RefundDto(BigDecimal amount, String currency) {}
}
