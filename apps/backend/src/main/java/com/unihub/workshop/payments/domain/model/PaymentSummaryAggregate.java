package com.unihub.workshop.payments.domain.model;

import java.math.BigDecimal;

/**
 * One currency/status row produced by the organization-scoped payment aggregate.
 *
 * <p>Only currencies and statuses with at least one matching payment are returned; the
 * application layer expands these rows into the fixed four-bucket currency summary.
 *
 * @param currency ISO 4217 currency code
 * @param status payment status represented by this row
 * @param count number of matching payments
 * @param amount sum of matching payment amounts
 */
public record PaymentSummaryAggregate(
    String currency,
    PaymentStatus status,
    long count,
    BigDecimal amount
) {}
