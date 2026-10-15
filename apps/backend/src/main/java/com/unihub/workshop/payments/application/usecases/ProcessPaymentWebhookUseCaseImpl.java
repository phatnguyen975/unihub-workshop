package com.unihub.workshop.payments.application.usecases;

import com.stripe.model.Event;
import com.unihub.workshop.payments.application.ports.in.ProcessPaymentWebhookUseCase;
import com.unihub.workshop.payments.application.ports.out.WebhookVerificationPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessPaymentWebhookUseCaseImpl
    implements ProcessPaymentWebhookUseCase
{

    private final WebhookVerificationPort webhookVerificationPort;

    @Override
    public void processWebhook(String payload, String signatureHeader) {
        // 1. Mandatory HMAC verification - throws BadRequestException if invalid
        Event event = webhookVerificationPort.verifyAndConstructEvent(
            payload,
            signatureHeader
        );

        // 2. Dispatch event with Always-200 protection
        String eventId = event != null ? event.getId() : "null";
        String eventType = "unknown";
        try {
            if (event != null) {
                eventType = event.getType();
                dispatch(event, eventType);
            } else {
                log.warn("Received null Stripe event");
            }
        } catch (Exception ex) {
            log.error(
                "Error processing Stripe webhook event [{}] of type [{}]: {}",
                eventId,
                eventType,
                ex.getMessage(),
                ex
            );
            // Catch all post-verification errors to prevent Stripe from flooding retry loops
        }
    }

    private void dispatch(Event event, String eventType) {
        if (eventType == null) {
            log.warn(
                "Received Stripe event [{}] with null event type",
                event.getId()
            );
            return;
        }

        log.info(
            "Processing Stripe webhook event [{}] of type [{}]",
            event.getId(),
            eventType
        );

        switch (eventType) {
            case "payment_intent.succeeded" -> {
                log.info(
                    "Received payment_intent.succeeded for event [{}]",
                    event.getId()
                );
                // Transition, ticket issuance, and outbox event handling implemented in T4
            }
            case "payment_intent.payment_failed" -> {
                log.info(
                    "Received payment_intent.payment_failed for event [{}]",
                    event.getId()
                );
                // Capacity compensation and failure transition handling implemented in T5
            }
            default -> {
                log.info(
                    "Unhandled Stripe webhook event type [{}], acknowledging without action",
                    eventType
                );
            }
        }
    }
}
