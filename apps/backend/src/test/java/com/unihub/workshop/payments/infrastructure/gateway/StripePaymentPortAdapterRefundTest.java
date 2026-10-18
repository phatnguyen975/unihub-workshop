package com.unihub.workshop.payments.infrastructure.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stripe.StripeClient;
import com.stripe.exception.ApiException;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.param.RefundCreateParams;
import com.stripe.service.RefundService;
import com.stripe.service.V1Services;
import com.unihub.workshop.payments.application.ports.out.PaymentRepository;
import com.unihub.workshop.payments.domain.model.Payment;
import com.unihub.workshop.payments.domain.model.PaymentStatus;
import com.unihub.workshop.registrations.api.dto.RefundPaymentCommand;
import com.unihub.workshop.registrations.api.dto.RefundPaymentResult;
import com.unihub.workshop.shared.exception.InternalServerException;
import com.unihub.workshop.shared.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StripePaymentPortAdapterRefundTest {

    private StripeClient stripeClient;
    private RefundService refundService;
    private CurrencyUnitConverter currencyUnitConverter;
    private CircuitBreaker circuitBreaker;
    private PaymentRepository paymentRepository;
    private StripePaymentPortAdapter adapter;

    private final UUID registrationId = UUID.randomUUID();
    private final String providerPaymentId = "pi_test_123456789";
    private final String idempotencyKey = "refund-reg-" + registrationId;

    @BeforeEach
    void setUp() {
        stripeClient = mock(StripeClient.class);
        V1Services v1Services = mock(V1Services.class);
        refundService = mock(RefundService.class);

        when(stripeClient.v1()).thenReturn(v1Services);
        when(v1Services.refunds()).thenReturn(refundService);

        currencyUnitConverter = new CurrencyUnitConverter();
        circuitBreaker = CircuitBreaker.ofDefaults("stripe-refund-test");
        paymentRepository = mock(PaymentRepository.class);

        adapter = new StripePaymentPortAdapter(
            stripeClient,
            currencyUnitConverter,
            circuitBreaker,
            paymentRepository
        );
    }

    private Payment createCompletedPayment(BigDecimal amount, String currency) {
        return Payment.builder()
            .id(UUID.randomUUID())
            .registrationId(registrationId)
            .provider("STRIPE")
            .providerPaymentId(providerPaymentId)
            .status(PaymentStatus.COMPLETED)
            .amount(amount)
            .currency(currency)
            .completedAt(Instant.now().minusSeconds(3600))
            .createdAt(Instant.now().minusSeconds(3600))
            .updatedAt(Instant.now().minusSeconds(3600))
            .build();
    }

    @Test
    @DisplayName(
        "hasCompletedPayment: returns true when payment exists in COMPLETED status"
    )
    void hasCompletedPayment_WhenCompleted_ReturnsTrue() {
        Payment payment = createCompletedPayment(
            new BigDecimal("50.00"),
            "USD"
        );
        when(paymentRepository.findByRegistrationId(registrationId)).thenReturn(
            Optional.of(payment)
        );

        boolean result = adapter.hasCompletedPayment(registrationId);

        assertTrue(result);
        verify(paymentRepository).findByRegistrationId(registrationId);
    }

    @Test
    @DisplayName(
        "hasCompletedPayment: returns false when payment is missing or status is not COMPLETED"
    )
    void hasCompletedPayment_WhenMissingOrNotCompleted_ReturnsFalse() {
        when(paymentRepository.findByRegistrationId(registrationId)).thenReturn(
            Optional.empty()
        );
        assertFalse(adapter.hasCompletedPayment(registrationId));

        Payment pending = Payment.builder()
            .id(UUID.randomUUID())
            .registrationId(registrationId)
            .provider("STRIPE")
            .providerPaymentId(providerPaymentId)
            .status(PaymentStatus.PENDING)
            .amount(new BigDecimal("50.00"))
            .currency("USD")
            .build();

        when(paymentRepository.findByRegistrationId(registrationId)).thenReturn(
            Optional.of(pending)
        );
        assertFalse(adapter.hasCompletedPayment(registrationId));
    }

    @Test
    @DisplayName(
        "refundPayment: returns RefundPaymentResult when payment is completed and Stripe refund succeeds (FR-31, FR-32)"
    )
    void refundPayment_WhenCompletedAndStripeSucceeds_ReturnsResult()
        throws Exception {
        Payment payment = createCompletedPayment(
            new BigDecimal("50.00"),
            "USD"
        );
        when(paymentRepository.findByRegistrationId(registrationId)).thenReturn(
            Optional.of(payment)
        );

        Refund stripeRefund = mock(Refund.class);
        when(stripeRefund.getId()).thenReturn("re_test_987654321");
        when(stripeRefund.getStatus()).thenReturn("succeeded");
        when(stripeRefund.getAmount()).thenReturn(5000L);
        when(stripeRefund.getCurrency()).thenReturn("usd");

        when(
            refundService.create(
                any(RefundCreateParams.class),
                any(RequestOptions.class)
            )
        ).thenReturn(stripeRefund);

        RefundPaymentCommand command = new RefundPaymentCommand(
            registrationId,
            new BigDecimal("50.00"),
            "USD",
            idempotencyKey,
            "Customer canceled"
        );

        RefundPaymentResult result = adapter.refundPayment(command);

        assertNotNull(result);
        assertEquals("re_test_987654321", result.providerRefundId());
        assertEquals("succeeded", result.status());
        assertEquals(new BigDecimal("50.00"), result.amount());
        assertEquals("USD", result.currency());
        assertTrue(result.isSucceeded());

        ArgumentCaptor<RefundCreateParams> paramsCaptor =
            ArgumentCaptor.forClass(RefundCreateParams.class);
        ArgumentCaptor<RequestOptions> optionsCaptor = ArgumentCaptor.forClass(
            RequestOptions.class
        );
        verify(refundService).create(
            paramsCaptor.capture(),
            optionsCaptor.capture()
        );

        RefundCreateParams capturedParams = paramsCaptor.getValue();
        assertEquals(providerPaymentId, capturedParams.getPaymentIntent());
        assertEquals(5000L, capturedParams.getAmount());
        if (capturedParams.getMetadata() instanceof java.util.Map<?, ?> meta) {
            assertEquals(registrationId.toString(), meta.get("registrationId"));
        } else {
            Assertions.fail("Metadata is not a Map");
        }

        RequestOptions capturedOptions = optionsCaptor.getValue();
        assertEquals(idempotencyKey, capturedOptions.getIdempotencyKey());
    }

    @Test
    @DisplayName(
        "refundPayment: throws InternalServerException when no payment record exists (FR-34)"
    )
    void refundPayment_WhenPaymentNotFound_ThrowsInternalServerException()
        throws Exception {
        when(paymentRepository.findByRegistrationId(registrationId)).thenReturn(
            Optional.empty()
        );

        RefundPaymentCommand command = new RefundPaymentCommand(
            registrationId,
            new BigDecimal("50.00"),
            "USD",
            idempotencyKey,
            null
        );

        assertThrows(InternalServerException.class, () ->
            adapter.refundPayment(command)
        );
        verify(refundService, never()).create(
            any(RefundCreateParams.class),
            any(RequestOptions.class)
        );
    }

    @Test
    @DisplayName(
        "refundPayment: throws InternalServerException when payment status is not COMPLETED (FR-34)"
    )
    void refundPayment_WhenPaymentNotCompleted_ThrowsInternalServerException()
        throws Exception {
        Payment pending = Payment.builder()
            .id(UUID.randomUUID())
            .registrationId(registrationId)
            .provider("STRIPE")
            .providerPaymentId(providerPaymentId)
            .status(PaymentStatus.PENDING)
            .amount(new BigDecimal("50.00"))
            .currency("USD")
            .build();

        when(paymentRepository.findByRegistrationId(registrationId)).thenReturn(
            Optional.of(pending)
        );

        RefundPaymentCommand command = new RefundPaymentCommand(
            registrationId,
            new BigDecimal("50.00"),
            "USD",
            idempotencyKey,
            null
        );

        assertThrows(InternalServerException.class, () ->
            adapter.refundPayment(command)
        );
        verify(refundService, never()).create(
            any(RefundCreateParams.class),
            any(RequestOptions.class)
        );
    }

    @Test
    @DisplayName(
        "refundPayment: throws ServiceUnavailableException with 30s retry-after when Stripe API fails (FR-33)"
    )
    void refundPayment_WhenStripeApiFails_ThrowsServiceUnavailableException()
        throws Exception {
        Payment payment = createCompletedPayment(
            new BigDecimal("50.00"),
            "USD"
        );
        when(paymentRepository.findByRegistrationId(registrationId)).thenReturn(
            Optional.of(payment)
        );

        when(
            refundService.create(
                any(RefundCreateParams.class),
                any(RequestOptions.class)
            )
        ).thenThrow(
            new ApiException("Stripe API down", "req_err", "code", 500, null)
        );

        RefundPaymentCommand command = new RefundPaymentCommand(
            registrationId,
            new BigDecimal("50.00"),
            "USD",
            idempotencyKey,
            null
        );

        ServiceUnavailableException ex = assertThrows(
            ServiceUnavailableException.class,
            () -> adapter.refundPayment(command)
        );

        assertEquals(30, ex.getRetryAfterSeconds());
    }

    @Test
    @DisplayName(
        "refundPayment: throws CallNotPermittedException when circuit breaker is open (FR-33)"
    )
    void refundPayment_WhenCircuitBreakerOpen_ThrowsCallNotPermittedException() {
        Payment payment = createCompletedPayment(
            new BigDecimal("50.00"),
            "USD"
        );
        when(paymentRepository.findByRegistrationId(registrationId)).thenReturn(
            Optional.of(payment)
        );

        circuitBreaker.transitionToOpenState();

        RefundPaymentCommand command = new RefundPaymentCommand(
            registrationId,
            new BigDecimal("50.00"),
            "USD",
            idempotencyKey,
            null
        );

        assertThrows(CallNotPermittedException.class, () ->
            adapter.refundPayment(command)
        );
    }

    @Test
    @DisplayName(
        "markPaymentRefunded: updates payment status to REFUNDED and persists (FR-35)"
    )
    void markPaymentRefunded_WhenPaymentExists_UpdatesStatusToRefunded() {
        Payment payment = createCompletedPayment(
            new BigDecimal("50.00"),
            "USD"
        );
        when(paymentRepository.findByRegistrationId(registrationId)).thenReturn(
            Optional.of(payment)
        );

        adapter.markPaymentRefunded(
            registrationId,
            new BigDecimal("50.00"),
            "USD"
        );

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());

        Payment saved = captor.getValue();
        assertEquals(PaymentStatus.REFUNDED, saved.status());
        assertEquals(new BigDecimal("50.00"), saved.amount());
        assertEquals("USD", saved.currency());
        assertNotNull(saved.updatedAt());
    }
}
