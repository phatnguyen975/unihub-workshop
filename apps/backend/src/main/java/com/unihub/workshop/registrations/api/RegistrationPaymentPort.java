package com.unihub.workshop.registrations.api;

import com.unihub.workshop.registrations.api.dto.CreatePaymentIntentCommand;
import com.unihub.workshop.registrations.api.dto.PaymentIntentResult;

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
}
