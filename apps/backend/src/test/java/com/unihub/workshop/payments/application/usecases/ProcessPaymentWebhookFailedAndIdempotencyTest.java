package com.unihub.workshop.payments.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeError;
import com.unihub.workshop.payments.application.ports.out.PaymentRepository;
import com.unihub.workshop.payments.application.ports.out.WebhookVerificationPort;
import com.unihub.workshop.payments.domain.model.Payment;
import com.unihub.workshop.payments.domain.model.PaymentStatus;
import com.unihub.workshop.registrations.api.RegistrationApi;
import com.unihub.workshop.registrations.api.dto.ConfirmPaidRegistrationCommand;
import com.unihub.workshop.registrations.api.dto.FailPaidRegistrationCommand;
import com.unihub.workshop.registrations.api.dto.RegistrationConfirmedDto;
import com.unihub.workshop.shared.realtime.api.RealtimeEventPublisher;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

class ProcessPaymentWebhookFailedAndIdempotencyTest {

    private WebhookVerificationPort webhookVerificationPort;
    private PaymentRepository paymentRepository;
    private RegistrationApi registrationApi;
    private RealtimeEventPublisher realtimeEventPublisher;
    private ProcessPaymentWebhookUseCaseImpl useCase;

    private static final String PAYLOAD = "{\"id\":\"evt_test_failed\"}";
    private static final String SIGNATURE = "valid_sig";
    private static final UUID REGISTRATION_ID = UUID.fromString(
        "e0000001-0000-4000-a000-000000000001"
    );
    private static final UUID WORKSHOP_ID = UUID.fromString(
        "f0000001-0000-4000-a000-000000000001"
    );
    private static final UUID STUDENT_USER_ID = UUID.fromString(
        "a0000001-0000-4000-a000-000000000001"
    );
    private static final String PROVIDER_PAYMENT_ID = "pi_test_failed_123";

    @BeforeEach
    void setUp() {
        webhookVerificationPort = mock(WebhookVerificationPort.class);
        paymentRepository = mock(PaymentRepository.class);
        registrationApi = mock(RegistrationApi.class);
        realtimeEventPublisher = mock(RealtimeEventPublisher.class);

        useCase = new ProcessPaymentWebhookUseCaseImpl(
            webhookVerificationPort,
            paymentRepository,
            registrationApi,
            realtimeEventPublisher
        );
    }

    private Event createMockFailedEvent(
        String paymentIntentId,
        Map<String, String> metadata,
        Long amount,
        String currency,
        String errorCode,
        String errorMessage
    ) {
        Event mockEvent = mock(Event.class);
        when(mockEvent.getId()).thenReturn("evt_test_failed");
        when(mockEvent.getType()).thenReturn("payment_intent.payment_failed");

        EventDataObjectDeserializer mockDeserializer = mock(
            EventDataObjectDeserializer.class
        );
        PaymentIntent mockPaymentIntent = mock(PaymentIntent.class);
        when(mockPaymentIntent.getId()).thenReturn(paymentIntentId);
        when(mockPaymentIntent.getAmount()).thenReturn(amount);
        when(mockPaymentIntent.getCurrency()).thenReturn(currency);
        when(mockPaymentIntent.getMetadata()).thenReturn(metadata);

        if (errorCode != null || errorMessage != null) {
            StripeError stripeError = mock(StripeError.class);
            when(stripeError.getCode()).thenReturn(errorCode);
            when(stripeError.getMessage()).thenReturn(errorMessage);
            when(mockPaymentIntent.getLastPaymentError()).thenReturn(
                stripeError
            );
        }

        when(mockDeserializer.getObject()).thenReturn(
            Optional.of(mockPaymentIntent)
        );
        when(mockEvent.getDataObjectDeserializer()).thenReturn(
            mockDeserializer
        );

        return mockEvent;
    }

    /** Helper to build a PENDING payment stub as would exist after PaidRegistrationStrategy runs. */
    private Payment pendingPayment() {
        return Payment.createPending(
            UUID.randomUUID(),
            REGISTRATION_ID,
            "STRIPE",
            PROVIDER_PAYMENT_ID,
            new BigDecimal("50.00"),
            "USD"
        );
    }

    @Test
    @DisplayName(
        "FR-21, BR-08: payment_intent.payment_failed with PENDING record updates to FAILED, calls failPaidRegistration, does not publish SSE"
    )
    void processWebhook_PaymentIntentFailed_Success() {
        Map<String, String> metadata = Map.of(
            "registrationId",
            REGISTRATION_ID.toString(),
            "workshopId",
            WORKSHOP_ID.toString()
        );
        Event mockEvent = createMockFailedEvent(
            PROVIDER_PAYMENT_ID,
            metadata,
            5000L,
            "usd",
            "card_declined",
            "Your card has insufficient funds."
        );
        when(
            webhookVerificationPort.verifyAndConstructEvent(PAYLOAD, SIGNATURE)
        ).thenReturn(mockEvent);
        when(
            paymentRepository.findByProviderPaymentId(PROVIDER_PAYMENT_ID)
        ).thenReturn(Optional.of(pendingPayment()));
        when(registrationApi.failPaidRegistration(any())).thenReturn(true);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(
            invocation -> invocation.getArgument(0)
        );

        useCase.processWebhook(PAYLOAD, SIGNATURE);

        // 1. Verify RegistrationApi call
        verify(registrationApi).failPaidRegistration(
            new FailPaidRegistrationCommand(
                REGISTRATION_ID,
                "Your card has insufficient funds."
            )
        );

        // 2. Verify Payment record saved as FAILED (updated from PENDING)
        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(
            Payment.class
        );
        verify(paymentRepository).save(paymentCaptor.capture());
        Payment savedPayment = paymentCaptor.getValue();
        assertThat(savedPayment.registrationId()).isEqualTo(REGISTRATION_ID);
        assertThat(savedPayment.providerPaymentId()).isEqualTo(
            PROVIDER_PAYMENT_ID
        );
        assertThat(savedPayment.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(savedPayment.failureCode()).isEqualTo("card_declined");
        assertThat(savedPayment.failureReason()).isEqualTo(
            "Your card has insufficient funds."
        );
        assertThat(savedPayment.failedAt()).isNotNull();

        // 3. Verify no SSE published on failure
        verify(realtimeEventPublisher, never()).publishToUser(any(), any());
        verify(realtimeEventPublisher, never()).publishToWorkshop(any(), any());
    }

    @Test
    @DisplayName(
        "FR-27: payment_intent.payment_failed missing registrationId logs warning and does not call registrationApi or save payment"
    )
    void processWebhook_PaymentIntentFailed_MissingRegistrationId_NoOp() {
        Event mockEvent = createMockFailedEvent(
            PROVIDER_PAYMENT_ID,
            Map.of(),
            5000L,
            "usd",
            "card_declined",
            "Insufficient funds"
        );
        when(
            webhookVerificationPort.verifyAndConstructEvent(PAYLOAD, SIGNATURE)
        ).thenReturn(mockEvent);

        useCase.processWebhook(PAYLOAD, SIGNATURE);

        verify(registrationApi, never()).failPaidRegistration(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName(
        "FR-27: Nonexistent or non-PENDING registration (failPaidRegistration returns false) does not save payment"
    )
    void processWebhook_PaymentIntentFailed_RegistrationApiReturnsFalse_DoesNotSavePayment() {
        Map<String, String> metadata = Map.of(
            "registrationId",
            REGISTRATION_ID.toString(),
            "workshopId",
            WORKSHOP_ID.toString()
        );
        Event mockEvent = createMockFailedEvent(
            PROVIDER_PAYMENT_ID,
            metadata,
            5000L,
            "usd",
            "card_declined",
            "Insufficient funds"
        );
        when(
            webhookVerificationPort.verifyAndConstructEvent(PAYLOAD, SIGNATURE)
        ).thenReturn(mockEvent);
        when(
            paymentRepository.findByProviderPaymentId(PROVIDER_PAYMENT_ID)
        ).thenReturn(Optional.of(pendingPayment()));
        when(registrationApi.failPaidRegistration(any())).thenReturn(false);

        useCase.processWebhook(PAYLOAD, SIGNATURE);

        verify(registrationApi).failPaidRegistration(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName(
        "FR-22, BR-07: Existing FAILED payment (terminal state) on payment_intent.payment_failed is ignored"
    )
    void processWebhook_PaymentIntentFailed_AlreadyFailed_NoOp() {
        Map<String, String> metadata = Map.of(
            "registrationId",
            REGISTRATION_ID.toString(),
            "workshopId",
            WORKSHOP_ID.toString()
        );
        Event mockEvent = createMockFailedEvent(
            PROVIDER_PAYMENT_ID,
            metadata,
            5000L,
            "usd",
            "card_declined",
            "Insufficient funds"
        );
        when(
            webhookVerificationPort.verifyAndConstructEvent(PAYLOAD, SIGNATURE)
        ).thenReturn(mockEvent);
        // Simulate an already-FAILED record (terminal state)
        Payment failedPayment = Payment.createFailed(
            UUID.randomUUID(),
            REGISTRATION_ID,
            "STRIPE",
            PROVIDER_PAYMENT_ID,
            new BigDecimal("50.00"),
            "USD",
            "card_declined",
            "Insufficient funds",
            Instant.now()
        );
        when(
            paymentRepository.findByProviderPaymentId(PROVIDER_PAYMENT_ID)
        ).thenReturn(Optional.of(failedPayment));

        useCase.processWebhook(PAYLOAD, SIGNATURE);

        verify(registrationApi, never()).failPaidRegistration(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName(
        "FR-22, BR-07: Concurrent duplicate throwing DataIntegrityViolationException is caught gracefully without throwing"
    )
    void processWebhook_PaymentIntentSucceeded_DataIntegrityViolationException_HandledGracefully() {
        Map<String, String> metadata = Map.of(
            "registrationId",
            REGISTRATION_ID.toString(),
            "workshopId",
            WORKSHOP_ID.toString()
        );
        Event mockEvent = mock(Event.class);
        when(mockEvent.getId()).thenReturn("evt_test_123");
        when(mockEvent.getType()).thenReturn("payment_intent.succeeded");

        EventDataObjectDeserializer mockDeserializer = mock(
            EventDataObjectDeserializer.class
        );
        PaymentIntent mockPaymentIntent = mock(PaymentIntent.class);
        when(mockPaymentIntent.getId()).thenReturn(PROVIDER_PAYMENT_ID);
        when(mockPaymentIntent.getAmount()).thenReturn(5000L);
        when(mockPaymentIntent.getCurrency()).thenReturn("usd");
        when(mockPaymentIntent.getMetadata()).thenReturn(metadata);

        when(mockDeserializer.getObject()).thenReturn(
            Optional.of(mockPaymentIntent)
        );
        when(mockEvent.getDataObjectDeserializer()).thenReturn(
            mockDeserializer
        );

        when(
            webhookVerificationPort.verifyAndConstructEvent(PAYLOAD, SIGNATURE)
        ).thenReturn(mockEvent);
        when(
            paymentRepository.findByProviderPaymentId(PROVIDER_PAYMENT_ID)
        ).thenReturn(Optional.of(pendingPayment()));

        RegistrationConfirmedDto confirmedDto = new RegistrationConfirmedDto(
            REGISTRATION_ID,
            WORKSHOP_ID,
            STUDENT_USER_ID,
            "qr_payload",
            Instant.now()
        );
        when(
            registrationApi.confirmPaidRegistration(
                new ConfirmPaidRegistrationCommand(
                    REGISTRATION_ID,
                    PROVIDER_PAYMENT_ID
                )
            )
        ).thenReturn(confirmedDto);

        doThrow(
            new DataIntegrityViolationException(
                "duplicate key value violates unique constraint uq_payments_provider_payment_id"
            )
        )
            .when(paymentRepository)
            .save(any(Payment.class));

        assertThatCode(() ->
            useCase.processWebhook(PAYLOAD, SIGNATURE)
        ).doesNotThrowAnyException();
    }
}
