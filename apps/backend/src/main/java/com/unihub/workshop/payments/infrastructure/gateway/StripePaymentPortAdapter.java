package com.unihub.workshop.payments.infrastructure.gateway;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import com.unihub.workshop.payments.application.ports.out.PaymentRepository;
import com.unihub.workshop.payments.domain.model.Payment;
import com.unihub.workshop.registrations.api.RegistrationPaymentPort;
import com.unihub.workshop.registrations.api.dto.CreatePaymentIntentCommand;
import com.unihub.workshop.registrations.api.dto.PaymentIntentResult;
import com.unihub.workshop.registrations.api.dto.RecordPendingPaymentCommand;
import com.unihub.workshop.shared.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.Locale;
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
}
