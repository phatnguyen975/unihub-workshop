package com.unihub.workshop.registrations.application.strategy;

import com.unihub.workshop.iam.api.dto.StudentProfileDto;
import com.unihub.workshop.registrations.api.RegistrationPaymentPort;
import com.unihub.workshop.registrations.api.dto.CreatePaymentIntentCommand;
import com.unihub.workshop.registrations.api.dto.PaymentIntentResult;
import com.unihub.workshop.registrations.api.dto.RecordPendingPaymentCommand;
import com.unihub.workshop.registrations.application.usecases.PendingPaidRegistrationPersistenceService;
import com.unihub.workshop.registrations.domain.model.Registration;
import com.unihub.workshop.workshops.api.dto.WorkshopRegistrationDetailsDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Concrete registration strategy for paid workshops (price > 0).
 * Creates a Stripe Payment Intent without holding a database transaction,
 * then persists a PENDING registration with an expiration timestamp.
 */
@Slf4j
@Component
public class PaidRegistrationStrategy implements RegistrationStrategy {

    private final RegistrationPaymentPort registrationPaymentPort;
    private final PendingPaidRegistrationPersistenceService persistenceService;
    private final long pendingTtlMinutes;

    public PaidRegistrationStrategy(
        RegistrationPaymentPort registrationPaymentPort,
        PendingPaidRegistrationPersistenceService persistenceService,
        @Value(
            "${app.registrations.pending-ttl-minutes:30}"
        ) long pendingTtlMinutes
    ) {
        this.registrationPaymentPort = registrationPaymentPort;
        this.persistenceService = persistenceService;
        this.pendingTtlMinutes = pendingTtlMinutes;
    }

    @Override
    public boolean supports(WorkshopRegistrationDetailsDto workshop) {
        return (
            workshop != null &&
            workshop.price() != null &&
            workshop.price().compareTo(BigDecimal.ZERO) > 0
        );
    }

    @Override
    public Registration register(
        StudentProfileDto student,
        WorkshopRegistrationDetailsDto workshop,
        UUID idempotencyKeyId
    ) {
        UUID registrationId = UUID.randomUUID();

        // 1. Transaction-free Payment Intent creation
        String idempotencyKey =
            idempotencyKeyId != null ? idempotencyKeyId.toString() : null;
        String currency = "USD";

        CreatePaymentIntentCommand command = new CreatePaymentIntentCommand(
            registrationId,
            workshop.id(),
            workshop.price(),
            currency,
            idempotencyKey
        );

        PaymentIntentResult paymentIntent =
            registrationPaymentPort.createPaymentIntent(command);

        // 2. Calculate PENDING registration expiration TTL
        Instant expiresAt = Instant.now().plus(
            pendingTtlMinutes,
            ChronoUnit.MINUTES
        );

        // 3. Persist PENDING registration record
        Registration pendingRegistration =
            Registration.createPendingPaidRegistration(
                registrationId,
                workshop.id(),
                student.userId(),
                workshop.price(),
                currency,
                idempotencyKeyId,
                paymentIntent.clientSecret(),
                expiresAt
            );

        Registration saved = persistenceService.persist(
            pendingRegistration,
            new RecordPendingPaymentCommand(
                registrationId,
                paymentIntent.paymentIntentId(),
                workshop.price(),
                currency
            )
        );

        log.info(
            "Created PENDING paid registration [{}] for student [{}] and workshop [{}]",
            saved.id(),
            student.userId(),
            workshop.id()
        );

        // Preserve client secret on the returned aggregate for presentation mapping
        return saved
            .toBuilder()
            .paymentClientSecret(paymentIntent.clientSecret())
            .build();
    }
}
