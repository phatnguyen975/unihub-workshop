package com.unihub.workshop.registrations.presentation.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unihub.workshop.registrations.application.ports.in.GetWorkshopAttendeeRosterUseCase;
import com.unihub.workshop.registrations.application.ports.in.GetWorkshopRegistrationSummaryQuery;
import com.unihub.workshop.registrations.application.ports.in.GetWorkshopRegistrationSummaryUseCase;
import com.unihub.workshop.registrations.domain.model.WorkshopRegistrationSeatCounts;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.security.JwtAuthenticatedPrincipal;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class WorkshopRegistrationSummaryControllerTest {

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
        "Scenario 1: Happy path returns 200 OK with formatted seat counts (FR-04..08)"
    )
    void getSummary_HappyPath_Returns200Ok() throws Exception {
        WorkshopRegistrationSeatCounts seatCounts =
            new WorkshopRegistrationSeatCounts(
                workshopId,
                100,
                72L,
                5L,
                23,
                new BigDecimal("72.00"),
                now
            );
        when(
            getSummaryUseCase.getSummary(
                any(GetWorkshopRegistrationSummaryQuery.class)
            )
        ).thenReturn(seatCounts);

        mockMvc
            .perform(
                get(
                    "/api/v1/admin/workshops/{workshopId}/registrations/summary",
                    workshopId
                ).principal(
                    createOrganizerAuth(organizerUserId, organizationId)
                )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(
                jsonPath("$.message").value("Registration summary retrieved")
            )
            .andExpect(
                jsonPath("$.data.workshopId").value(workshopId.toString())
            )
            .andExpect(jsonPath("$.data.capacity").value(100))
            .andExpect(jsonPath("$.data.confirmedCount").value(72))
            .andExpect(jsonPath("$.data.pendingCount").value(5))
            .andExpect(jsonPath("$.data.availableSeats").value(23))
            .andExpect(jsonPath("$.data.occupancyPercentage").value(72.00))
            .andExpect(jsonPath("$.data.lastUpdatedAt").value(now.toString()));

        verify(getSummaryUseCase).getSummary(
            new GetWorkshopRegistrationSummaryQuery(workshopId, organizationId)
        );
    }

    @Test
    @DisplayName("Scenario 4: Workshop not found returns 404 Not Found (FR-02)")
    void getSummary_WorkshopNotFound_Returns404() throws Exception {
        when(
            getSummaryUseCase.getSummary(
                any(GetWorkshopRegistrationSummaryQuery.class)
            )
        ).thenThrow(
            new NotFoundException("Workshop not found with ID: " + workshopId)
        );

        mockMvc
            .perform(
                get(
                    "/api/v1/admin/workshops/{workshopId}/registrations/summary",
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
    void getSummary_OrgMismatch_Returns403() throws Exception {
        when(
            getSummaryUseCase.getSummary(
                any(GetWorkshopRegistrationSummaryQuery.class)
            )
        ).thenThrow(
            new ForbiddenException(
                "Forbidden: Workshop does not belong to caller's organization"
            )
        );

        mockMvc
            .perform(
                get(
                    "/api/v1/admin/workshops/{workshopId}/registrations/summary",
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
    void getSummary_Unauthenticated_Returns401() throws Exception {
        mockMvc
            .perform(
                get(
                    "/api/v1/admin/workshops/{workshopId}/registrations/summary",
                    workshopId
                )
            )
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Invalid UUID format returns 400 Bad Request")
    void getSummary_InvalidUuid_Returns400() throws Exception {
        mockMvc
            .perform(
                get(
                    "/api/v1/admin/workshops/{workshopId}/registrations/summary",
                    "invalid-uuid"
                ).principal(
                    createOrganizerAuth(organizerUserId, organizationId)
                )
            )
            .andExpect(status().isBadRequest());
    }
}
