package com.unihub.workshop.payments.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.unihub.workshop.payments.application.ports.out.PaymentRepository;
import com.unihub.workshop.payments.application.ports.out.WebhookVerificationPort;
import com.unihub.workshop.payments.domain.model.Payment;
import com.unihub.workshop.payments.domain.model.PaymentStatus;
import com.unihub.workshop.registrations.api.RegistrationApi;
import com.unihub.workshop.registrations.api.dto.ConfirmPaidRegistrationCommand;
import com.unihub.workshop.registrations.api.dto.RegistrationConfirmedDto;
import com.unihub.workshop.shared.realtime.api.RealtimeEvent;
import com.unihub.workshop.shared.realtime.api.RealtimeEventPublisher;
import com.unihub.workshop.shared.realtime.api.RealtimeEventType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ProcessPaymentWebhookSuccessTest {

    private WebhookVerificationPort webhookVerificationPort;
    private PaymentRepository paymentRepository;
    private RegistrationApi registrationApi;
    private RealtimeEventPublisher realtimeEventPublisher;
    private ProcessPaymentWebhookUseCaseImpl useCase;

    private static final String PAYLOAD = "{\"id\":\"evt_test_123\"}";
    private static final String SIGNATURE = "valid_signature";
    private static final UUID REGISTRATION_ID = UUID.fromString(
        "e0000001-0000-4000-a000-000000000001"
    );
    private static final UUID WORKSHOP_ID = UUID.fromString(
        "f0000001-0000-4000-a000-000000000001"
    );
    private static final UUID STUDENT_USER_ID = UUID.fromString(
        "a0000001-0000-4000-a000-000000000001"
    );
    private static final String PROVIDER_PAYMENT_ID = "pi_test_succeeded_123";

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

    private Event createMockSucceededEvent(
        String paymentIntentId,
        Map<String, String> metadata,
        Long amount,
        String currency
    ) {
        Event mockEvent = mock(Event.class);
        when(mockEvent.getId()).thenReturn("evt_test_123");
        when(mockEvent.getType()).thenReturn("payment_intent.succeeded");

        EventDataObjectDeserializer mockDeserializer = mock(
            EventDataObjectDeserializer.class
        );
        PaymentIntent mockPaymentIntent = mock(PaymentIntent.class);
        when(mockPaymentIntent.getId()).thenReturn(paymentIntentId);
        when(mockPaymentIntent.getAmount()).thenReturn(amount);
        when(mockPaymentIntent.getCurrency()).thenReturn(currency);
        when(mockPaymentIntent.getMetadata()).thenReturn(metadata);

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
        "FR-20, FR-23, FR-24: payment_intent.succeeded with existing PENDING record updates to COMPLETED, confirms registration, and publishes SSE events"
    )
    void processWebhook_PaymentIntentSucceeded_Success() {
        Map<String, String> metadata = Map.of(
            "registrationId",
            REGISTRATION_ID.toString(),
            "workshopId",
            WORKSHOP_ID.toString()
        );
        Event mockEvent = createMockSucceededEvent(
            PROVIDER_PAYMENT_ID,
            metadata,
            5000L,
            "usd"
        );
        when(
            webhookVerificationPort.verifyAndConstructEvent(PAYLOAD, SIGNATURE)
        ).thenReturn(mockEvent);
        // Simulate the PENDING record that PaidRegistrationStrategy created
        when(
            paymentRepository.findByProviderPaymentId(PROVIDER_PAYMENT_ID)
        ).thenReturn(Optional.of(pendingPayment()));

        Instant issuedAt = Instant.now();
        RegistrationConfirmedDto confirmedDto = new RegistrationConfirmedDto(
            REGISTRATION_ID,
            WORKSHOP_ID,
            STUDENT_USER_ID,
            "{\"reg\":\"" + REGISTRATION_ID + "\"}",
            issuedAt
        );
        when(
            registrationApi.confirmPaidRegistration(
                new ConfirmPaidRegistrationCommand(
                    REGISTRATION_ID,
                    PROVIDER_PAYMENT_ID
                )
            )
        ).thenReturn(confirmedDto);

        when(paymentRepository.save(any(Payment.class))).thenAnswer(
            invocation -> invocation.getArgument(0)
        );

        useCase.processWebhook(PAYLOAD, SIGNATURE);

        // 1. Verify RegistrationApi call
        verify(registrationApi).confirmPaidRegistration(
            new ConfirmPaidRegistrationCommand(
                REGISTRATION_ID,
                PROVIDER_PAYMENT_ID
            )
        );

        // 2. Verify Payment record saved as COMPLETED (updated from PENDING)
        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(
            Payment.class
        );
        verify(paymentRepository).save(paymentCaptor.capture());
        Payment savedPayment = paymentCaptor.getValue();
        assertThat(savedPayment.registrationId()).isEqualTo(REGISTRATION_ID);
        assertThat(savedPayment.providerPaymentId()).isEqualTo(
            PROVIDER_PAYMENT_ID
        );
        assertThat(savedPayment.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(savedPayment.amount()).isEqualByComparingTo(
            BigDecimal.valueOf(50.00)
        );
        assertThat(savedPayment.currency()).isEqualTo("USD");
        assertThat(savedPayment.completedAt()).isNotNull();

        // 3. Verify Realtime SSE events
        ArgumentCaptor<RealtimeEvent<?>> userEventCaptor =
            ArgumentCaptor.captor();
        verify(realtimeEventPublisher).publishToUser(
            eq(STUDENT_USER_ID),
            userEventCaptor.capture()
        );
        RealtimeEvent<?> userEvent = userEventCaptor.getValue();
        assertThat(userEvent.eventType()).isEqualTo(
            RealtimeEventType.PAYMENT_CONFIRMED.getValue()
        );
        assertThat(userEvent.payload())
            .asInstanceOf(
                InstanceOfAssertFactories.map(String.class, Object.class)
            )
            .containsEntry("registrationId", REGISTRATION_ID)
            .containsEntry("workshopId", WORKSHOP_ID)
            .containsEntry(
                "qrPayload",
                "{\"reg\":\"" + REGISTRATION_ID + "\"}"
            );

        ArgumentCaptor<RealtimeEvent<?>> workshopEventCaptor =
            ArgumentCaptor.captor();
        verify(realtimeEventPublisher).publishToWorkshop(
            eq(WORKSHOP_ID),
            workshopEventCaptor.capture()
        );
        RealtimeEvent<?> workshopEvent = workshopEventCaptor.getValue();
        assertThat(workshopEvent.eventType()).isEqualTo(
            RealtimeEventType.REGISTRATION_UPDATED.getValue()
        );
    }

    @Test
    @DisplayName(
        "FR-27: Missing registrationId in metadata logs warning and acknowledges without confirmation or payment"
    )
    void processWebhook_PaymentIntentSucceeded_MissingRegistrationId_NoOp() {
        Event mockEvent = createMockSucceededEvent(
            PROVIDER_PAYMENT_ID,
            Map.of(),
            5000L,
            "usd"
        );
        when(
            webhookVerificationPort.verifyAndConstructEvent(PAYLOAD, SIGNATURE)
        ).thenReturn(mockEvent);

        useCase.processWebhook(PAYLOAD, SIGNATURE);

        verify(registrationApi, never()).confirmPaidRegistration(any());
        verify(paymentRepository, never()).save(any());
        verify(realtimeEventPublisher, never()).publishToUser(any(), any());
    }

    @Test
    @DisplayName(
        "FR-22, BR-07: Existing COMPLETED payment skips confirmation and payment save"
    )
    void processWebhook_PaymentIntentSucceeded_AlreadyCompleted_NoOp() {
        Map<String, String> metadata = Map.of(
            "registrationId",
            REGISTRATION_ID.toString(),
            "workshopId",
            WORKSHOP_ID.toString()
        );
        Event mockEvent = createMockSucceededEvent(
            PROVIDER_PAYMENT_ID,
            metadata,
            5000L,
            "usd"
        );
        when(
            webhookVerificationPort.verifyAndConstructEvent(PAYLOAD, SIGNATURE)
        ).thenReturn(mockEvent);
        // Simulate a COMPLETED record already in the DB (terminal state)
        Payment completedPayment = Payment.createCompleted(
            UUID.randomUUID(),
            REGISTRATION_ID,
            "STRIPE",
            PROVIDER_PAYMENT_ID,
            new BigDecimal("50.00"),
            "USD",
            Instant.now()
        );
        when(
            paymentRepository.findByProviderPaymentId(PROVIDER_PAYMENT_ID)
        ).thenReturn(Optional.of(completedPayment));

        useCase.processWebhook(PAYLOAD, SIGNATURE);

        verify(registrationApi, never()).confirmPaidRegistration(any());
        verify(paymentRepository, never()).save(any());
        verify(realtimeEventPublisher, never()).publishToUser(any(), any());
    }

    @Test
    @DisplayName(
        "FR-27: Nonexistent or non-PENDING registration (RegistrationApi returns null) does not save payment"
    )
    void processWebhook_PaymentIntentSucceeded_RegistrationApiReturnsNull_DoesNotSavePayment() {
        Map<String, String> metadata = Map.of(
            "registrationId",
            REGISTRATION_ID.toString(),
            "workshopId",
            WORKSHOP_ID.toString()
        );
        Event mockEvent = createMockSucceededEvent(
            PROVIDER_PAYMENT_ID,
            metadata,
            5000L,
            "usd"
        );
        when(
            webhookVerificationPort.verifyAndConstructEvent(PAYLOAD, SIGNATURE)
        ).thenReturn(mockEvent);
        when(
            paymentRepository.findByProviderPaymentId(PROVIDER_PAYMENT_ID)
        ).thenReturn(Optional.of(pendingPayment()));
        when(registrationApi.confirmPaidRegistration(any())).thenReturn(null);

        useCase.processWebhook(PAYLOAD, SIGNATURE);

        verify(registrationApi).confirmPaidRegistration(
            new ConfirmPaidRegistrationCommand(
                REGISTRATION_ID,
                PROVIDER_PAYMENT_ID
            )
        );
        verify(paymentRepository, never()).save(any());
        verify(realtimeEventPublisher, never()).publishToUser(any(), any());
    }

    @Test
    @DisplayName(
        "No existing payment record (fallback path): inserts a new COMPLETED record"
    )
    void processWebhook_PaymentIntentSucceeded_NoExistingRecord_InsertsCompleted() {
        Map<String, String> metadata = Map.of(
            "registrationId",
            REGISTRATION_ID.toString(),
            "workshopId",
            WORKSHOP_ID.toString()
        );
        Event mockEvent = createMockSucceededEvent(
            PROVIDER_PAYMENT_ID,
            metadata,
            5000L,
            "usd"
        );
        when(
            webhookVerificationPort.verifyAndConstructEvent(PAYLOAD, SIGNATURE)
        ).thenReturn(mockEvent);
        // No existing record
        when(
            paymentRepository.findByProviderPaymentId(PROVIDER_PAYMENT_ID)
        ).thenReturn(Optional.empty());

        RegistrationConfirmedDto confirmedDto = new RegistrationConfirmedDto(
            REGISTRATION_ID,
            WORKSHOP_ID,
            STUDENT_USER_ID,
            "qr_payload",
            Instant.now()
        );
        when(registrationApi.confirmPaidRegistration(any())).thenReturn(
            confirmedDto
        );
        when(paymentRepository.save(any(Payment.class))).thenAnswer(
            invocation -> invocation.getArgument(0)
        );

        useCase.processWebhook(PAYLOAD, SIGNATURE);

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(
            Payment.class
        );
        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().status()).isEqualTo(
            PaymentStatus.COMPLETED
        );
    }
}
