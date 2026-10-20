package com.unihub.workshop.registrations.api.dto;

import java.math.BigDecimal;

/**
 * Result representing the outcome of a refund request processed by the payment gateway.
 *
 * @param providerRefundId external refund identifier from the provider
 * @param status refund status from provider (e.g. succeeded, pending, failed)
 * @param amount refunded amount
 * @param currency ISO 4217 currency code
 */
public record RefundPaymentResult(
    String providerRefundId,
    String status,
    BigDecimal amount,
    String currency
) {
    public boolean isSucceeded() {
        return "succeeded".equalsIgnoreCase(status);
    }
}
