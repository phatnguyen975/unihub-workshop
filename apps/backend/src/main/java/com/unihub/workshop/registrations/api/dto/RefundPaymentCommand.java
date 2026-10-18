package com.unihub.workshop.registrations.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Command representing a request to issue a refund via the payment gateway.
 *
 * @param registrationId the registration to refund
 * @param amount the amount to refund (100% of price at registration)
 * @param currency ISO 4217 currency code
 * @param idempotencyKey deterministic idempotency key for gateway deduplication
 * @param reason optional reason for refund
 */
public record RefundPaymentCommand(
    UUID registrationId,
    BigDecimal amount,
    String currency,
    String idempotencyKey,
    String reason
) {}
