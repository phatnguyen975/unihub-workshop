package com.unihub.workshop.registrations.api;

import com.unihub.workshop.registrations.api.dto.CreatePaymentIntentCommand;
import com.unihub.workshop.registrations.api.dto.PaymentIntentResult;
import com.unihub.workshop.registrations.api.dto.PaymentStatusResult;
import com.unihub.workshop.registrations.api.dto.RecordPendingPaymentCommand;
import com.unihub.workshop.registrations.api.dto.RefundPaymentCommand;
import com.unihub.workshop.registrations.api.dto.RefundPaymentResult;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound SPI port for initiating payment checkout sessions with external payment providers.
 * Implemented by payment provider adapters (e.g. Stripe in the payments module).
 */
public interface RegistrationPaymentPort {
    /**
     * Creates a payment intent for a paid workshop registration.
     *
     * @param command payment intent details
     * @return result containing external payment intent ID and client secret
     */
    PaymentIntentResult createPaymentIntent(CreatePaymentIntentCommand command);

    /**
     * Persists an initial PENDING payment record for the created payment intent.
     *
     * @param command pending payment details
     */
    void recordPendingPayment(RecordPendingPaymentCommand command);

    /**
     * Queries the payment gateway for the real payment outcome of a registration.
     *
     * @param registrationId registration ID
     * @return payment status result if a payment record exists, empty otherwise
     */
    Optional<PaymentStatusResult> getPaymentStatus(UUID registrationId);

    /**
     * Requests the payment gateway to cancel an outstanding payment attempt.
     *
     * @param providerPaymentId external payment identifier
     * @return payment status result representing the final or updated status
     */
    PaymentStatusResult cancelPaymentAttempt(String providerPaymentId);

    /**
     * Atomically marks the payment record as COMPLETED.
     *
     * @param registrationId registration ID
     * @param providerPaymentId external payment identifier
     * @param amount payment amount
     * @param currency payment currency
     */
    void markPaymentCompleted(
        UUID registrationId,
        String providerPaymentId,
        BigDecimal amount,
        String currency
    );

    /**
     * Atomically marks the payment record as FAILED.
     *
     * @param registrationId registration ID
     * @param failureReason description of failure
     */
    void markPaymentFailed(UUID registrationId, String failureReason);

    /**
     * Checks if an associated payment record exists in COMPLETED status for a registration.
     *
     * @param registrationId registration ID
     * @return true if an associated COMPLETED payment record exists, false otherwise
     */
    boolean hasCompletedPayment(UUID registrationId);

    /**
     * Requests a 100% refund from the payment gateway outside database transactions.
     * Uses a deterministic idempotency key to prevent duplicate refunds across client retries.
     *
     * @param command refund command details
     * @return result containing external refund identifier, status, amount, and currency
     */
    RefundPaymentResult refundPayment(RefundPaymentCommand command);

    /**
     * Atomically transitions the associated payment record to REFUNDED status.
     *
     * @param registrationId registration ID
     * @param providerRefundId refund identifier returned by the payment provider
     * @param amount refunded amount
     * @param currency refunded currency
     */
    void markPaymentRefunded(
        UUID registrationId,
        String providerRefundId,
        BigDecimal amount,
        String currency
    );
}
