package com.unihub.workshop.registrations.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Command to persist an initial PENDING payment record for an initiated checkout session.
 * Used by the registration strategy to record payment provider references for reconciliation.
 *
 * @param registrationId registration unique identifier
 * @param providerPaymentId external payment identifier (e.g. Stripe PaymentIntent ID)
 * @param amount payment amount in major units
 * @param currency ISO 4217 currency code
 */
public record RecordPendingPaymentCommand(
    UUID registrationId,
    String providerPaymentId,
    BigDecimal amount,
    String currency
) {}
