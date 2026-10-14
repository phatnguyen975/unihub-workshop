package com.unihub.workshop.registrations.presentation.controllers;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unihub.workshop.registrations.application.ports.in.GetRegistrationDetailUseCase;
import com.unihub.workshop.registrations.domain.model.RegistrationDetail;
import com.unihub.workshop.registrations.domain.model.RegistrationStatus;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.security.JwtAuthenticatedPrincipal;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class GetRegistrationDetailControllerTest {

    private MockMvc mockMvc;
    private GetRegistrationDetailUseCase getRegistrationDetailUseCase;

    private final UUID studentUserId = UUID.randomUUID();
    private final UUID registrationId = UUID.randomUUID();
    private final UUID workshopId = UUID.randomUUID();
    private final Instant now = Instant.now();

    @BeforeEach
    void setUp() {
        getRegistrationDetailUseCase = mock(GetRegistrationDetailUseCase.class);

        RegistrationController controller =
            RegistrationControllerTestBuilder.builder()
                .withGetRegistrationDetailUseCase(getRegistrationDetailUseCase)
                .build();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(
                new GlobalExceptionHandler(),
                new GlobalResponseBodyAdvice()
            )
            .build();
    }

    private TestingAuthenticationToken createAuth(UUID userId, String role) {
        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
            userId.toString(),
            null,
            "jti-test",
            role,
            UUID.randomUUID().toString(),
            Instant.now(),
            Instant.now().plusSeconds(3600)
        );
        return new TestingAuthenticationToken(principal, null, "ROLE_" + role);
    }

    @Test
    @DisplayName(
        "GET /api/v1/registrations/{id}: returns 200 OK with full details and QR ticket when status is CONFIRMED"
    )
    void getRegistrationDetail_whenConfirmedAndOwner_returns200WithFullDetailsAndQr()
        throws Exception {
        RegistrationDetail detail = RegistrationDetail.builder()
            .id(registrationId)
            .workshopId(workshopId)
            .workshopTitle("Career Sprint 101")
            .workshopStartsAt(now.plusSeconds(3600))
            .workshopEndsAt(now.plusSeconds(7200))
            .workshopLocation("Room B4-101")
            .studentId(studentUserId)
            .status(RegistrationStatus.CONFIRMED)
            .createdAt(now)
            .qrPayload("{\"regId\":\"" + registrationId + "\"}")
            .qrSignature("signature-data-xyz")
            .qrIssuedAt(now)
            .build();

        when(
            getRegistrationDetailUseCase.getRegistrationDetail(
                studentUserId,
                registrationId
            )
        ).thenReturn(detail);

        mockMvc
            .perform(
                get("/api/v1/registrations/{id}", registrationId).principal(
                    createAuth(studentUserId, "STUDENT")
                )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Registration retrieved"))
            .andExpect(jsonPath("$.data.id").value(registrationId.toString()))
            .andExpect(
                jsonPath("$.data.workshopId").value(workshopId.toString())
            )
            .andExpect(
                jsonPath("$.data.workshopTitle").value("Career Sprint 101")
            )
            .andExpect(jsonPath("$.data.workshopLocation").value("Room B4-101"))
            .andExpect(
                jsonPath("$.data.studentId").value(studentUserId.toString())
            )
            .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
            .andExpect(
                jsonPath("$.data.qrPayload").value(
                    "{\"regId\":\"" + registrationId + "\"}"
                )
            )
            .andExpect(
                jsonPath("$.data.qrSignature").value("signature-data-xyz")
            )
            .andExpect(jsonPath("$.data.qrIssuedAt").isNotEmpty());

        verify(getRegistrationDetailUseCase).getRegistrationDetail(
            studentUserId,
            registrationId
        );
    }

    @Test
    @DisplayName(
        "GET /api/v1/registrations/{id}: returns 200 OK with QR fields omitted when status is non-CONFIRMED"
    )
    void getRegistrationDetail_whenCanceledAndOwner_returns200WithoutQrFields()
        throws Exception {
        RegistrationDetail detail = RegistrationDetail.builder()
            .id(registrationId)
            .workshopId(workshopId)
            .workshopTitle("Career Sprint 101")
            .workshopStartsAt(now.plusSeconds(3600))
            .workshopEndsAt(now.plusSeconds(7200))
            .workshopLocation("Room B4-101")
            .studentId(studentUserId)
            .status(RegistrationStatus.CANCELED)
            .createdAt(now)
            .qrPayload(null)
            .qrSignature(null)
            .qrIssuedAt(null)
            .build();

        when(
            getRegistrationDetailUseCase.getRegistrationDetail(
                studentUserId,
                registrationId
            )
        ).thenReturn(detail);

        mockMvc
            .perform(
                get("/api/v1/registrations/{id}", registrationId).principal(
                    createAuth(studentUserId, "STUDENT")
                )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Registration retrieved"))
            .andExpect(jsonPath("$.data.id").value(registrationId.toString()))
            .andExpect(
                jsonPath("$.data.workshopId").value(workshopId.toString())
            )
            .andExpect(jsonPath("$.data.status").value("CANCELED"))
            .andExpect(jsonPath("$.data.qrPayload").doesNotExist())
            .andExpect(jsonPath("$.data.qrSignature").doesNotExist())
            .andExpect(jsonPath("$.data.qrIssuedAt").doesNotExist());
    }

    @Test
    @DisplayName(
        "GET /api/v1/registrations/{id}: returns 404 Not Found when registration does not exist"
    )
    void getRegistrationDetail_whenNotFound_returns404() throws Exception {
        when(
            getRegistrationDetailUseCase.getRegistrationDetail(
                studentUserId,
                registrationId
            )
        ).thenThrow(
            new NotFoundException(
                "Registration not found with ID: " + registrationId
            )
        );

        mockMvc
            .perform(
                get("/api/v1/registrations/{id}", registrationId).principal(
                    createAuth(studentUserId, "STUDENT")
                )
            )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:not-found")
            );
    }

    @Test
    @DisplayName(
        "GET /api/v1/registrations/{id}: returns 403 Forbidden when registration belongs to another student"
    )
    void getRegistrationDetail_whenNotOwner_returns403() throws Exception {
        when(
            getRegistrationDetailUseCase.getRegistrationDetail(
                studentUserId,
                registrationId
            )
        ).thenThrow(
            new ForbiddenException(
                "You do not have permission to view this registration"
            )
        );

        mockMvc
            .perform(
                get("/api/v1/registrations/{id}", registrationId).principal(
                    createAuth(studentUserId, "STUDENT")
                )
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:forbidden")
            );
    }

    @Test
    @DisplayName(
        "GET /api/v1/registrations/{id}: returns 403 Forbidden when role is not STUDENT"
    )
    void getRegistrationDetail_whenRoleNotStudent_returns403()
        throws Exception {
        mockMvc
            .perform(
                get("/api/v1/registrations/{id}", registrationId).principal(
                    createAuth(studentUserId, "ORGANIZER")
                )
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:forbidden")
            );
    }

    @Test
    @DisplayName(
        "GET /api/v1/registrations/{id}: returns 400 Bad Request when id is not a valid UUID"
    )
    void getRegistrationDetail_whenInvalidUuid_returns400() throws Exception {
        mockMvc
            .perform(
                get("/api/v1/registrations/{id}", "not-a-valid-uuid").principal(
                    createAuth(studentUserId, "STUDENT")
                )
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:bad-request")
            );
    }
}
