package com.unihub.workshop.payments.application.usecases;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.Stripe;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeError;
import com.stripe.model.StripeObject;
import com.unihub.workshop.payments.application.ports.in.ProcessPaymentWebhookUseCase;
import com.unihub.workshop.payments.application.ports.out.PaymentRepository;
import com.unihub.workshop.payments.application.ports.out.WebhookVerificationPort;
import com.unihub.workshop.payments.domain.model.Payment;
import com.unihub.workshop.payments.domain.model.PaymentStatus;
import com.unihub.workshop.registrations.api.RegistrationApi;
import com.unihub.workshop.registrations.api.dto.ConfirmPaidRegistrationCommand;
import com.unihub.workshop.registrations.api.dto.FailPaidRegistrationCommand;
import com.unihub.workshop.registrations.api.dto.RegistrationConfirmedDto;
import com.unihub.workshop.shared.realtime.api.RealtimeEvent;
import com.unihub.workshop.shared.realtime.api.RealtimeEventPublisher;
import com.unihub.workshop.shared.realtime.api.RealtimeEventType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            }
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

        // Idempotency check: skip only if already in a terminal state
        Optional<Payment> existingPayment =
            paymentRepository.findByProviderPaymentId(providerPaymentId);
        if (
            existingPayment.isPresent() &&
            existingPayment.get().status() != PaymentStatus.PENDING
        ) {
            log.info(
                "PaymentIntent [{}] already in terminal state [{}], skipping duplicate event",
                providerPaymentId,
                existingPayment.get().status()
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

        // Update existing PENDING record to COMPLETED, or insert a new COMPLETED record as fallback
        Payment completedPayment = existingPayment
            .map(p ->
                p
                    .toBuilder()
                    .status(PaymentStatus.COMPLETED)
                    .amount(amount)
                    .currency(currency.toUpperCase(java.util.Locale.ROOT))
                    .completedAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build()
            )
            .orElseGet(() ->
                Payment.createCompleted(
                    UUID.randomUUID(),
                    registrationId,
                    "STRIPE",
                    providerPaymentId,
                    amount,
                    currency,
                    Instant.now()
                )
            );

        paymentRepository.save(completedPayment);
        log.info(
            "Successfully recorded COMPLETED payment [{}] for registration [{}]",
            providerPaymentId,
            registrationId
        );

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

        // Idempotency check: skip only if already in a terminal state
        Optional<Payment> existingPayment =
            paymentRepository.findByProviderPaymentId(providerPaymentId);
        if (
            existingPayment.isPresent() &&
            existingPayment.get().status() != PaymentStatus.PENDING
        ) {
            log.info(
                "PaymentIntent [{}] already in terminal state [{}], skipping duplicate failed event",
                providerPaymentId,
                existingPayment.get().status()
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

        // Update existing PENDING record to FAILED, or insert a new FAILED record as fallback
        final String resolvedCode = failureCode;
        final String resolvedReason = failureReason;
        Payment failedPayment = existingPayment
            .map(p ->
                p
                    .toBuilder()
                    .status(PaymentStatus.FAILED)
                    .failureCode(resolvedCode)
                    .failureReason(resolvedReason)
                    .failedAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build()
            )
            .orElseGet(() ->
                Payment.createFailed(
                    UUID.randomUUID(),
                    registrationId,
                    "STRIPE",
                    providerPaymentId,
                    amount,
                    currency,
                    resolvedCode,
                    resolvedReason,
                    Instant.now()
                )
            );

        paymentRepository.save(failedPayment);
        log.info(
            "Successfully recorded FAILED payment [{}] for registration [{}]",
            providerPaymentId,
            registrationId
        );
    }

    private PaymentIntent extractPaymentIntent(Event event) {
        if (event == null) {
            return null;
        }

        if (event.getApiVersion() == null) {
            try {
                event.setApiVersion(Stripe.API_VERSION);
            } catch (Exception ignored) {}
        }

        EventDataObjectDeserializer deserializer = null;
        try {
            deserializer = event.getDataObjectDeserializer();
        } catch (Exception e) {
            log.warn(
                "Unable to obtain EventDataObjectDeserializer for event [{}]: {}",
                event.getId(),
                e.getMessage()
            );
        }

        if (deserializer != null) {
            try {
                Optional<StripeObject> stripeObject = deserializer.getObject();
                if (
                    stripeObject.isPresent() &&
                    stripeObject.get() instanceof PaymentIntent pi
                ) {
                    return pi;
                }
            } catch (Exception e) {
                log.warn(
                    "Error calling getObject() on Stripe event [{}]: {}",
                    event.getId(),
                    e.getMessage()
                );
            }

            try {
                StripeObject unsafeObject = deserializer.deserializeUnsafe();
                if (unsafeObject instanceof PaymentIntent pi) {
                    return pi;
                }
            } catch (Exception e) {
                log.warn(
                    "Unable to deserialize Stripe object using deserializeUnsafe for event [{}]: {}",
                    event.getId(),
                    e.getMessage()
                );
            }

            if (deserializer.getRawJson() != null) {
                PaymentIntent fallbackPi = parsePaymentIntentFromJson(
                    deserializer.getRawJson()
                );
                if (fallbackPi != null) {
                    return fallbackPi;
                }
            }
        }

        return null;
    }

    private PaymentIntent parsePaymentIntentFromJson(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return null;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(rawJson);
            if (root == null || !root.isObject()) {
                return null;
            }
            PaymentIntent pi = new PaymentIntent();
            if (root.hasNonNull("id")) {
                pi.setId(root.get("id").asText());
            }
            if (root.hasNonNull("amount")) {
                pi.setAmount(root.get("amount").asLong());
            }
            if (root.hasNonNull("currency")) {
                pi.setCurrency(root.get("currency").asText());
            }
            if (root.has("metadata") && root.get("metadata").isObject()) {
                Map<String, String> meta = new HashMap<>();
                root.get("metadata")
                    .properties()
                    .forEach(entry -> {
                        if (
                            entry.getValue() != null &&
                            !entry.getValue().isNull()
                        ) {
                            meta.put(entry.getKey(), entry.getValue().asText());
                        }
                    });
                pi.setMetadata(meta);
            }
            if (
                root.has("last_payment_error") &&
                root.get("last_payment_error").isObject()
            ) {
                JsonNode errNode = root.get("last_payment_error");
                StripeError stripeError = new StripeError();
                if (errNode.hasNonNull("code")) {
                    stripeError.setCode(errNode.get("code").asText());
                }
                if (errNode.hasNonNull("message")) {
                    stripeError.setMessage(errNode.get("message").asText());
                }
                pi.setLastPaymentError(stripeError);
            }
            return pi;
        } catch (Exception ex) {
            log.debug(
                "Fallback parsing of PaymentIntent failed: {}",
                ex.getMessage()
            );
            return null;
        }
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

                // 2. registration.updated to workshop's stream: unihub:stream:workshop:{workshopId} with 6-field seat count snapshot
                registrationApi.broadcastSeatCountSnapshot(
                    confirmedDto.workshopId()
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
