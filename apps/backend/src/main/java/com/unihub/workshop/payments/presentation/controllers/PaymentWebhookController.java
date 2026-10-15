package com.unihub.workshop.payments.presentation.controllers;

import com.unihub.workshop.payments.application.ports.in.ProcessPaymentWebhookUseCase;
import com.unihub.workshop.shared.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing webhook endpoint for Stripe payment events.
 */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final ProcessPaymentWebhookUseCase processPaymentWebhookUseCase;

    /**
     * Handles incoming Stripe webhooks.
     * Returns 200 OK for any request that passes HMAC signature verification.
     *
     * @param payload         raw request body preserving original bytes
     * @param signatureHeader Stripe-Signature header containing timestamp and HMAC
     * @return 200 OK acknowledgment envelope
     */
    @PostMapping("/webhook")
    public ApiResponse<Void> handleWebhook(
        @RequestBody String payload,
        @RequestHeader(
            value = "Stripe-Signature",
            required = false
        ) String signatureHeader
    ) {
        processPaymentWebhookUseCase.processWebhook(payload, signatureHeader);
        return ApiResponse.success(null, "Webhook received.");
    }
}
