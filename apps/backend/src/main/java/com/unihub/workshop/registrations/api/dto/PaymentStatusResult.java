package com.unihub.workshop.registrations.api.dto;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

/**
 * Result record representing the payment status queried or updated via the payment gateway.
 */
public record PaymentStatusResult(
    UUID registrationId,
    String providerPaymentId,
    String status,
    BigDecimal amount,
    String currency,
    String failureCode,
    String failureReason
) {
    public boolean isSucceeded() {
        return "succeeded".equalsIgnoreCase(status);
    }

    public boolean isProcessing() {
        return "processing".equalsIgnoreCase(status);
    }

    public boolean isFailedOrCanceled() {
        if (status == null) {
            return false;
        }
        return switch (status.toLowerCase(Locale.ROOT)) {
            case
                "canceled",
                "failed",
                "requires_payment_method",
                "requires_confirmation",
                "requires_action" -> true;
            default -> false;
        };
    }
}
