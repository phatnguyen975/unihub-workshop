package com.unihub.workshop.payments.application.ports.out;

import com.stripe.model.Event;

public interface WebhookVerificationPort {
    /**
     * Verifies the HMAC-SHA256 signature of the raw payload and deserializes the verified content.
     *
     * @param payload         the raw HTTP request body string
     * @param signatureHeader the Stripe-Signature header value
     * @return the verified Stripe event
     */
    Event verifyAndConstructEvent(String payload, String signatureHeader);
}
