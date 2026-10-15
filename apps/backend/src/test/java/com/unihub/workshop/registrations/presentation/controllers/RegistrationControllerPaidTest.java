package com.unihub.workshop.registrations.presentation.controllers;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.unihub.workshop.registrations.application.ports.in.RegisterWorkshopUseCase;
import com.unihub.workshop.registrations.domain.model.Registration;
import com.unihub.workshop.registrations.domain.model.RegistrationStatus;
import com.unihub.workshop.registrations.presentation.dto.RegisterWorkshopRequest;
import com.unihub.workshop.shared.security.JwtAuthenticatedPrincipal;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RegistrationControllerPaidTest {

    private MockMvc mockMvc;
    private RegisterWorkshopUseCase registerWorkshopUseCase;
    private ObjectMapper objectMapper;

    private final UUID studentUserId = UUID.randomUUID();
    private final UUID workshopId = UUID.randomUUID();
    private final UUID idempotencyKey = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        registerWorkshopUseCase = mock(RegisterWorkshopUseCase.class);
        RegistrationController controller =
            RegistrationControllerTestBuilder.builder()
                .withRegisterWorkshopUseCase(registerWorkshopUseCase)
                .build();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(
                new GlobalExceptionHandler(),
                new GlobalResponseBodyAdvice()
            )
            .build();

        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    private TestingAuthenticationToken createAuth(UUID userId, String role) {
        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
            userId.toString(),
            "student@uni.edu",
            "jti-123",
            role,
            UUID.randomUUID().toString(),
            Instant.now(),
            Instant.now().plusSeconds(3600)
        );
        return new TestingAuthenticationToken(principal, null, "ROLE_" + role);
    }

    @Test
    @DisplayName(
        "POST /api/v1/registrations: Returns 201 Created with PENDING status, client secret, and omitted QR fields for paid workshop"
    )
    void register_PaidWorkshop_Returns201PendingWithClientSecret()
        throws Exception {
        UUID registrationId = UUID.randomUUID();
        Instant now = Instant.now();

        Registration pendingRegistration = Registration.builder()
            .id(registrationId)
            .workshopId(workshopId)
            .studentUserId(studentUserId)
            .status(RegistrationStatus.PENDING)
            .priceAtRegistration(new BigDecimal("50.00"))
            .currency("USD")
            .idempotencyKeyId(idempotencyKey)
            .paymentClientSecret("pi_3MtwxAEhLOAGSuTX2xHNulnv_secret_987654")
            .qrTicket(null)
            .createdAt(now)
            .updatedAt(now)
            .createdBy(studentUserId)
            .updatedBy(studentUserId)
            .build();

        when(
            registerWorkshopUseCase.register(
                eq(studentUserId),
                eq(workshopId),
                eq(idempotencyKey.toString())
            )
        ).thenReturn(pendingRegistration);

        RegisterWorkshopRequest request = new RegisterWorkshopRequest(
            workshopId
        );

        mockMvc
            .perform(
                post("/api/v1/registrations")
                    .principal(createAuth(studentUserId, "STUDENT"))
                    .header("Idempotency-Key", idempotencyKey.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(registrationId.toString()))
            .andExpect(
                jsonPath("$.data.workshopId").value(workshopId.toString())
            )
            .andExpect(
                jsonPath("$.data.studentId").value(studentUserId.toString())
            )
            .andExpect(jsonPath("$.data.status").value("PENDING"))
            .andExpect(jsonPath("$.data.priceAtRegistration").value(50.00))
            .andExpect(jsonPath("$.data.currency").value("USD"))
            .andExpect(
                jsonPath("$.data.paymentClientSecret").value(
                    "pi_3MtwxAEhLOAGSuTX2xHNulnv_secret_987654"
                )
            )
            .andExpect(jsonPath("$.data.qrPayload").doesNotExist())
            .andExpect(jsonPath("$.data.qrSignature").doesNotExist())
            .andExpect(jsonPath("$.data.qrIssuedAt").doesNotExist());

        verify(registerWorkshopUseCase).register(
            eq(studentUserId),
            eq(workshopId),
            eq(idempotencyKey.toString())
        );
    }
}
