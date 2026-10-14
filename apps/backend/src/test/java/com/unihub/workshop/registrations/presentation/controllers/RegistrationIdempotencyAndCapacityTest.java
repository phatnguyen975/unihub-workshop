package com.unihub.workshop.registrations.presentation.controllers;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.unihub.workshop.registrations.application.ports.in.RegisterWorkshopUseCase;
import com.unihub.workshop.registrations.domain.model.QrTicket;
import com.unihub.workshop.registrations.domain.model.Registration;
import com.unihub.workshop.registrations.domain.model.RegistrationStatus;
import com.unihub.workshop.registrations.presentation.dto.RegisterWorkshopRequest;
import com.unihub.workshop.shared.exception.ConflictException;
import com.unihub.workshop.shared.idempotency.IdempotencyConflictException;
import com.unihub.workshop.shared.security.JwtAuthenticatedPrincipal;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RegistrationIdempotencyAndCapacityTest {

    private MockMvc mockMvc;
    private RegisterWorkshopUseCase registerWorkshopUseCase;
    private ObjectMapper objectMapper;

    private final UUID studentUserId = UUID.randomUUID();
    private final UUID workshopId = UUID.randomUUID();
    private final String idempotencyKey = UUID.randomUUID().toString();

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

    private TestingAuthenticationToken createStudentAuth(UUID userId) {
        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
            userId.toString(),
            null,
            "jti-test",
            "STUDENT",
            UUID.randomUUID().toString(),
            Instant.now(),
            Instant.now().plusSeconds(3600)
        );
        return new TestingAuthenticationToken(principal, null, "ROLE_STUDENT");
    }

    @Test
    @DisplayName(
        "FR-03: Missing Idempotency-Key header returns 422 Unprocessable Entity"
    )
    void register_MissingIdempotencyKey_Returns422() throws Exception {
        RegisterWorkshopRequest request = new RegisterWorkshopRequest(
            workshopId
        );

        mockMvc
            .perform(
                post("/api/v1/registrations")
                    .principal(createStudentAuth(studentUserId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            );
    }

    @Test
    @DisplayName(
        "FR-03: Malformed Idempotency-Key header returns 422 Unprocessable Entity"
    )
    void register_MalformedIdempotencyKey_Returns422() throws Exception {
        RegisterWorkshopRequest request = new RegisterWorkshopRequest(
            workshopId
        );

        mockMvc
            .perform(
                post("/api/v1/registrations")
                    .principal(createStudentAuth(studentUserId))
                    .header("Idempotency-Key", "invalid-not-uuid")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            );
    }

    @Test
    @DisplayName(
        "FR-04, BR-03: In-flight request with same Idempotency-Key returns 409 Conflict with Retry-After: 2"
    )
    void register_InFlightIdempotencyConflict_Returns409WithRetryAfterHeader()
        throws Exception {
        when(
            registerWorkshopUseCase.register(
                eq(studentUserId),
                eq(workshopId),
                eq(idempotencyKey)
            )
        ).thenThrow(
            new IdempotencyConflictException(
                "A request with this Idempotency-Key is already in progress. Retry after 2 seconds.",
                2
            )
        );

        RegisterWorkshopRequest request = new RegisterWorkshopRequest(
            workshopId
        );

        mockMvc
            .perform(
                post("/api/v1/registrations")
                    .principal(createStudentAuth(studentUserId))
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
            .andExpect(status().isConflict())
            .andExpect(header().string(HttpHeaders.RETRY_AFTER, "2"))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:conflict")
            )
            .andExpect(
                jsonPath("$.error.detail").value(
                    "A request with this Idempotency-Key is already in progress. Retry after 2 seconds."
                )
            );
    }

    @Test
    @DisplayName(
        "FR-05, BR-04: Replayed idempotent request returns 201 Created with cached payload"
    )
    void register_CompletedIdempotentReplay_Returns201Created()
        throws Exception {
        UUID registrationId = UUID.randomUUID();
        Instant now = Instant.now();

        Registration cached = Registration.builder()
            .id(registrationId)
            .workshopId(workshopId)
            .studentUserId(studentUserId)
            .status(RegistrationStatus.CONFIRMED)
            .priceAtRegistration(BigDecimal.ZERO)
            .currency("USD")
            .idempotencyKeyId(UUID.randomUUID())
            .qrTicket(
                new QrTicket(
                    "{\"regId\":\"" + registrationId + "\"}",
                    "cachedSig",
                    now
                )
            )
            .createdAt(now)
            .build();

        when(
            registerWorkshopUseCase.register(
                eq(studentUserId),
                eq(workshopId),
                eq(idempotencyKey)
            )
        ).thenReturn(cached);

        RegisterWorkshopRequest request = new RegisterWorkshopRequest(
            workshopId
        );

        mockMvc
            .perform(
                post("/api/v1/registrations")
                    .principal(createStudentAuth(studentUserId))
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(registrationId.toString()))
            .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
            .andExpect(jsonPath("$.data.qrSignature").value("cachedSig"));
    }

    @Test
    @DisplayName(
        "FR-11, BR-08: Workshop capacity exhausted returns 409 Conflict with appropriate detail"
    )
    void register_CapacityExhausted_Returns409Conflict() throws Exception {
        when(
            registerWorkshopUseCase.register(
                eq(studentUserId),
                eq(workshopId),
                eq(idempotencyKey)
            )
        ).thenThrow(
            new ConflictException("No seats remaining for this workshop.")
        );

        RegisterWorkshopRequest request = new RegisterWorkshopRequest(
            workshopId
        );

        mockMvc
            .perform(
                post("/api/v1/registrations")
                    .principal(createStudentAuth(studentUserId))
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:conflict")
            )
            .andExpect(
                jsonPath("$.error.detail").value(
                    "No seats remaining for this workshop."
                )
            );
    }
}
