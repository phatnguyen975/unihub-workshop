package com.unihub.workshop.registrations.presentation.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unihub.workshop.registrations.application.ports.in.GetMyRegistrationsQuery;
import com.unihub.workshop.registrations.application.ports.in.GetMyRegistrationsUseCase;
import com.unihub.workshop.registrations.domain.model.RegistrationStatus;
import com.unihub.workshop.registrations.domain.model.RegistrationSummary;
import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.shared.security.JwtAuthenticatedPrincipal;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class GetMyRegistrationsControllerTest {

    private MockMvc mockMvc;
    private GetMyRegistrationsUseCase getMyRegistrationsUseCase;

    private final UUID studentUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        getMyRegistrationsUseCase = mock(GetMyRegistrationsUseCase.class);

        RegistrationController controller =
            RegistrationControllerTestBuilder.builder()
                .withGetMyRegistrationsUseCase(getMyRegistrationsUseCase)
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
        "GET /api/v1/registrations/me: returns 200 OK with paginated list and metadata"
    )
    void getMyRegistrations_asStudent_returns200WithPaginatedEnrichedItems()
        throws Exception {
        UUID regId = UUID.randomUUID();
        UUID workshopId = UUID.randomUUID();
        Instant now = Instant.now();

        RegistrationSummary summary = new RegistrationSummary(
            regId,
            workshopId,
            "Career Sprint 101",
            now.plusSeconds(3600),
            now.plusSeconds(7200),
            RegistrationStatus.CONFIRMED,
            now
        );

        when(
            getMyRegistrationsUseCase.getMyRegistrations(
                any(GetMyRegistrationsQuery.class)
            )
        ).thenReturn(
            new CursorPage<>(List.of(summary), "encoded-cursor-xyz", true)
        );

        mockMvc
            .perform(
                get("/api/v1/registrations/me").principal(
                    createAuth(studentUserId, "STUDENT")
                )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Registrations retrieved"))
            .andExpect(jsonPath("$.data.items[0].id").value(regId.toString()))
            .andExpect(
                jsonPath("$.data.items[0].workshopId").value(
                    workshopId.toString()
                )
            )
            .andExpect(
                jsonPath("$.data.items[0].workshopTitle").value(
                    "Career Sprint 101"
                )
            )
            .andExpect(jsonPath("$.data.items[0].status").value("CONFIRMED"))
            .andExpect(
                jsonPath("$.data.cursor.nextCursor").value("encoded-cursor-xyz")
            )
            .andExpect(jsonPath("$.data.cursor.hasMore").value(true));
    }

    @Test
    @DisplayName(
        "GET /api/v1/registrations/me: passes query params correctly to use case"
    )
    void getMyRegistrations_withStatusAndSize_passesQueryParamsToUseCase()
        throws Exception {
        when(
            getMyRegistrationsUseCase.getMyRegistrations(
                any(GetMyRegistrationsQuery.class)
            )
        ).thenReturn(CursorPage.empty());

        mockMvc
            .perform(
                get("/api/v1/registrations/me")
                    .principal(createAuth(studentUserId, "STUDENT"))
                    .param("status", "CONFIRMED")
                    .param("size", "10")
                    .param("cursor", "test-cursor")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items").isEmpty())
            .andExpect(jsonPath("$.data.cursor.hasMore").value(false));

        verify(getMyRegistrationsUseCase).getMyRegistrations(
            eq(
                new GetMyRegistrationsQuery(
                    studentUserId,
                    "CONFIRMED",
                    "test-cursor",
                    10
                )
            )
        );
    }

    @Test
    @DisplayName(
        "GET /api/v1/registrations/me: returns 200 OK with empty items and null nextCursor when zero registrations"
    )
    void getMyRegistrations_withEmptyResults_returns200WithEmptyItemsAndNullCursor()
        throws Exception {
        when(
            getMyRegistrationsUseCase.getMyRegistrations(
                any(GetMyRegistrationsQuery.class)
            )
        ).thenReturn(CursorPage.empty());

        mockMvc
            .perform(
                get("/api/v1/registrations/me").principal(
                    createAuth(studentUserId, "STUDENT")
                )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items").isEmpty())
            .andExpect(jsonPath("$.data.cursor.hasMore").value(false))
            .andExpect(jsonPath("$.data.cursor.nextCursor").doesNotExist());
    }

    @Test
    @DisplayName(
        "GET /api/v1/registrations/me: returns 422 Unprocessable Entity when status is invalid enum"
    )
    void getMyRegistrations_withInvalidStatus_returns422UnprocessableEntity()
        throws Exception {
        when(
            getMyRegistrationsUseCase.getMyRegistrations(
                any(GetMyRegistrationsQuery.class)
            )
        ).thenThrow(
            new ValidationException(
                "status",
                "Invalid registration status: INVALID"
            )
        );

        mockMvc
            .perform(
                get("/api/v1/registrations/me")
                    .principal(createAuth(studentUserId, "STUDENT"))
                    .param("status", "INVALID")
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            );
    }

    @Test
    @DisplayName(
        "GET /api/v1/registrations/me: returns 400 Bad Request when cursor is malformed"
    )
    void getMyRegistrations_withInvalidCursor_returns400BadRequest()
        throws Exception {
        when(
            getMyRegistrationsUseCase.getMyRegistrations(
                any(GetMyRegistrationsQuery.class)
            )
        ).thenThrow(new BadRequestException("Invalid cursor format"));

        mockMvc
            .perform(
                get("/api/v1/registrations/me")
                    .principal(createAuth(studentUserId, "STUDENT"))
                    .param("cursor", "malformed-base64")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:bad-request")
            );
    }

    @Test
    @DisplayName(
        "GET /api/v1/registrations/me: returns 403 Forbidden when user is not STUDENT"
    )
    void getMyRegistrations_withoutStudentRole_returns403Forbidden()
        throws Exception {
        mockMvc
            .perform(
                get("/api/v1/registrations/me").principal(
                    createAuth(studentUserId, "ORGANIZER")
                )
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false));
    }
}
