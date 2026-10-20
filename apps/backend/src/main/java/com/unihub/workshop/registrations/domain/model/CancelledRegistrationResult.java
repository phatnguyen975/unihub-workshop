package com.unihub.workshop.registrations.domain.model;

import java.math.BigDecimal;

/**
 * Domain result representing a successful registration cancellation.
 *
 * @param registration the updated Registration aggregate in CANCELED status
 * @param refundAmount the refunded amount (null for free registrations)
 * @param refundCurrency the refund currency (null for free registrations)
 */
public record CancelledRegistrationResult(
    Registration registration,
    BigDecimal refundAmount,
    String refundCurrency
) {}
