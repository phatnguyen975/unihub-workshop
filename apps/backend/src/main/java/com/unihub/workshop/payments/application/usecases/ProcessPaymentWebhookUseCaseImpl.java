package com.unihub.workshop.payments.application.usecases;

import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.unihub.workshop.payments.application.ports.in.ProcessPaymentWebhookUseCase;
import com.unihub.workshop.payments.application.ports.out.PaymentRepository;
import com.unihub.workshop.payments.application.ports.out.WebhookVerificationPort;
import com.unihub.workshop.payments.domain.model.Payment;
import com.unihub.workshop.registrations.api.RegistrationApi;
import com.unihub.workshop.registrations.api.dto.ConfirmPaidRegistrationCommand;
import com.unihub.workshop.registrations.api.dto.FailPaidRegistrationCommand;
import com.unihub.workshop.registrations.api.dto.RegistrationConfirmedDto;
import com.unihub.workshop.shared.realtime.api.RealtimeEvent;
import com.unihub.workshop.shared.realtime.api.RealtimeEventPublisher;
import com.unihub.workshop.shared.realtime.api.RealtimeEventType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Service implementing {@link ProcessPaymentWebhookUseCase}.
 * Routes verified Stripe webhook events to appropriate business handlers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessPaymentWebhookUseCaseImpl
    implements ProcessPaymentWebhookUseCase
{

    private final WebhookVerificationPort webhookVerificationPort;
    private final PaymentRepository paymentRepository;
    private final RegistrationApi registrationApi;
    private final RealtimeEventPublisher realtimeEventPublisher;

    @Override
    @Transactional
    public void processWebhook(String payload, String signatureHeader) {
        // 1. Mandatory HMAC verification - throws BadRequestException (400) if invalid
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
            case "payment_intent.succeeded" -> handlePaymentIntentSucceeded(
                event
            );
            case "payment_intent.payment_failed" -> handlePaymentIntentFailed(
                event
            );
            default -> {
                log.info(
                    "Unhandled Stripe webhook event type [{}], acknowledging without action",
                    eventType
                );
            }
        }
    }

    private void handlePaymentIntentSucceeded(Event event) {
        PaymentIntent paymentIntent = extractPaymentIntent(event);
        if (paymentIntent == null) {
            log.warn(
                "Stripe event [{}] data object could not be resolved to a PaymentIntent",
                event.getId()
            );
            return;
        }

        String providerPaymentId = paymentIntent.getId();
        if (providerPaymentId == null || providerPaymentId.isBlank()) {
            log.warn(
                "Stripe event [{}] PaymentIntent has no ID",
                event.getId()
            );
            return;
        }

        Map<String, String> metadata = paymentIntent.getMetadata();
        String registrationIdStr =
            metadata != null ? metadata.get("registrationId") : null;
        if (registrationIdStr == null || registrationIdStr.isBlank()) {
            log.warn(
                "Stripe PaymentIntent [{}] has no registrationId in metadata. Acknowledging without state change.",
                providerPaymentId
            );
            return;
        }

        UUID registrationId;
        try {
            registrationId = UUID.fromString(registrationIdStr);
        } catch (IllegalArgumentException ex) {
            log.warn(
                "Stripe PaymentIntent [{}] has invalid UUID for registrationId [{}]: {}",
                providerPaymentId,
                registrationIdStr,
                ex.getMessage()
            );
            return;
        }

        // Idempotency check
        if (paymentRepository.existsByProviderPaymentId(providerPaymentId)) {
            log.info(
                "PaymentIntent [{}] already processed, skipping duplicate event",
                providerPaymentId
            );
            return;
        }

        BigDecimal amount = Payment.convertFromSmallestUnit(
            paymentIntent.getAmount() != null ? paymentIntent.getAmount() : 0L,
            paymentIntent.getCurrency()
        );
        String currency =
            paymentIntent.getCurrency() != null
                ? paymentIntent.getCurrency()
                : "USD";

        ConfirmPaidRegistrationCommand command =
            new ConfirmPaidRegistrationCommand(
                registrationId,
                providerPaymentId
            );
        RegistrationConfirmedDto confirmedDto =
            registrationApi.confirmPaidRegistration(command);

        if (confirmedDto == null) {
            log.warn(
                "Registration [{}] was not confirmed (not found or not PENDING). Payment [{}] not recorded.",
                registrationId,
                providerPaymentId
            );
            return;
        }

        Payment payment = Payment.createCompleted(
            UUID.randomUUID(),
            registrationId,
            "STRIPE",
            providerPaymentId,
            amount,
            currency,
            Instant.now()
        );

        try {
            paymentRepository.save(payment);
            log.info(
                "Successfully recorded COMPLETED payment [{}] for registration [{}]",
                providerPaymentId,
                registrationId
            );
        } catch (DataIntegrityViolationException ex) {
            log.info(
                "Duplicate payment record detected for providerPaymentId [{}], ignoring constraint violation",
                providerPaymentId
            );
            return;
        }

        registerPostCommitRealtimeEvents(confirmedDto);
    }

    private void handlePaymentIntentFailed(Event event) {
        PaymentIntent paymentIntent = extractPaymentIntent(event);
        if (paymentIntent == null) {
            log.warn(
                "Stripe event [{}] data object could not be resolved to a PaymentIntent",
                event.getId()
            );
            return;
        }

        String providerPaymentId = paymentIntent.getId();
        if (providerPaymentId == null || providerPaymentId.isBlank()) {
            log.warn(
                "Stripe event [{}] PaymentIntent has no ID",
                event.getId()
            );
            return;
        }

        Map<String, String> metadata = paymentIntent.getMetadata();
        String registrationIdStr =
            metadata != null ? metadata.get("registrationId") : null;
        if (registrationIdStr == null || registrationIdStr.isBlank()) {
            log.warn(
                "Stripe PaymentIntent [{}] has no registrationId in metadata. Acknowledging without state change.",
                providerPaymentId
            );
            return;
        }

        UUID registrationId;
        try {
            registrationId = UUID.fromString(registrationIdStr);
        } catch (IllegalArgumentException ex) {
            log.warn(
                "Stripe PaymentIntent [{}] has invalid UUID for registrationId [{}]: {}",
                providerPaymentId,
                registrationIdStr,
                ex.getMessage()
            );
            return;
        }

        // Idempotency check (BR-07, FR-22)
        if (paymentRepository.existsByProviderPaymentId(providerPaymentId)) {
            log.info(
                "PaymentIntent [{}] already processed, skipping duplicate failed event",
                providerPaymentId
            );
            return;
        }

        String failureCode = "payment_failed";
        String failureReason = "PaymentIntent payment failed";
        if (paymentIntent.getLastPaymentError() != null) {
            if (paymentIntent.getLastPaymentError().getCode() != null) {
                failureCode = paymentIntent.getLastPaymentError().getCode();
            }
            if (paymentIntent.getLastPaymentError().getMessage() != null) {
                failureReason = paymentIntent
                    .getLastPaymentError()
                    .getMessage();
            }
        }

        BigDecimal amount = Payment.convertFromSmallestUnit(
            paymentIntent.getAmount() != null ? paymentIntent.getAmount() : 0L,
            paymentIntent.getCurrency()
        );
        String currency =
            paymentIntent.getCurrency() != null
                ? paymentIntent.getCurrency()
                : "USD";

        FailPaidRegistrationCommand command = new FailPaidRegistrationCommand(
            registrationId,
            failureReason
        );
        boolean failed = registrationApi.failPaidRegistration(command);

        if (!failed) {
            log.warn(
                "Registration [{}] was not failed (not found or not PENDING). Payment [{}] not recorded.",
                registrationId,
                providerPaymentId
            );
            return;
        }

        Payment payment = Payment.createFailed(
            UUID.randomUUID(),
            registrationId,
            "STRIPE",
            providerPaymentId,
            amount,
            currency,
            failureCode,
            failureReason,
            Instant.now()
        );

        try {
            paymentRepository.save(payment);
            log.info(
                "Successfully recorded FAILED payment [{}] for registration [{}]",
                providerPaymentId,
                registrationId
            );
        } catch (DataIntegrityViolationException ex) {
            log.info(
                "Duplicate payment record detected for providerPaymentId [{}], ignoring constraint violation",
                providerPaymentId
            );
        }
    }

    private PaymentIntent extractPaymentIntent(Event event) {
        EventDataObjectDeserializer deserializer =
            event.getDataObjectDeserializer();
        if (deserializer != null) {
            Optional<StripeObject> stripeObject = deserializer.getObject();
            if (
                stripeObject.isPresent() &&
                stripeObject.get() instanceof PaymentIntent pi
            ) {
                return pi;
            }
            try {
                StripeObject unsafeObject = deserializer.deserializeUnsafe();
                if (unsafeObject instanceof PaymentIntent pi) {
                    return pi;
                }
            } catch (Exception e) {
                log.warn(
                    "Unable to deserialize Stripe object for event [{}]: {}",
                    event.getId(),
                    e.getMessage()
                );
            }
        }

        return null;
    }

    private void registerPostCommitRealtimeEvents(
        RegistrationConfirmedDto confirmedDto
    ) {
        Runnable publishEvents = () -> {
            try {
                // 1. payment.confirmed to student's personal stream: unihub:stream:user:{studentUserId}
                Map<String, Object> userPayload = Map.of(
                    "registrationId",
                    confirmedDto.registrationId(),
                    "workshopId",
                    confirmedDto.workshopId(),
                    "qrPayload",
                    confirmedDto.qrPayload() != null
                        ? confirmedDto.qrPayload()
                        : "",
                    "qrIssuedAt",
                    confirmedDto.qrIssuedAt() != null
                        ? confirmedDto.qrIssuedAt().toString()
                        : ""
                );
                RealtimeEvent<Map<String, Object>> userEvent = RealtimeEvent.of(
                    RealtimeEventType.PAYMENT_CONFIRMED,
                    userPayload
                );
                realtimeEventPublisher.publishToUser(
                    confirmedDto.studentUserId(),
                    userEvent
                );

                // 2. registration.updated to workshop's stream: unihub:stream:workshop:{workshopId}
                Map<String, Object> workshopPayload = Map.of(
                    "workshopId",
                    confirmedDto.workshopId(),
                    "status",
                    "CONFIRMED"
                );
                RealtimeEvent<Map<String, Object>> workshopEvent =
                    RealtimeEvent.of(
                        RealtimeEventType.REGISTRATION_UPDATED,
                        workshopPayload
                    );
                realtimeEventPublisher.publishToWorkshop(
                    confirmedDto.workshopId(),
                    workshopEvent
                );
            } catch (Exception ex) {
                log.error(
                    "Failed to publish post-commit realtime events for registration [{}]: {}",
                    confirmedDto.registrationId(),
                    ex.getMessage(),
                    ex
                );
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        publishEvents.run();
                    }
                }
            );
        } else {
            publishEvents.run();
        }
    }
}
