package com.unihub.workshop.registrations.presentation.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unihub.workshop.registrations.application.ports.in.GetWorkshopAttendeeRosterUseCase;
import com.unihub.workshop.registrations.application.ports.in.GetWorkshopRegistrationSummaryQuery;
import com.unihub.workshop.registrations.application.ports.in.GetWorkshopRegistrationSummaryUseCase;
import com.unihub.workshop.registrations.domain.model.WorkshopRegistrationSeatCounts;
import com.unihub.workshop.shared.security.JwtAuthenticatedPrincipal;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class WorkshopRegistrationMonitoringControllerTest {

    @Test
    void emptySummaryReturnsTheUseCaseTimestamp() throws Exception {
        UUID workshopId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        Instant updatedAt = Instant.parse("2026-10-28T13:00:00Z");
        GetWorkshopRegistrationSummaryUseCase summary = mock(
            GetWorkshopRegistrationSummaryUseCase.class
        );
        GetWorkshopAttendeeRosterUseCase roster = mock(
            GetWorkshopAttendeeRosterUseCase.class
        );
        when(
            summary.getSummary(
                new GetWorkshopRegistrationSummaryQuery(
                    workshopId,
                    organizationId
                )
            )
        ).thenReturn(
            WorkshopRegistrationSeatCounts.of(workshopId, 0, 0, 0, updatedAt)
        );
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
            new WorkshopRegistrationMonitoringController(summary, roster)
        )
            .setControllerAdvice(
                new GlobalExceptionHandler(),
                new GlobalResponseBodyAdvice()
            )
            .build();
        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
            UUID.randomUUID().toString(),
            organizationId.toString(),
            "jti",
            "ORGANIZER",
            UUID.randomUUID().toString(),
            updatedAt,
            updatedAt.plusSeconds(3600)
        );

        mvc.perform(
            get(
                "/api/v1/admin/workshops/{workshopId}/registrations/summary",
                workshopId
            ).principal(
                new TestingAuthenticationToken(
                    principal,
                    null,
                    "ROLE_ORGANIZER"
                )
            )
        )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.data.lastUpdatedAt").value(updatedAt.toString())
            )
            .andExpect(jsonPath("$.data.confirmedCount").value(0));
    }

    @Test
    void summaryAndRosterRequireOrganizerRole() throws Exception {
        PreAuthorize summary = WorkshopRegistrationMonitoringController.class
            .getMethod("getSummary", UUID.class, Authentication.class)
            .getAnnotation(PreAuthorize.class);
        PreAuthorize roster = WorkshopRegistrationMonitoringController.class
            .getMethod(
                "getRoster",
                UUID.class,
                String.class,
                Integer.class,
                String.class,
                Authentication.class
            )
            .getAnnotation(PreAuthorize.class);

        assertThat(summary.value()).isEqualTo("hasRole('ORGANIZER')");
        assertThat(roster.value()).isEqualTo("hasRole('ORGANIZER')");
    }
}
