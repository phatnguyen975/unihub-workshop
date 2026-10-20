package com.unihub.workshop.payments.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stripe.model.Event;
import com.stripe.net.Webhook;
import com.unihub.workshop.payments.application.ports.out.PaymentRepository;
import com.unihub.workshop.payments.application.ports.out.WebhookVerificationPort;
import com.unihub.workshop.registrations.api.RegistrationApi;
import com.unihub.workshop.registrations.api.dto.ConfirmPaidRegistrationCommand;
import com.unihub.workshop.registrations.api.dto.RegistrationConfirmedDto;
import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.realtime.api.RealtimeEventPublisher;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.transaction.annotation.Transactional;

class ProcessPaymentWebhookUseCaseImplTest {

    private WebhookVerificationPort webhookVerificationPort;
    private PaymentRepository paymentRepository;
    private RegistrationApi registrationApi;
    private RealtimeEventPublisher realtimeEventPublisher;
    private ProcessPaymentWebhookUseCaseImpl useCase;

    private static final String PAYLOAD = "{\"id\":\"evt_123\"}";
    private static final String SIGNATURE = "valid_sig";

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

    @Test
    @DisplayName(
        "Propagates BadRequestException when signature verification fails"
    )
    void processWebhook_InvalidSignature_ThrowsBadRequestException() {
        when(
            webhookVerificationPort.verifyAndConstructEvent(PAYLOAD, SIGNATURE)
        ).thenThrow(new BadRequestException("Invalid signature"));

        assertThatThrownBy(() -> useCase.processWebhook(PAYLOAD, SIGNATURE))
            .isInstanceOf(BadRequestException.class)
            .hasMessage("Invalid signature");

        verify(webhookVerificationPort).verifyAndConstructEvent(
            PAYLOAD,
            SIGNATURE
        );
    }

    @Test
    @DisplayName(
        "Processes valid webhook state transitions inside one transaction"
    )
    void processWebhook_IsTransactional() throws NoSuchMethodException {
        Method method = ProcessPaymentWebhookUseCaseImpl.class.getMethod(
            "processWebhook",
            String.class,
            String.class
        );

        assertThat(method.isAnnotationPresent(Transactional.class)).isTrue();
    }

    @Test
    @DisplayName("Handles unhandled event types gracefully without error")
    void processWebhook_UnhandledEventType_CompletesNormally() {
        Event event = new Event();
        event.setId("evt_123");
        event.setType("customer.subscription.created");

        when(
            webhookVerificationPort.verifyAndConstructEvent(PAYLOAD, SIGNATURE)
        ).thenReturn(event);

        assertThatCode(() ->
            useCase.processWebhook(PAYLOAD, SIGNATURE)
        ).doesNotThrowAnyException();

        verify(webhookVerificationPort).verifyAndConstructEvent(
            PAYLOAD,
            SIGNATURE
        );
    }

    @Test
    @DisplayName(
        "Handles payment_intent.succeeded and payment_intent.payment_failed without error"
    )
    void processWebhook_KnownEventTypes_CompletesNormally() {
        Event succeededEvent = new Event();
        succeededEvent.setId("evt_succ_1");
        succeededEvent.setType("payment_intent.succeeded");

        when(
            webhookVerificationPort.verifyAndConstructEvent(PAYLOAD, SIGNATURE)
        ).thenReturn(succeededEvent);

        assertThatCode(() ->
            useCase.processWebhook(PAYLOAD, SIGNATURE)
        ).doesNotThrowAnyException();

        Event failedEvent = new Event();
        failedEvent.setId("evt_fail_1");
        failedEvent.setType("payment_intent.payment_failed");

        when(
            webhookVerificationPort.verifyAndConstructEvent(PAYLOAD, SIGNATURE)
        ).thenReturn(failedEvent);

        assertThatCode(() ->
            useCase.processWebhook(PAYLOAD, SIGNATURE)
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName(
        "Event with null apiVersion triggers Jackson fallback parser (missing 'object' discriminator)"
    )
    void processWebhook_NullApiVersion_FallbackToJacksonParser_MissingDiscriminator()
        throws Exception {
        String jsonWithoutApiVersionOrDiscriminator = """
        {
          "id": "evt_test_null_api_ver",
          "object": "event",
          "type": "payment_intent.succeeded",
          "data": {
            "object": {
              "id": "pi_test_null_ver_123",
              "amount": 5000,
              "currency": "usd",
              "status": "succeeded",
              "metadata": {
                "registrationId": "e0000001-0000-4000-a000-000000000001",
                "workshopId": "f0000001-0000-4000-a000-000000000001"
              }
            }
          }
        }
        """;
        long now = Instant.now().getEpochSecond();
        String secret = "whsec_test_secret";
        String sig = Webhook.Util.computeHmacSha256(
            secret,
            now + "." + jsonWithoutApiVersionOrDiscriminator
        );
        Event event = Webhook.constructEvent(
            jsonWithoutApiVersionOrDiscriminator,
            "t=" + now + ",v1=" + sig,
            secret
        );
        assertThat(event.getApiVersion()).isNull();

        when(
            webhookVerificationPort.verifyAndConstructEvent(PAYLOAD, SIGNATURE)
        ).thenReturn(event);
        when(
            paymentRepository.existsByProviderPaymentId("pi_test_null_ver_123")
        ).thenReturn(false);
        when(
            registrationApi.confirmPaidRegistration(ArgumentMatchers.any())
        ).thenReturn(
            new RegistrationConfirmedDto(
                UUID.fromString("e0000001-0000-4000-a000-000000000001"),
                UUID.fromString("f0000001-0000-4000-a000-000000000001"),
                UUID.randomUUID(),
                "qr_ticket",
                java.time.Instant.now()
            )
        );

        useCase.processWebhook(PAYLOAD, SIGNATURE);

        verify(registrationApi).confirmPaidRegistration(
            new ConfirmPaidRegistrationCommand(
                UUID.fromString("e0000001-0000-4000-a000-000000000001"),
                "pi_test_null_ver_123"
            )
        );
        verify(paymentRepository).save(ArgumentMatchers.any());
    }
}
