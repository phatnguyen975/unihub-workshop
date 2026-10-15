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
import com.unihub.workshop.registrations.domain.model.Registration;
import com.unihub.workshop.registrations.domain.model.RegistrationStatus;
import com.unihub.workshop.registrations.presentation.dto.RegisterWorkshopRequest;
import com.unihub.workshop.shared.exception.ConflictException;
import com.unihub.workshop.shared.exception.ServiceUnavailableException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.shared.security.JwtAuthenticatedPrincipal;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
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

class RegistrationControllerPaidEdgeCasesTest {

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
            "student@uni.edu",
            "jti-123",
            "STUDENT",
            UUID.randomUUID().toString(),
            Instant.now(),
            Instant.now().plusSeconds(3600)
        );
        return new TestingAuthenticationToken(principal, null, "ROLE_STUDENT");
    }

    @Test
    @DisplayName(
        "FR-06, BR-01: Returns 409 Conflict when workshop remaining capacity is 0 (sold out)"
    )
    void register_ZeroCapacity_Returns409Conflict() throws Exception {
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

    @Test
    @DisplayName(
        "FR-07, BR-10: Returns 409 Conflict when student already has an active registration"
    )
    void register_DuplicateActiveRegistration_Returns409Conflict()
        throws Exception {
        when(
            registerWorkshopUseCase.register(
                eq(studentUserId),
                eq(workshopId),
                eq(idempotencyKey)
            )
        ).thenThrow(
            new ConflictException(
                "Student is already registered for this workshop."
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
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:conflict")
            )
            .andExpect(
                jsonPath("$.error.detail").value(
                    "Student is already registered for this workshop."
                )
            );
    }

    @Test
    @DisplayName(
        "FR-08, BR-11: Returns 422 Unprocessable Entity when student has an overlapping confirmed registration"
    )
    void register_ScheduleOverlap_Returns422UnprocessableEntity()
        throws Exception {
        when(
            registerWorkshopUseCase.register(
                eq(studentUserId),
                eq(workshopId),
                eq(idempotencyKey)
            )
        ).thenThrow(
            new ValidationException(
                "Schedule conflict: you have an overlapping active registration"
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
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            )
            .andExpect(
                jsonPath("$.error.detail").value(
                    "Schedule conflict: you have an overlapping active registration"
                )
            );
    }

    @Test
    @DisplayName(
        "FR-14, BR-13: Returns 503 Service Unavailable when Stripe call fails"
    )
    void register_StripeFailure_Returns503ServiceUnavailableWithRetryAfterHeader()
        throws Exception {
        when(
            registerWorkshopUseCase.register(
                eq(studentUserId),
                eq(workshopId),
                eq(idempotencyKey)
            )
        ).thenThrow(
            new ServiceUnavailableException(
                "Payment gateway service unavailable. Please retry later.",
                30
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
            .andExpect(status().isServiceUnavailable())
            .andExpect(header().string(HttpHeaders.RETRY_AFTER, "30"))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value(
                    "urn:unihub:error:service-unavailable"
                )
            )
            .andExpect(
                jsonPath("$.error.detail").value(
                    "Payment gateway service unavailable. Please retry later."
                )
            );
    }

    @Test
    @DisplayName(
        "FR-15, BR-13: Returns 503 Service Unavailable when Resilience4j CircuitBreaker is OPEN"
    )
    void register_CircuitBreakerOpen_Returns503ServiceUnavailable()
        throws Exception {
        CircuitBreaker cb = CircuitBreaker.ofDefaults("stripe-payment");
        cb.transitionToOpenState();
        CallNotPermittedException openEx =
            CallNotPermittedException.createCallNotPermittedException(cb);

        when(
            registerWorkshopUseCase.register(
                eq(studentUserId),
                eq(workshopId),
                eq(idempotencyKey)
            )
        ).thenThrow(openEx);

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
            .andExpect(status().isServiceUnavailable())
            .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value(
                    "urn:unihub:error:service-unavailable"
                )
            );
    }

    @Test
    @DisplayName(
        "FR-04, FR-12: Resending request with same Idempotency-Key returns cached 201 Created with PENDING status"
    )
    void register_IdempotentReplay_Returns201CreatedWithCachedPendingRegistration()
        throws Exception {
        UUID registrationId = UUID.randomUUID();
        Instant now = Instant.now();

        Registration cachedPending = Registration.builder()
            .id(registrationId)
            .workshopId(workshopId)
            .studentUserId(studentUserId)
            .status(RegistrationStatus.PENDING)
            .priceAtRegistration(new BigDecimal("50.00"))
            .currency("USD")
            .idempotencyKeyId(UUID.fromString(idempotencyKey))
            .paymentClientSecret("pi_test_secret_replayed")
            .expiresAt(now.plusSeconds(1800))
            .createdAt(now)
            .build();

        when(
            registerWorkshopUseCase.register(
                eq(studentUserId),
                eq(workshopId),
                eq(idempotencyKey)
            )
        ).thenReturn(cachedPending);

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
                    "pi_test_secret_replayed"
                )
            )
            .andExpect(jsonPath("$.data.qrPayload").doesNotExist())
            .andExpect(jsonPath("$.data.qrSignature").doesNotExist())
            .andExpect(jsonPath("$.data.qrIssuedAt").doesNotExist());
    }
}
