package com.unihub.workshop.payments.infrastructure.gateway;

import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import com.unihub.workshop.payments.application.ports.out.WebhookVerificationPort;
import com.unihub.workshop.shared.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Validates Stripe webhook signatures using HMAC-SHA256 and deserializes the verified payload into an Event.
 */
@Slf4j
@Component
public class StripeWebhookVerifier implements WebhookVerificationPort {

    private final String webhookSecret;

    public StripeWebhookVerifier(
        @Value("${stripe.webhook-secret:whsec_dummy}") String webhookSecret
    ) {
        this.webhookSecret = webhookSecret;
    }

    /**
     * Verifies the raw request payload against the Stripe-Signature header using the configured webhook secret.
     *
     * @param payload         the raw request body bytes converted to string
     * @param signatureHeader the value of the Stripe-Signature header
     * @return the verified and parsed Stripe Event
     * @throws BadRequestException if the header is missing, malformed, expired, or signature does not match
     */
    @Override
    public Event verifyAndConstructEvent(
        String payload,
        String signatureHeader
    ) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            log.warn("Missing Stripe-Signature header in webhook request");
            throw new BadRequestException("Missing Stripe-Signature header");
        }
        if (payload == null) {
            log.warn("Empty payload in webhook request");
            throw new BadRequestException("Missing webhook payload");
        }

        try {
            Event event = Webhook.constructEvent(
                payload,
                signatureHeader,
                webhookSecret
            );
            if (event != null && event.getApiVersion() == null) {
                event.setApiVersion(Stripe.API_VERSION);
            }
            return event;
        } catch (SignatureVerificationException e) {
            log.warn(
                "Stripe webhook signature verification failed: {}",
                e.getMessage()
            );
            throw new BadRequestException(
                "Invalid webhook signature: " + e.getMessage()
            );
        }
    }
}
