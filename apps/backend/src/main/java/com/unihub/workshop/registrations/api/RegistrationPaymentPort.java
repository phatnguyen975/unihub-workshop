package com.unihub.workshop.registrations.api;

import com.unihub.workshop.registrations.api.dto.CreatePaymentIntentCommand;
import com.unihub.workshop.registrations.api.dto.PaymentIntentResult;
import com.unihub.workshop.registrations.api.dto.PaymentStatusResult;
import com.unihub.workshop.registrations.api.dto.RecordPendingPaymentCommand;
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
}
