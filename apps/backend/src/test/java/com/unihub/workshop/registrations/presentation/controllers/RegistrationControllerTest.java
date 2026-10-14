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
import com.unihub.workshop.registrations.domain.model.QrTicket;
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

class RegistrationControllerTest {

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
            null,
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
        "POST /api/v1/registrations: returns 201 Created with valid free registration"
    )
    void register_ValidFreeRegistration_Returns201Created() throws Exception {
        UUID registrationId = UUID.randomUUID();
        Instant now = Instant.now();

        Registration registration = Registration.builder()
            .id(registrationId)
            .workshopId(workshopId)
            .studentUserId(studentUserId)
            .status(RegistrationStatus.CONFIRMED)
            .priceAtRegistration(BigDecimal.ZERO)
            .currency("USD")
            .idempotencyKeyId(idempotencyKey)
            .qrTicket(
                new QrTicket(
                    "{\"regId\":\"" + registrationId + "\"}",
                    "sig123",
                    now
                )
            )
            .createdAt(now)
            .build();

        when(
            registerWorkshopUseCase.register(
                eq(studentUserId),
                eq(workshopId),
                eq(idempotencyKey.toString())
            )
        ).thenReturn(registration);

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
            .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
            .andExpect(jsonPath("$.data.qrPayload").isNotEmpty())
            .andExpect(jsonPath("$.data.qrSignature").value("sig123"))
            .andExpect(jsonPath("$.data.qrIssuedAt").isNotEmpty());

        verify(registerWorkshopUseCase).register(
            studentUserId,
            workshopId,
            idempotencyKey.toString()
        );
    }

    @Test
    @DisplayName(
        "POST /api/v1/registrations: returns 422 Unprocessable Entity when Idempotency-Key header is missing"
    )
    void register_MissingIdempotencyKey_Returns422() throws Exception {
        RegisterWorkshopRequest request = new RegisterWorkshopRequest(
            workshopId
        );

        mockMvc
            .perform(
                post("/api/v1/registrations")
                    .principal(createAuth(studentUserId, "STUDENT"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName(
        "POST /api/v1/registrations: returns 422 Unprocessable Entity when Idempotency-Key header is malformed"
    )
    void register_MalformedIdempotencyKey_Returns422() throws Exception {
        RegisterWorkshopRequest request = new RegisterWorkshopRequest(
            workshopId
        );

        mockMvc
            .perform(
                post("/api/v1/registrations")
                    .principal(createAuth(studentUserId, "STUDENT"))
                    .header("Idempotency-Key", "not-a-valid-uuid")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName(
        "POST /api/v1/registrations: returns 422 Unprocessable Entity when workshopId is null"
    )
    void register_NullWorkshopId_Returns422() throws Exception {
        mockMvc
            .perform(
                post("/api/v1/registrations")
                    .principal(createAuth(studentUserId, "STUDENT"))
                    .header("Idempotency-Key", idempotencyKey.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName(
        "POST /api/v1/registrations: returns 403 Forbidden when user is not STUDENT"
    )
    void register_NonStudentRole_Returns403() throws Exception {
        RegisterWorkshopRequest request = new RegisterWorkshopRequest(
            workshopId
        );

        mockMvc
            .perform(
                post("/api/v1/registrations")
                    .principal(createAuth(studentUserId, "ORGANIZER"))
                    .header("Idempotency-Key", idempotencyKey.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false));
    }
}
