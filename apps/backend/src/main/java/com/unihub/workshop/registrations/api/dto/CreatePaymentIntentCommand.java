package com.unihub.workshop.registrations.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Command payload sent across module boundary to request creation of a payment intent.
 *
 * @param registrationId target registration ID
 * @param workshopId workshop being registered for
 * @param amount price to charge
 * @param currency ISO-4217 currency code (e.g. "USD")
 * @param idempotencyKey idempotency key string for deduplication
 */
public record CreatePaymentIntentCommand(
    UUID registrationId,
    UUID workshopId,
    BigDecimal amount,
    String currency,
    String idempotencyKey
) {}
