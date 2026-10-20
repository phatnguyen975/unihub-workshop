package com.unihub.workshop.registrations.presentation.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.unihub.workshop.registrations.application.ports.in.CancelRegistrationCommand;
import com.unihub.workshop.registrations.application.ports.in.CancelRegistrationUseCase;
import com.unihub.workshop.registrations.domain.model.CancelledRegistrationResult;
import com.unihub.workshop.registrations.domain.model.Registration;
import com.unihub.workshop.registrations.domain.model.RegistrationStatus;
import com.unihub.workshop.registrations.presentation.dto.CancelRegistrationRequest;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.exception.ValidationException;
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
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RegistrationControllerCancelTest {

    private MockMvc mockMvc;
    private CancelRegistrationUseCase cancelRegistrationUseCase;
    private ObjectMapper objectMapper;

    private final UUID studentUserId = UUID.randomUUID();
    private final UUID organizerUserId = UUID.randomUUID();
    private final UUID organizationId = UUID.randomUUID();
    private final UUID workshopId = UUID.randomUUID();
    private final UUID registrationId = UUID.randomUUID();
    private final UUID idempotencyKey = UUID.randomUUID();
    private final Instant now = Instant.now();

    @BeforeEach
    void setUp() {
        cancelRegistrationUseCase = mock(CancelRegistrationUseCase.class);

        RegistrationController controller =
            RegistrationControllerTestBuilder.builder()
                .withCancelRegistrationUseCase(cancelRegistrationUseCase)
                .build();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(
                new GlobalExceptionHandler(),
                new GlobalResponseBodyAdvice()
            )
            .build();

        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    private TestingAuthenticationToken createAuth(
        UUID userId,
        String role,
        UUID orgId
    ) {
        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
            userId.toString(),
            orgId != null ? orgId.toString() : null,
            "jti-cancel-test",
            role,
            UUID.randomUUID().toString(),
            Instant.now(),
            Instant.now().plusSeconds(3600)
        );
        return new TestingAuthenticationToken(principal, null, "ROLE_" + role);
    }

    private CancelledRegistrationResult createFreeCancelledResult() {
        Registration reg = Registration.builder()
            .id(registrationId)
            .workshopId(workshopId)
            .studentUserId(studentUserId)
            .status(RegistrationStatus.CANCELED)
            .priceAtRegistration(BigDecimal.ZERO)
            .currency("USD")
            .idempotencyKeyId(idempotencyKey)
            .createdAt(now.minusSeconds(7200))
            .updatedAt(now)
            .build();

        return new CancelledRegistrationResult(reg, null, null);
    }

    @Test
    @DisplayName(
        "POST /api/v1/registrations/{id}/cancel: returns 200 OK when student cancels own free registration"
    )
    void cancel_WhenStudentValidFreeRegistration_Returns200Ok()
        throws Exception {
        CancelledRegistrationResult result = createFreeCancelledResult();
        when(
            cancelRegistrationUseCase.cancel(
                any(CancelRegistrationCommand.class)
            )
        ).thenReturn(result);

        CancelRegistrationRequest request = new CancelRegistrationRequest(
            "Change of plans"
        );

        mockMvc
            .perform(
                post("/api/v1/registrations/{id}/cancel", registrationId)
                    .principal(createAuth(studentUserId, "STUDENT", null))
                    .header("Idempotency-Key", idempotencyKey.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Registration canceled"))
            .andExpect(jsonPath("$.data.id").value(registrationId.toString()))
            .andExpect(
                jsonPath("$.data.workshopId").value(workshopId.toString())
            )
            .andExpect(
                jsonPath("$.data.studentId").value(studentUserId.toString())
            )
            .andExpect(jsonPath("$.data.status").value("CANCELED"))
            .andExpect(jsonPath("$.data.priceAtRegistration").value(0.0))
            .andExpect(jsonPath("$.data.currency").value("USD"))
            .andExpect(jsonPath("$.data.refund").doesNotExist())
            .andExpect(jsonPath("$.data.qrPayload").doesNotExist())
            .andExpect(jsonPath("$.data.qrSignature").doesNotExist())
            .andExpect(jsonPath("$.data.qrIssuedAt").doesNotExist())
            .andExpect(jsonPath("$.data.paymentClientSecret").doesNotExist());

        ArgumentCaptor<CancelRegistrationCommand> captor =
            ArgumentCaptor.forClass(CancelRegistrationCommand.class);
        verify(cancelRegistrationUseCase).cancel(captor.capture());

        CancelRegistrationCommand command = captor.getValue();
        assertEquals(registrationId, command.registrationId());
        assertEquals(studentUserId, command.callerUserId());
        assertEquals("STUDENT", command.callerRole());
        assertNull(command.callerOrganizationId());
        assertEquals(idempotencyKey.toString(), command.idempotencyKey());
        assertEquals("Change of plans", command.reason());
    }

    @Test
    @DisplayName(
        "POST /api/v1/registrations/{id}/cancel: returns 200 OK when organizer cancels registration"
    )
    void cancel_WhenOrganizerValid_Returns200Ok() throws Exception {
        CancelledRegistrationResult result = createFreeCancelledResult();
        when(
            cancelRegistrationUseCase.cancel(
                any(CancelRegistrationCommand.class)
            )
        ).thenReturn(result);

        mockMvc
            .perform(
                post("/api/v1/registrations/{id}/cancel", registrationId)
                    .principal(
                        createAuth(organizerUserId, "ORGANIZER", organizationId)
                    )
                    .header("Idempotency-Key", idempotencyKey.toString())
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("CANCELED"));

        ArgumentCaptor<CancelRegistrationCommand> captor =
            ArgumentCaptor.forClass(CancelRegistrationCommand.class);
        verify(cancelRegistrationUseCase).cancel(captor.capture());

        CancelRegistrationCommand command = captor.getValue();
        assertEquals(organizerUserId, command.callerUserId());
        assertEquals("ORGANIZER", command.callerRole());
        assertEquals(organizationId, command.callerOrganizationId());
        assertNull(command.reason());
    }

    @Test
    @DisplayName(
        "POST /api/v1/registrations/{id}/cancel: returns 400 Bad Request when id is not a valid UUID"
    )
    void cancel_WhenInvalidUuidInPath_Returns400BadRequest() throws Exception {
        mockMvc
            .perform(
                post("/api/v1/registrations/{id}/cancel", "not-a-valid-uuid")
                    .principal(createAuth(studentUserId, "STUDENT", null))
                    .header("Idempotency-Key", idempotencyKey.toString())
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:bad-request")
            );
    }

    @Test
    @DisplayName(
        "POST /api/v1/registrations/{id}/cancel: returns 401 Unauthorized when unauthenticated"
    )
    void cancel_WhenUnauthenticated_Returns401Unauthorized() throws Exception {
        mockMvc
            .perform(
                post(
                    "/api/v1/registrations/{id}/cancel",
                    registrationId
                ).header("Idempotency-Key", idempotencyKey.toString())
            )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:unauthorized")
            );
    }

    @Test
    @DisplayName(
        "POST /api/v1/registrations/{id}/cancel: returns 403 Forbidden when role is STAFF"
    )
    void cancel_WhenRoleIsStaff_Returns403Forbidden() throws Exception {
        mockMvc
            .perform(
                post("/api/v1/registrations/{id}/cancel", registrationId)
                    .principal(createAuth(studentUserId, "STAFF", null))
                    .header("Idempotency-Key", idempotencyKey.toString())
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:forbidden")
            );
    }

    @Test
    @DisplayName(
        "POST /api/v1/registrations/{id}/cancel: returns 403 Forbidden when student does not own registration"
    )
    void cancel_WhenStudentNotOwner_Returns403Forbidden() throws Exception {
        when(
            cancelRegistrationUseCase.cancel(
                any(CancelRegistrationCommand.class)
            )
        ).thenThrow(
            new ForbiddenException(
                "Access denied: You can only cancel your own registrations"
            )
        );

        mockMvc
            .perform(
                post("/api/v1/registrations/{id}/cancel", registrationId)
                    .principal(createAuth(studentUserId, "STUDENT", null))
                    .header("Idempotency-Key", idempotencyKey.toString())
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:forbidden")
            );
    }

    @Test
    @DisplayName(
        "POST /api/v1/registrations/{id}/cancel: returns 403 Forbidden when organizer is missing org claim"
    )
    void cancel_WhenOrganizerMissingOrgClaim_Returns403Forbidden()
        throws Exception {
        mockMvc
            .perform(
                post("/api/v1/registrations/{id}/cancel", registrationId)
                    .principal(createAuth(organizerUserId, "ORGANIZER", null))
                    .header("Idempotency-Key", idempotencyKey.toString())
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:forbidden")
            );
    }

    @Test
    @DisplayName(
        "POST /api/v1/registrations/{id}/cancel: returns 404 Not Found when registration does not exist"
    )
    void cancel_WhenRegistrationNotFound_Returns404NotFound() throws Exception {
        when(
            cancelRegistrationUseCase.cancel(
                any(CancelRegistrationCommand.class)
            )
        ).thenThrow(
            new NotFoundException("Registration not found: " + registrationId)
        );

        mockMvc
            .perform(
                post("/api/v1/registrations/{id}/cancel", registrationId)
                    .principal(createAuth(studentUserId, "STUDENT", null))
                    .header("Idempotency-Key", idempotencyKey.toString())
            )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:not-found")
            );
    }

    @Test
    @DisplayName(
        "POST /api/v1/registrations/{id}/cancel: returns 409 Conflict with Retry-After when key is in-flight"
    )
    void cancel_WhenIdempotencyConflictInFlight_Returns409WithRetryAfter()
        throws Exception {
        when(
            cancelRegistrationUseCase.cancel(
                any(CancelRegistrationCommand.class)
            )
        ).thenThrow(
            new IdempotencyConflictException(
                "A request with the same idempotency key is currently in flight",
                2
            )
        );

        mockMvc
            .perform(
                post("/api/v1/registrations/{id}/cancel", registrationId)
                    .principal(createAuth(studentUserId, "STUDENT", null))
                    .header("Idempotency-Key", idempotencyKey.toString())
            )
            .andExpect(status().isConflict())
            .andExpect(header().string(HttpHeaders.RETRY_AFTER, "2"))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:conflict")
            );
    }

    @Test
    @DisplayName(
        "POST /api/v1/registrations/{id}/cancel: returns 422 Unprocessable Content when Idempotency-Key is missing"
    )
    void cancel_WhenMissingIdempotencyKey_Returns422Unprocessable()
        throws Exception {
        mockMvc
            .perform(
                post(
                    "/api/v1/registrations/{id}/cancel",
                    registrationId
                ).principal(createAuth(studentUserId, "STUDENT", null))
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            );
    }

    @Test
    @DisplayName(
        "POST /api/v1/registrations/{id}/cancel: returns 422 Unprocessable Content when Idempotency-Key is malformed"
    )
    void cancel_WhenMalformedIdempotencyKey_Returns422Unprocessable()
        throws Exception {
        mockMvc
            .perform(
                post("/api/v1/registrations/{id}/cancel", registrationId)
                    .principal(createAuth(studentUserId, "STUDENT", null))
                    .header("Idempotency-Key", "not-a-uuid")
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            );
    }

    @Test
    @DisplayName(
        "POST /api/v1/registrations/{id}/cancel: returns 422 Unprocessable Content when reason exceeds 1000 chars"
    )
    void cancel_WhenReasonExceeds1000Chars_Returns422Unprocessable()
        throws Exception {
        String longReason = "a".repeat(1001);
        CancelRegistrationRequest request = new CancelRegistrationRequest(
            longReason
        );

        mockMvc
            .perform(
                post("/api/v1/registrations/{id}/cancel", registrationId)
                    .principal(createAuth(studentUserId, "STUDENT", null))
                    .header("Idempotency-Key", idempotencyKey.toString())
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
        "POST /api/v1/registrations/{id}/cancel: returns 422 Unprocessable Content when already canceled"
    )
    void cancel_WhenAlreadyCanceled_Returns422Unprocessable() throws Exception {
        when(
            cancelRegistrationUseCase.cancel(
                any(CancelRegistrationCommand.class)
            )
        ).thenThrow(
            new ValidationException(
                "status",
                "Registration is already canceled"
            )
        );

        mockMvc
            .perform(
                post("/api/v1/registrations/{id}/cancel", registrationId)
                    .principal(createAuth(studentUserId, "STUDENT", null))
                    .header("Idempotency-Key", idempotencyKey.toString())
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            )
            .andExpect(
                jsonPath("$.error.detail").value(
                    "Registration is already canceled"
                )
            );
    }

    @Test
    @DisplayName(
        "POST /api/v1/registrations/{id}/cancel: returns 422 Unprocessable Content when workshop already started"
    )
    void cancel_WhenWorkshopAlreadyStarted_Returns422Unprocessable()
        throws Exception {
        when(
            cancelRegistrationUseCase.cancel(
                any(CancelRegistrationCommand.class)
            )
        ).thenThrow(
            new ValidationException(
                "startsAt",
                "Cannot cancel registration for a workshop that has already started or ended"
            )
        );

        mockMvc
            .perform(
                post("/api/v1/registrations/{id}/cancel", registrationId)
                    .principal(createAuth(studentUserId, "STUDENT", null))
                    .header("Idempotency-Key", idempotencyKey.toString())
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            );
    }
}
