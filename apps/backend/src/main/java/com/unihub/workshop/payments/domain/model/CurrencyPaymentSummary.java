package com.unihub.workshop.payments.domain.model;

/**
 * Operational payment summary for one currency.
 *
 * <p>Every status bucket is always present; a status with no matching payments is
 * represented by {@link PaymentSummaryBucket#empty()}.
 *
 * @param currency ISO 4217 currency code
 * @param pending pending-payment bucket
 * @param completed completed-payment bucket
 * @param failed failed-payment bucket
 * @param refunded refunded-payment bucket
 */
public record CurrencyPaymentSummary(
    String currency,
    PaymentSummaryBucket pending,
    PaymentSummaryBucket completed,
    PaymentSummaryBucket failed,
    PaymentSummaryBucket refunded
) {}
