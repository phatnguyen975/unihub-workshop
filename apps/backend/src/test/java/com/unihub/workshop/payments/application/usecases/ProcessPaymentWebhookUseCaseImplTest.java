package com.unihub.workshop.payments.application.usecases;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stripe.model.Event;
import com.unihub.workshop.payments.application.ports.out.WebhookVerificationPort;
import com.unihub.workshop.shared.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProcessPaymentWebhookUseCaseImplTest {

    private WebhookVerificationPort webhookVerificationPort;
    private ProcessPaymentWebhookUseCaseImpl useCase;

    private static final String PAYLOAD = "{\"id\":\"evt_123\"}";
    private static final String SIGNATURE = "valid_sig";

    @BeforeEach
    void setUp() {
        webhookVerificationPort = mock(WebhookVerificationPort.class);
        useCase = new ProcessPaymentWebhookUseCaseImpl(webhookVerificationPort);
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
        "Always-200 contract: post-verification dispatch exception is caught and suppressed"
    )
    void processWebhook_DispatchThrowsException_DoesNotPropagate() {
        Event event = new Event() {
            @Override
            public String getType() {
                throw new RuntimeException("Unexpected parsing failure");
            }
        };
        event.setId("evt_err");

        when(
            webhookVerificationPort.verifyAndConstructEvent(PAYLOAD, SIGNATURE)
        ).thenReturn(event);

        assertThatCode(() ->
            useCase.processWebhook(PAYLOAD, SIGNATURE)
        ).doesNotThrowAnyException();
    }
}
