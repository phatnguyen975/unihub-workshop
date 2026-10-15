package com.unihub.workshop.payments.presentation.controllers;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unihub.workshop.payments.application.ports.in.ProcessPaymentWebhookUseCase;
import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PaymentWebhookControllerTest {

    private MockMvc mockMvc;
    private ProcessPaymentWebhookUseCase processPaymentWebhookUseCase;

    private static final String SAMPLE_PAYLOAD = """
    {
      "id": "evt_test_123",
      "type": "customer.subscription.created",
      "data": { "object": { "id": "sub_123" } }
    }
    """;

    @BeforeEach
    void setUp() {
        processPaymentWebhookUseCase = mock(ProcessPaymentWebhookUseCase.class);

        PaymentWebhookController controller = new PaymentWebhookController(
            processPaymentWebhookUseCase
        );

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(
                new GlobalExceptionHandler(),
                new GlobalResponseBodyAdvice()
            )
            .build();
    }

    @Test
    @DisplayName(
        "FR-18: Missing Stripe-Signature header returns 400 Bad Request"
    )
    void handleWebhook_MissingSignature_Returns400BadRequest()
        throws Exception {
        doThrow(new BadRequestException("Missing Stripe-Signature header"))
            .when(processPaymentWebhookUseCase)
            .processWebhook(eq(SAMPLE_PAYLOAD), eq(null));

        mockMvc
            .perform(
                post("/api/v1/payments/webhook")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(SAMPLE_PAYLOAD)
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:bad-request")
            )
            .andExpect(
                jsonPath("$.error.detail").value(
                    "Missing Stripe-Signature header"
                )
            );

        verify(processPaymentWebhookUseCase).processWebhook(
            SAMPLE_PAYLOAD,
            null
        );
    }

    @Test
    @DisplayName(
        "FR-18: Invalid/Malformed Stripe-Signature header returns 400 Bad Request"
    )
    void handleWebhook_InvalidSignature_Returns400BadRequest()
        throws Exception {
        doThrow(new BadRequestException("Invalid webhook signature"))
            .when(processPaymentWebhookUseCase)
            .processWebhook(eq(SAMPLE_PAYLOAD), eq("invalid_sig"));

        mockMvc
            .perform(
                post("/api/v1/payments/webhook")
                    .header("Stripe-Signature", "invalid_sig")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(SAMPLE_PAYLOAD)
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:bad-request")
            )
            .andExpect(
                jsonPath("$.error.detail").value("Invalid webhook signature")
            );

        verify(processPaymentWebhookUseCase).processWebhook(
            SAMPLE_PAYLOAD,
            "invalid_sig"
        );
    }

    @Test
    @DisplayName("FR-19, FR-26, BR-06: Valid signature returns 200 OK")
    void handleWebhook_ValidSignature_Returns200Ok() throws Exception {
        doNothing()
            .when(processPaymentWebhookUseCase)
            .processWebhook(eq(SAMPLE_PAYLOAD), eq("valid_sig"));

        mockMvc
            .perform(
                post("/api/v1/payments/webhook")
                    .header("Stripe-Signature", "valid_sig")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(SAMPLE_PAYLOAD)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Webhook received."))
            .andExpect(jsonPath("$.data").doesNotExist());

        verify(processPaymentWebhookUseCase).processWebhook(
            SAMPLE_PAYLOAD,
            "valid_sig"
        );
    }
}
