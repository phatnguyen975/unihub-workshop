package com.unihub.workshop.payments.application.ports.in;

public interface ProcessPaymentWebhookUseCase {
    /**
     * Verifies and processes an incoming Stripe webhook request.
     *
     * @param payload         the raw request body
     * @param signatureHeader the Stripe-Signature header
     */
    void processWebhook(String payload, String signatureHeader);
}
