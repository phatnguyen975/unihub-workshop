package com.unihub.workshop.payments.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentHistoryItemResponse(
    UUID paymentId,
    PaymentRegistrationContextResponse registration,
    BigDecimal amount,
    String currency,
    String status,
    String providerReferenceMasked,
    Instant statusOccurredAt,
    Instant createdAt,
    Instant updatedAt
) {}
