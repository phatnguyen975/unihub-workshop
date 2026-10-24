package com.unihub.workshop.payments.presentation.dto;

/**
 * Payment summary for one currency, with every status bucket present.
 *
 * @param currency ISO 4217 currency code
 * @param pending pending-payment bucket
 * @param completed completed-payment bucket
 * @param failed failed-payment bucket
 * @param refunded refunded-payment bucket
 */
public record CurrencyPaymentSummaryResponse(
    String currency,
    PaymentSummaryBucketResponse pending,
    PaymentSummaryBucketResponse completed,
    PaymentSummaryBucketResponse failed,
    PaymentSummaryBucketResponse refunded
) {}
