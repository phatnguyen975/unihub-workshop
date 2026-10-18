package com.unihub.workshop.payments.infrastructure.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stripe.StripeClient;
import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.model.PaymentIntent;
import com.stripe.service.PaymentIntentService;
import com.stripe.service.V1Services;
import com.unihub.workshop.payments.application.ports.out.PaymentRepository;
import com.unihub.workshop.payments.domain.model.Payment;
import com.unihub.workshop.payments.domain.model.PaymentStatus;
import com.unihub.workshop.registrations.api.dto.PaymentStatusResult;
import com.unihub.workshop.shared.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StripePaymentPortAdapterSweeperTest {

    private StripeClient stripeClient;
    private PaymentIntentService paymentIntentService;
    private CurrencyUnitConverter currencyUnitConverter;
    private CircuitBreaker circuitBreaker;
    private PaymentRepository paymentRepository;
    private StripePaymentPortAdapter adapter;

    private final UUID registrationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        stripeClient = mock(StripeClient.class);
        V1Services v1Services = mock(V1Services.class);
        paymentIntentService = mock(PaymentIntentService.class);

        when(stripeClient.v1()).thenReturn(v1Services);
        when(v1Services.paymentIntents()).thenReturn(paymentIntentService);

        currencyUnitConverter = new CurrencyUnitConverter();
        circuitBreaker = CircuitBreaker.ofDefaults("stripe-sweeper");
        paymentRepository = mock(PaymentRepository.class);
        adapter = new StripePaymentPortAdapter(
            stripeClient,
            currencyUnitConverter,
            circuitBreaker,
            paymentRepository
        );
    }

    @Test
    @DisplayName(
        "getPaymentStatus: returns empty when no payment record exists for registration (FR-13)"
    )
    void getPaymentStatus_NoPaymentRecord_ReturnsEmpty() {
        when(paymentRepository.findByRegistrationId(registrationId)).thenReturn(
            Optional.empty()
        );

        Optional<PaymentStatusResult> result = adapter.getPaymentStatus(
            registrationId
        );

        assertThat(result).isEmpty();
        verify(stripeClient, never()).v1();
    }

    @Test
    @DisplayName(
        "getPaymentStatus: retrieves status succeeded from Stripe and maps correctly (FR-06)"
    )
    void getPaymentStatus_Succeeded_ReturnsPaymentStatusResult()
        throws Exception {
        Payment payment = Payment.createPending(
            UUID.randomUUID(),
            registrationId,
            "STRIPE",
            "pi_100",
            new BigDecimal("25.00"),
            "USD"
        );
        when(paymentRepository.findByRegistrationId(registrationId)).thenReturn(
            Optional.of(payment)
        );

        PaymentIntent mockIntent = mock(PaymentIntent.class);
        when(mockIntent.getId()).thenReturn("pi_100");
        when(mockIntent.getStatus()).thenReturn("succeeded");
        when(mockIntent.getAmount()).thenReturn(2500L);
        when(mockIntent.getCurrency()).thenReturn("usd");

        when(paymentIntentService.retrieve("pi_100")).thenReturn(mockIntent);

        Optional<PaymentStatusResult> result = adapter.getPaymentStatus(
            registrationId
        );

        assertThat(result).isPresent();
        PaymentStatusResult statusResult = result.get();
        assertThat(statusResult.providerPaymentId()).isEqualTo("pi_100");
        assertThat(statusResult.status()).isEqualTo("succeeded");
        assertThat(statusResult.isSucceeded()).isTrue();
        assertThat(statusResult.amount()).isEqualByComparingTo("25.00");
        assertThat(statusResult.currency()).isEqualTo("USD");
    }

    @Test
    @DisplayName(
        "getPaymentStatus: retrieves abandoned status requires_payment_method from Stripe"
    )
    void getPaymentStatus_RequiresPaymentMethod_ReturnsPaymentStatusResult()
        throws Exception {
        Payment payment = Payment.createPending(
            UUID.randomUUID(),
            registrationId,
            "STRIPE",
            "pi_200",
            new BigDecimal("25.00"),
            "USD"
        );
        when(paymentRepository.findByRegistrationId(registrationId)).thenReturn(
            Optional.of(payment)
        );

        PaymentIntent mockIntent = mock(PaymentIntent.class);
        when(mockIntent.getId()).thenReturn("pi_200");
        when(mockIntent.getStatus()).thenReturn("requires_payment_method");
        when(mockIntent.getAmount()).thenReturn(2500L);
        when(mockIntent.getCurrency()).thenReturn("usd");

        when(paymentIntentService.retrieve("pi_200")).thenReturn(mockIntent);

        Optional<PaymentStatusResult> result = adapter.getPaymentStatus(
            registrationId
        );

        assertThat(result).isPresent();
        PaymentStatusResult statusResult = result.get();
        assertThat(statusResult.isFailedOrCanceled()).isTrue();
    }

    @Test
    @DisplayName(
        "getPaymentStatus: throws ServiceUnavailableException on Stripe network failure"
    )
    void getPaymentStatus_StripeException_ThrowsServiceUnavailable()
        throws Exception {
        Payment payment = Payment.createPending(
            UUID.randomUUID(),
            registrationId,
            "STRIPE",
            "pi_err",
            new BigDecimal("25.00"),
            "USD"
        );
        when(paymentRepository.findByRegistrationId(registrationId)).thenReturn(
            Optional.of(payment)
        );
        when(paymentIntentService.retrieve("pi_err")).thenThrow(
            new ApiConnectionException("Connection timeout")
        );

        assertThatThrownBy(() ->
            adapter.getPaymentStatus(registrationId)
        ).isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    @DisplayName(
        "getPaymentStatus: throws CallNotPermittedException when circuit breaker is open (FR-10)"
    )
    void getPaymentStatus_CircuitBreakerOpen_ThrowsCallNotPermittedException() {
        circuitBreaker.transitionToOpenState();
        Payment payment = Payment.createPending(
            UUID.randomUUID(),
            registrationId,
            "STRIPE",
            "pi_cb",
            new BigDecimal("25.00"),
            "USD"
        );
        when(paymentRepository.findByRegistrationId(registrationId)).thenReturn(
            Optional.of(payment)
        );

        assertThatThrownBy(() ->
            adapter.getPaymentStatus(registrationId)
        ).isInstanceOf(CallNotPermittedException.class);
    }

    @Test
    @DisplayName(
        "cancelPaymentAttempt: cancels PaymentIntent on Stripe and returns canceled result (FR-08)"
    )
    void cancelPaymentAttempt_Success_ReturnsCanceledResult() throws Exception {
        PaymentIntent mockIntent = mock(PaymentIntent.class);
        when(mockIntent.getId()).thenReturn("pi_cancel_1");
        when(mockIntent.getStatus()).thenReturn("canceled");

        when(paymentIntentService.cancel("pi_cancel_1")).thenReturn(mockIntent);

        PaymentStatusResult result = adapter.cancelPaymentAttempt(
            "pi_cancel_1"
        );

        assertThat(result.providerPaymentId()).isEqualTo("pi_cancel_1");
        assertThat(result.status()).isEqualTo("canceled");
    }

    @Test
    @DisplayName(
        "cancelPaymentAttempt: when Stripe reveals payment has succeeded, transitions to Case A (FR-12)"
    )
    void cancelPaymentAttempt_RevealsSucceeded_ReturnsSucceededResult()
        throws Exception {
        when(paymentIntentService.cancel("pi_succeeded_race")).thenThrow(
            new InvalidRequestException(
                "You cannot cancel this PaymentIntent because it has a status of succeeded.",
                "status",
                "req_123",
                "payment_intent_unexpected_state",
                400,
                null
            )
        );

        PaymentIntent mockIntent = mock(PaymentIntent.class);
        when(mockIntent.getId()).thenReturn("pi_succeeded_race");
        when(mockIntent.getStatus()).thenReturn("succeeded");
        when(mockIntent.getAmount()).thenReturn(5000L);
        when(mockIntent.getCurrency()).thenReturn("usd");

        when(paymentIntentService.retrieve("pi_succeeded_race")).thenReturn(
            mockIntent
        );

        PaymentStatusResult result = adapter.cancelPaymentAttempt(
            "pi_succeeded_race"
        );

        assertThat(result.providerPaymentId()).isEqualTo("pi_succeeded_race");
        assertThat(result.isSucceeded()).isTrue();
        assertThat(result.amount()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("markPaymentCompleted: updates pending payment to COMPLETED")
    void markPaymentCompleted_Success_UpdatesPayment() {
        Payment payment = Payment.createPending(
            UUID.randomUUID(),
            registrationId,
            "STRIPE",
            "pi_comp",
            new BigDecimal("30.00"),
            "USD"
        );
        when(paymentRepository.findByRegistrationId(registrationId)).thenReturn(
            Optional.of(payment)
        );

        adapter.markPaymentCompleted(
            registrationId,
            "pi_comp",
            new BigDecimal("30.00"),
            "USD"
        );

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        Payment saved = captor.getValue();
        assertThat(saved.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(saved.completedAt()).isNotNull();
    }

    @Test
    @DisplayName("markPaymentFailed: updates pending payment to FAILED")
    void markPaymentFailed_Success_UpdatesPayment() {
        Payment payment = Payment.createPending(
            UUID.randomUUID(),
            registrationId,
            "STRIPE",
            "pi_fail",
            new BigDecimal("30.00"),
            "USD"
        );
        when(paymentRepository.findByRegistrationId(registrationId)).thenReturn(
            Optional.of(payment)
        );

        adapter.markPaymentFailed(
            registrationId,
            "Expired pending registration"
        );

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        Payment saved = captor.getValue();
        assertThat(saved.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(saved.failureReason()).isEqualTo(
            "Expired pending registration"
        );
        assertThat(saved.failedAt()).isNotNull();
    }
}
