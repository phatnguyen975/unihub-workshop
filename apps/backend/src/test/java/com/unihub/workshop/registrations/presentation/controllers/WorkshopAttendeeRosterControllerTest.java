package com.unihub.workshop.registrations.presentation.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unihub.workshop.registrations.application.ports.in.GetWorkshopAttendeeRosterQuery;
import com.unihub.workshop.registrations.application.ports.in.GetWorkshopAttendeeRosterUseCase;
import com.unihub.workshop.registrations.application.ports.in.GetWorkshopRegistrationSummaryUseCase;
import com.unihub.workshop.registrations.domain.model.RegistrationStatus;
import com.unihub.workshop.registrations.domain.model.WorkshopAttendeeRosterItem;
import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.shared.security.JwtAuthenticatedPrincipal;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class WorkshopAttendeeRosterControllerTest {

    private MockMvc mockMvc;
    private GetWorkshopRegistrationSummaryUseCase getSummaryUseCase;
    private GetWorkshopAttendeeRosterUseCase getRosterUseCase;

    private final UUID organizerUserId = UUID.randomUUID();
    private final UUID organizationId = UUID.randomUUID();
    private final UUID workshopId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-28T14:00:00Z");

    @BeforeEach
    void setUp() {
        getSummaryUseCase = mock(GetWorkshopRegistrationSummaryUseCase.class);
        getRosterUseCase = mock(GetWorkshopAttendeeRosterUseCase.class);

        WorkshopRegistrationMonitoringController controller =
            new WorkshopRegistrationMonitoringController(
                getSummaryUseCase,
                getRosterUseCase
            );

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(
                new GlobalExceptionHandler(),
                new GlobalResponseBodyAdvice()
            )
            .build();
    }

    private TestingAuthenticationToken createOrganizerAuth(
        UUID userId,
        UUID orgId
    ) {
        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
            userId.toString(),
            orgId.toString(),
            "jti-" + UUID.randomUUID(),
            "ORGANIZER",
            UUID.randomUUID().toString(),
            now.minusSeconds(60),
            now.plusSeconds(3600)
        );
        return new TestingAuthenticationToken(
            principal,
            null,
            "ROLE_ORGANIZER"
        );
    }

    @Test
    @DisplayName(
        "Scenario 8: Happy path returns 200 OK with roster items and cursor metadata (FR-10, 11, 13, 14)"
    )
    void getRoster_HappyPath_Returns200Ok() throws Exception {
        UUID regId = UUID.randomUUID();
        UUID studentUserId = UUID.randomUUID();

        WorkshopAttendeeRosterItem item = new WorkshopAttendeeRosterItem(
            regId,
            new WorkshopAttendeeRosterItem.StudentProfileSummary(
                studentUserId,
                "Nguyen Van A",
                "23123456",
                "23123456@student.hcmus.edu.vn"
            ),
            RegistrationStatus.CONFIRMED,
            BigDecimal.ZERO,
            "USD",
            now
        );

        CursorPage<WorkshopAttendeeRosterItem> page = new CursorPage<>(
            List.of(item),
            "next-cursor-token",
            true
        );
        when(
            getRosterUseCase.getRoster(
                any(GetWorkshopAttendeeRosterQuery.class)
            )
        ).thenReturn(page);

        mockMvc
            .perform(
                get(
                    "/api/v1/admin/workshops/{workshopId}/registrations",
                    workshopId
                ).principal(
                    createOrganizerAuth(organizerUserId, organizationId)
                )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Attendee roster retrieved"))
            .andExpect(jsonPath("$.data.items[0].id").value(regId.toString()))
            .andExpect(
                jsonPath("$.data.items[0].student.id").value(
                    studentUserId.toString()
                )
            )
            .andExpect(
                jsonPath("$.data.items[0].student.fullName").value(
                    "Nguyen Van A"
                )
            )
            .andExpect(
                jsonPath("$.data.items[0].student.studentNumber").value(
                    "23123456"
                )
            )
            .andExpect(
                jsonPath("$.data.items[0].student.email").value(
                    "23123456@student.hcmus.edu.vn"
                )
            )
            .andExpect(jsonPath("$.data.items[0].status").value("CONFIRMED"))
            .andExpect(jsonPath("$.data.items[0].price").value(0))
            .andExpect(jsonPath("$.data.items[0].currency").value("USD"))
            .andExpect(
                jsonPath("$.data.items[0].registeredAt").value(now.toString())
            )
            .andExpect(
                jsonPath("$.data.cursor.nextCursor").value("next-cursor-token")
            )
            .andExpect(jsonPath("$.data.cursor.hasMore").value(true));

        verify(getRosterUseCase).getRoster(
            new GetWorkshopAttendeeRosterQuery(
                workshopId,
                organizationId,
                null,
                null,
                null
            )
        );
    }

    @Test
    @DisplayName("Scenario 10: Supports status filter and pagination params")
    void getRoster_WithFilterAndPaginationParams_PassesToUseCase()
        throws Exception {
        CursorPage<WorkshopAttendeeRosterItem> emptyPage = new CursorPage<>(
            List.of(),
            null,
            false
        );
        when(
            getRosterUseCase.getRoster(
                any(GetWorkshopAttendeeRosterQuery.class)
            )
        ).thenReturn(emptyPage);

        mockMvc
            .perform(
                get(
                    "/api/v1/admin/workshops/{workshopId}/registrations",
                    workshopId
                )
                    .param("cursor", "test-cursor")
                    .param("size", "50")
                    .param("status", "CONFIRMED")
                    .principal(
                        createOrganizerAuth(organizerUserId, organizationId)
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items").isEmpty())
            .andExpect(jsonPath("$.data.cursor.nextCursor").doesNotExist())
            .andExpect(jsonPath("$.data.cursor.hasMore").value(false));

        verify(getRosterUseCase).getRoster(
            new GetWorkshopAttendeeRosterQuery(
                workshopId,
                organizationId,
                "CONFIRMED",
                "test-cursor",
                50
            )
        );
    }

    @Test
    @DisplayName(
        "Scenario 11: Size > 100 returns 422 Unprocessable Entity (FR-13, BR-04)"
    )
    void getRoster_SizeGreaterThan100_Returns422() throws Exception {
        when(
            getRosterUseCase.getRoster(
                any(GetWorkshopAttendeeRosterQuery.class)
            )
        ).thenThrow(
            new ValidationException("size", "size must be between 1 and 100")
        );

        mockMvc
            .perform(
                get(
                    "/api/v1/admin/workshops/{workshopId}/registrations",
                    workshopId
                )
                    .param("size", "101")
                    .principal(
                        createOrganizerAuth(organizerUserId, organizationId)
                    )
            )
            .andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName(
        "Scenario 12: Cursor filter mismatch returns 400 Bad Request (BR-05)"
    )
    void getRoster_CursorFilterMismatch_Returns400() throws Exception {
        when(
            getRosterUseCase.getRoster(
                any(GetWorkshopAttendeeRosterQuery.class)
            )
        ).thenThrow(new BadRequestException("Cursor filter mismatch"));

        mockMvc
            .perform(
                get(
                    "/api/v1/admin/workshops/{workshopId}/registrations",
                    workshopId
                )
                    .param("cursor", "mismatched-cursor")
                    .principal(
                        createOrganizerAuth(organizerUserId, organizationId)
                    )
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName(
        "Scenario 15: Invalid status enum returns 422 Unprocessable Entity (FR-12)"
    )
    void getRoster_InvalidStatus_Returns422() throws Exception {
        when(
            getRosterUseCase.getRoster(
                any(GetWorkshopAttendeeRosterQuery.class)
            )
        ).thenThrow(
            new ValidationException(
                "status",
                "Invalid registration status: INVALID_STATUS"
            )
        );

        mockMvc
            .perform(
                get(
                    "/api/v1/admin/workshops/{workshopId}/registrations",
                    workshopId
                )
                    .param("status", "INVALID_STATUS")
                    .principal(
                        createOrganizerAuth(organizerUserId, organizationId)
                    )
            )
            .andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("Scenario 4: Workshop not found returns 404 Not Found (FR-02)")
    void getRoster_WorkshopNotFound_Returns404() throws Exception {
        when(
            getRosterUseCase.getRoster(
                any(GetWorkshopAttendeeRosterQuery.class)
            )
        ).thenThrow(
            new NotFoundException("Workshop not found with ID: " + workshopId)
        );

        mockMvc
            .perform(
                get(
                    "/api/v1/admin/workshops/{workshopId}/registrations",
                    workshopId
                ).principal(
                    createOrganizerAuth(organizerUserId, organizationId)
                )
            )
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName(
        "Scenario 5: Org mismatch returns 403 Forbidden (FR-03, BR-01)"
    )
    void getRoster_OrgMismatch_Returns403() throws Exception {
        when(
            getRosterUseCase.getRoster(
                any(GetWorkshopAttendeeRosterQuery.class)
            )
        ).thenThrow(
            new ForbiddenException(
                "Forbidden: Workshop does not belong to caller's organization"
            )
        );

        mockMvc
            .perform(
                get(
                    "/api/v1/admin/workshops/{workshopId}/registrations",
                    workshopId
                ).principal(
                    createOrganizerAuth(organizerUserId, organizationId)
                )
            )
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName(
        "Scenario 6: Unauthenticated request returns 401 Unauthorized (FR-01)"
    )
    void getRoster_Unauthenticated_Returns401() throws Exception {
        mockMvc
            .perform(
                get(
                    "/api/v1/admin/workshops/{workshopId}/registrations",
                    workshopId
                )
            )
            .andExpect(status().isUnauthorized());
    }
}
