package com.unihub.workshop.attendances.presentation.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unihub.workshop.attendances.application.ports.in.GetAttendanceSummaryUseCase;
import com.unihub.workshop.attendances.domain.model.Attendance;
import com.unihub.workshop.attendances.domain.model.AttendanceSummary;
import com.unihub.workshop.attendances.presentation.mapper.AttendancePresentationMapperImpl;
import com.unihub.workshop.shared.security.JwtAuthenticatedPrincipal;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AttendanceSummaryControllerTest {

    private final GetAttendanceSummaryUseCase useCase = mock(
        GetAttendanceSummaryUseCase.class
    );
    private final UUID workshopId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(
            new AttendanceSummaryController(
                useCase,
                new AttendancePresentationMapperImpl()
            )
        )
            .setControllerAdvice(
                new GlobalExceptionHandler(),
                new GlobalResponseBodyAdvice()
            )
            .build();
    }

    private TestingAuthenticationToken auth(String role) {
        Instant now = Instant.parse("2026-11-07T00:00:00Z");
        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
            UUID.randomUUID().toString(),
            orgId.toString(),
            "jti",
            role,
            UUID.randomUUID().toString(),
            now,
            now.plusSeconds(3600)
        );
        return new TestingAuthenticationToken(principal, null, "ROLE_" + role);
    }

    @Test
    void organizerReceivesOnlyApprovedFieldsAndUtcScanTimes() throws Exception {
        Instant time = Instant.parse("2026-11-07T00:00:00Z");
        Attendance scan = Attendance.builder()
            .id(UUID.randomUUID())
            .registrationId(UUID.randomUUID())
            .checkedInAt(time)
            .createdAt(time)
            .deviceId("private")
            .build();
        when(useCase.execute(any())).thenReturn(
            AttendanceSummary.of(workshopId, 100, 80, 50, List.of(scan))
        );
        mvc.perform(
            get(
                "/api/v1/workshops/{workshopId}/attendances/summary",
                workshopId
            ).principal(auth("ORGANIZER"))
        )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.data.workshopId").value(workshopId.toString())
            )
            .andExpect(jsonPath("$.data.confirmedCount").value(80))
            .andExpect(jsonPath("$.data.checkedInCount").value(50))
            .andExpect(jsonPath("$.data.checkInPercentage").value(62.5))
            .andExpect(
                jsonPath("$.data.recentScans[0].checkedInAt").value(
                    time.toString()
                )
            )
            .andExpect(
                jsonPath("$.data.recentScans[0].createdAt").value(
                    time.toString()
                )
            )
            .andExpect(
                jsonPath("$.data.recentScans[0].id").value(scan.id().toString())
            )
            .andExpect(
                jsonPath("$.data.recentScans[0].registrationId").value(
                    scan.registrationId().toString()
                )
            )
            .andExpect(jsonPath("$.data.length()").value(6))
            .andExpect(jsonPath("$.data.recentScans[0].length()").value(4))
            .andExpect(
                jsonPath("$.data.recentScans[0].deviceId").doesNotExist()
            )
            .andExpect(jsonPath("$.data.cursor").doesNotExist());
    }

    @Test
    void studentIsForbiddenWithoutReporting() throws Exception {
        mvc.perform(
            get(
                "/api/v1/workshops/{workshopId}/attendances/summary",
                workshopId
            ).principal(auth("STUDENT"))
        ).andExpect(status().isForbidden());
        verifyNoInteractions(useCase);
    }

    @Test
    void invalidWorkshopIdentifierReturnsBadRequest() throws Exception {
        mvc.perform(
            get("/api/v1/workshops/not-a-uuid/attendances/summary").principal(
                auth("ORGANIZER")
            )
        ).andExpect(status().isBadRequest());
        verifyNoInteractions(useCase);
    }
}
