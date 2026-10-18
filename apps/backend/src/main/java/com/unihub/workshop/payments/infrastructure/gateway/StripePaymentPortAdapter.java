package com.unihub.workshop.payments.infrastructure.gateway;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import com.unihub.workshop.payments.application.ports.out.PaymentRepository;
import com.unihub.workshop.payments.domain.model.Payment;
import com.unihub.workshop.payments.domain.model.PaymentStatus;
import com.unihub.workshop.registrations.api.RegistrationPaymentPort;
import com.unihub.workshop.registrations.api.dto.CreatePaymentIntentCommand;
import com.unihub.workshop.registrations.api.dto.PaymentIntentResult;
import com.unihub.workshop.registrations.api.dto.PaymentStatusResult;
import com.unihub.workshop.registrations.api.dto.RecordPendingPaymentCommand;
import com.unihub.workshop.registrations.api.dto.RefundPaymentCommand;
import com.unihub.workshop.registrations.api.dto.RefundPaymentResult;
import com.unihub.workshop.shared.exception.InternalServerException;
import com.unihub.workshop.shared.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing {@link RegistrationPaymentPort} by dispatching to the Stripe Java SDK
 * wrapped with a Resilience4j CircuitBreaker.
 */
@Slf4j
@Component
public class StripePaymentPortAdapter implements RegistrationPaymentPort {

    private final StripeClient stripeClient;
    private final CurrencyUnitConverter currencyUnitConverter;
    private final CircuitBreaker circuitBreaker;
    private final PaymentRepository paymentRepository;

    public StripePaymentPortAdapter(
        StripeClient stripeClient,
        CurrencyUnitConverter currencyUnitConverter,
        @Qualifier("stripeCircuitBreaker") CircuitBreaker circuitBreaker,
        PaymentRepository paymentRepository
    ) {
        this.stripeClient = stripeClient;
        this.currencyUnitConverter = currencyUnitConverter;
        this.circuitBreaker = circuitBreaker;
        this.paymentRepository = paymentRepository;
    }

    @Override
    public PaymentIntentResult createPaymentIntent(
        CreatePaymentIntentCommand command
    ) {
        long amountInSmallestUnit = currencyUnitConverter.toSmallestUnit(
            command.amount(),
            command.currency()
        );

        String normalizedCurrency =
            command.currency() != null && !command.currency().isBlank()
                ? command.currency().trim().toLowerCase(Locale.ROOT)
                : "usd";

        PaymentIntentCreateParams.Builder paramsBuilder =
            PaymentIntentCreateParams.builder()
                .setAmount(amountInSmallestUnit)
                .setCurrency(normalizedCurrency)
                .putMetadata(
                    "registrationId",
                    command.registrationId().toString()
                )
                .putMetadata("workshopId", command.workshopId().toString());

        RequestOptions.RequestOptionsBuilder optionsBuilder =
            RequestOptions.builder();
        if (
            command.idempotencyKey() != null &&
            !command.idempotencyKey().isBlank()
        ) {
            optionsBuilder.setIdempotencyKey(command.idempotencyKey());
        }

        try {
            PaymentIntent intent = circuitBreaker.executeCallable(() ->
                stripeClient
                    .v1()
                    .paymentIntents()
                    .create(paramsBuilder.build(), optionsBuilder.build())
            );

            log.info(
                "Created Stripe PaymentIntent [{}] for registration [{}] and workshop [{}]",
                intent.getId(),
                command.registrationId(),
                command.workshopId()
            );

            return new PaymentIntentResult(
                intent.getId(),
                intent.getClientSecret(),
                intent.getStatus()
            );
        } catch (CallNotPermittedException e) {
            log.warn(
                "Stripe circuit breaker [{}] is OPEN for registration [{}]: {}",
                circuitBreaker.getName(),
                command.registrationId(),
                e.getMessage()
            );
            throw e;
        } catch (StripeException e) {
            log.error(
                "Stripe API call failed for registration [{}]: {}",
                command.registrationId(),
                e.getMessage(),
                e
            );
            throw new ServiceUnavailableException(
                "Payment gateway service unavailable. Please retry later.",
                30,
                e
            );
        } catch (Exception e) {
            log.error(
                "Unexpected failure during payment intent creation for registration [{}]: {}",
                command.registrationId(),
                e.getMessage(),
                e
            );
            throw new ServiceUnavailableException(
                "Payment gateway service unavailable. Please retry later.",
                30,
                e
            );
        }
    }

    @Override
    public void recordPendingPayment(RecordPendingPaymentCommand command) {
        Payment pending = Payment.createPending(
            UUID.randomUUID(),
            command.registrationId(),
            "STRIPE",
            command.providerPaymentId(),
            command.amount(),
            command.currency()
        );
        try {
            paymentRepository.save(pending);
            log.info(
                "Recorded PENDING payment [{}] for registration [{}]",
                command.providerPaymentId(),
                command.registrationId()
            );
        } catch (DataIntegrityViolationException e) {
            log.info(
                "PENDING payment already exists for providerPaymentId [{}], skipping duplicate insert",
                command.providerPaymentId()
            );
        }
    }

    @Override
    public Optional<PaymentStatusResult> getPaymentStatus(UUID registrationId) {
        Optional<Payment> optPayment = paymentRepository.findByRegistrationId(
            registrationId
        );
        if (optPayment.isEmpty()) {
            log.debug(
                "No payment record found for registration [{}] during status query",
                registrationId
            );
            return Optional.empty();
        }

        Payment payment = optPayment.get();
        String providerPaymentId = payment.providerPaymentId();

        try {
            PaymentIntent intent = circuitBreaker.executeCallable(() ->
                stripeClient.v1().paymentIntents().retrieve(providerPaymentId)
            );

            BigDecimal amount = Payment.convertFromSmallestUnit(
                intent.getAmount() != null ? intent.getAmount() : 0L,
                intent.getCurrency()
            );
            String currency =
                intent.getCurrency() != null
                    ? intent.getCurrency().toUpperCase(Locale.ROOT)
                    : "USD";

            String failureCode = null;
            String failureReason = null;
            if (intent.getLastPaymentError() != null) {
                failureCode = intent.getLastPaymentError().getCode();
                failureReason = intent.getLastPaymentError().getMessage();
            }

            return Optional.of(
                new PaymentStatusResult(
                    registrationId,
                    intent.getId(),
                    intent.getStatus(),
                    amount,
                    currency,
                    failureCode,
                    failureReason
                )
            );
        } catch (CallNotPermittedException e) {
            log.warn(
                "Stripe circuit breaker [{}] is OPEN for payment query [{}]: {}",
                circuitBreaker.getName(),
                providerPaymentId,
                e.getMessage()
            );
            throw e;
        } catch (StripeException e) {
            log.error(
                "Stripe API call failed for payment status query [{}]: {}",
                providerPaymentId,
                e.getMessage(),
                e
            );
            throw new ServiceUnavailableException(
                "Payment gateway service unavailable. Please retry later.",
                30,
                e
            );
        } catch (Exception e) {
            log.error(
                "Unexpected failure during payment status query [{}]: {}",
                providerPaymentId,
                e.getMessage(),
                e
            );
            throw new ServiceUnavailableException(
                "Payment gateway service unavailable. Please retry later.",
                30,
                e
            );
        }
    }

    @Override
    public PaymentStatusResult cancelPaymentAttempt(String providerPaymentId) {
        try {
            PaymentIntent canceledIntent = circuitBreaker.executeCallable(() ->
                stripeClient.v1().paymentIntents().cancel(providerPaymentId)
            );

            log.info(
                "Canceled Stripe PaymentIntent [{}] with status [{}]",
                canceledIntent.getId(),
                canceledIntent.getStatus()
            );

            BigDecimal amount = Payment.convertFromSmallestUnit(
                canceledIntent.getAmount() != null
                    ? canceledIntent.getAmount()
                    : 0L,
                canceledIntent.getCurrency()
            );
            String currency =
                canceledIntent.getCurrency() != null
                    ? canceledIntent.getCurrency().toUpperCase(Locale.ROOT)
                    : "USD";

            return new PaymentStatusResult(
                null,
                canceledIntent.getId(),
                canceledIntent.getStatus(),
                amount,
                currency,
                null,
                null
            );
        } catch (CallNotPermittedException e) {
            log.warn(
                "Stripe circuit breaker [{}] is OPEN for payment cancel [{}]: {}",
                circuitBreaker.getName(),
                providerPaymentId,
                e.getMessage()
            );
            throw e;
        } catch (StripeException e) {
            // Check if cancel failed because PaymentIntent has already succeeded (FR-12)
            try {
                PaymentIntent current = circuitBreaker.executeCallable(() ->
                    stripeClient
                        .v1()
                        .paymentIntents()
                        .retrieve(providerPaymentId)
                );
                if ("succeeded".equalsIgnoreCase(current.getStatus())) {
                    log.info(
                        "PaymentIntent [{}] cancel revealed payment has already succeeded (FR-12)",
                        providerPaymentId
                    );
                    BigDecimal amount = Payment.convertFromSmallestUnit(
                        current.getAmount() != null ? current.getAmount() : 0L,
                        current.getCurrency()
                    );
                    String currency =
                        current.getCurrency() != null
                            ? current.getCurrency().toUpperCase(Locale.ROOT)
                            : "USD";
                    return new PaymentStatusResult(
                        null,
                        current.getId(),
                        current.getStatus(),
                        amount,
                        currency,
                        null,
                        null
                    );
                }
            } catch (Exception ignored) {
                // Ignore fallback retrieve errors and continue throwing original error below
            }

            log.error(
                "Stripe API call failed for payment cancel [{}]: {}",
                providerPaymentId,
                e.getMessage(),
                e
            );
            throw new ServiceUnavailableException(
                "Payment gateway service unavailable. Please retry later.",
                30,
                e
            );
        } catch (Exception e) {
            log.error(
                "Unexpected failure during payment cancel [{}]: {}",
                providerPaymentId,
                e.getMessage(),
                e
            );
            throw new ServiceUnavailableException(
                "Payment gateway service unavailable. Please retry later.",
                30,
                e
            );
        }
    }

    @Override
    public void markPaymentCompleted(
        UUID registrationId,
        String providerPaymentId,
        BigDecimal amount,
        String currency
    ) {
        Optional<Payment> existingPayment =
            paymentRepository.findByRegistrationId(registrationId);
        Payment completedPayment = existingPayment
            .map(p ->
                p
                    .toBuilder()
                    .status(PaymentStatus.COMPLETED)
                    .amount(amount != null ? amount : p.amount())
                    .currency(
                        currency != null
                            ? currency.toUpperCase(Locale.ROOT)
                            : p.currency()
                    )
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
            "Marked payment [{}] as COMPLETED for registration [{}]",
            providerPaymentId,
            registrationId
        );
    }

    @Override
    public void markPaymentFailed(UUID registrationId, String failureReason) {
        Optional<Payment> existingPayment =
            paymentRepository.findByRegistrationId(registrationId);
        if (existingPayment.isPresent()) {
            Payment p = existingPayment.get();
            if (p.status() == PaymentStatus.PENDING) {
                Payment failed = p
                    .toBuilder()
                    .status(PaymentStatus.FAILED)
                    .failureReason(failureReason)
                    .failedAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
                paymentRepository.save(failed);
                log.info(
                    "Marked payment [{}] as FAILED for registration [{}]. Reason: {}",
                    p.providerPaymentId(),
                    registrationId,
                    failureReason
                );
            }
        }
    }

    @Override
    public boolean hasCompletedPayment(UUID registrationId) {
        return paymentRepository
            .findByRegistrationId(registrationId)
            .map(p -> p.status() == PaymentStatus.COMPLETED)
            .orElse(false);
    }

    @Override
    public RefundPaymentResult refundPayment(RefundPaymentCommand command) {
        Optional<Payment> optPayment = paymentRepository.findByRegistrationId(
            command.registrationId()
        );
        if (
            optPayment.isEmpty() ||
            optPayment.get().status() != PaymentStatus.COMPLETED
        ) {
            log.error(
                "Refusing refund for registration [{}]: payment record missing or not COMPLETED (FR-34)",
                command.registrationId()
            );
            throw new InternalServerException(
                "Paid registration has no payment record in COMPLETED status: " +
                    command.registrationId()
            );
        }

        Payment payment = optPayment.get();
        String providerPaymentId = payment.providerPaymentId();

        long amountInSmallestUnit = currencyUnitConverter.toSmallestUnit(
            command.amount(),
            command.currency()
        );

        RefundCreateParams.Builder paramsBuilder = RefundCreateParams.builder()
            .setPaymentIntent(providerPaymentId)
            .setAmount(amountInSmallestUnit)
            .putMetadata("registrationId", command.registrationId().toString());

        RequestOptions.RequestOptionsBuilder optionsBuilder =
            RequestOptions.builder();
        if (
            command.idempotencyKey() != null &&
            !command.idempotencyKey().isBlank()
        ) {
            optionsBuilder.setIdempotencyKey(command.idempotencyKey());
        }

        try {
            Refund refund = circuitBreaker.executeCallable(() ->
                stripeClient
                    .v1()
                    .refunds()
                    .create(paramsBuilder.build(), optionsBuilder.build())
            );

            log.info(
                "Created Stripe Refund [{}] with status [{}] for payment intent [{}] and registration [{}]",
                refund.getId(),
                refund.getStatus(),
                providerPaymentId,
                command.registrationId()
            );

            BigDecimal refundedAmount = Payment.convertFromSmallestUnit(
                refund.getAmount() != null
                    ? refund.getAmount()
                    : amountInSmallestUnit,
                refund.getCurrency() != null
                    ? refund.getCurrency()
                    : command.currency()
            );

            String currency =
                refund.getCurrency() != null
                    ? refund.getCurrency().toUpperCase(Locale.ROOT)
                    : command.currency() != null
                      ? command.currency().toUpperCase(Locale.ROOT)
                      : "USD";

            return new RefundPaymentResult(
                refund.getId(),
                refund.getStatus(),
                refundedAmount,
                currency
            );
        } catch (CallNotPermittedException e) {
            log.warn(
                "Stripe circuit breaker [{}] is OPEN for payment refund [{}]: {}",
                circuitBreaker.getName(),
                providerPaymentId,
                e.getMessage()
            );
            throw e;
        } catch (StripeException e) {
            log.error(
                "Stripe API call failed for payment refund [{}]: {}",
                providerPaymentId,
                e.getMessage(),
                e
            );
            throw new ServiceUnavailableException(
                "Payment gateway service unavailable. Please retry later.",
                30,
                e
            );
        } catch (Exception e) {
            log.error(
                "Unexpected failure during payment refund [{}]: {}",
                providerPaymentId,
                e.getMessage(),
                e
            );
            throw new ServiceUnavailableException(
                "Payment gateway service unavailable. Please retry later.",
                30,
                e
            );
        }
    }

    @Override
    public void markPaymentRefunded(
        UUID registrationId,
        BigDecimal amount,
        String currency
    ) {
        Optional<Payment> existingPayment =
            paymentRepository.findByRegistrationId(registrationId);
        if (existingPayment.isPresent()) {
            Payment p = existingPayment.get();
            Payment refunded = p
                .toBuilder()
                .status(PaymentStatus.REFUNDED)
                .amount(amount != null ? amount : p.amount())
                .currency(
                    currency != null
                        ? currency.toUpperCase(Locale.ROOT)
                        : p.currency()
                )
                .updatedAt(Instant.now())
                .build();
            paymentRepository.save(refunded);
            log.info(
                "Marked payment [{}] as REFUNDED for registration [{}]",
                p.providerPaymentId(),
                registrationId
            );
        }
    }
}
