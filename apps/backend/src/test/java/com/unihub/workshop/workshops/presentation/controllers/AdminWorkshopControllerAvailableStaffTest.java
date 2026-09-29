package com.unihub.workshop.workshops.presentation.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unihub.workshop.shared.exception.ConflictException;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.shared.security.JwtAuthenticatedPrincipal;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
import com.unihub.workshop.workshops.application.ports.in.ListAvailableStaffQuery;
import com.unihub.workshop.workshops.application.ports.in.ListAvailableStaffUseCase;
import com.unihub.workshop.workshops.domain.model.AvailableStaff;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminWorkshopControllerAvailableStaffTest {

    private MockMvc mockMvc;
    private ListAvailableStaffUseCase listAvailableStaffUseCase;

    private static final UUID CALLER_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID WORKSHOP_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        listAvailableStaffUseCase = mock(ListAvailableStaffUseCase.class);

        AdminWorkshopController controller =
            AdminWorkshopControllerTestBuilder.builder()
                .withListAvailableStaffUseCase(listAvailableStaffUseCase)
                .build();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(
                new GlobalExceptionHandler(),
                new GlobalResponseBodyAdvice()
            )
            .build();
    }

    private TestingAuthenticationToken organizerAuth() {
        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
            CALLER_ID.toString(),
            ORG_ID.toString(),
            "jti-123",
            "ORGANIZER",
            UUID.randomUUID().toString(),
            Instant.now(),
            Instant.now().plusSeconds(3600)
        );
        return new TestingAuthenticationToken(
            principal,
            null,
            "ROLE_ORGANIZER"
        );
    }

    @Test
    @DisplayName(
        "GET /staff/available returns 200 with empty list when no staff are available"
    )
    void shouldReturn200WithEmptyList() throws Exception {
        when(listAvailableStaffUseCase.execute(any())).thenReturn(
            new CursorPage<>(List.of(), null, false)
        );

        mockMvc
            .perform(
                get(
                    "/api/v1/admin/workshops/{id}/staff/available",
                    WORKSHOP_ID
                ).principal(organizerAuth())
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items").isArray())
            .andExpect(jsonPath("$.data.items").isEmpty())
            .andExpect(jsonPath("$.data.cursor.hasMore").value(false));
    }

    @Test
    @DisplayName(
        "GET /staff/available returns 200 with available staff items and cursor"
    )
    void shouldReturn200WithAvailableStaffItems() throws Exception {
        UUID staffId = UUID.randomUUID();

        AvailableStaff staff = AvailableStaff.builder()
            .userId(staffId)
            .fullName("Bob Tran")
            .email("bob@example.com")
            .avatarUrl("https://example.com/bob.png")
            .phoneNumber("+84909876543")
            .activeAssignmentsCount(2)
            .build();

        when(listAvailableStaffUseCase.execute(any())).thenReturn(
            new CursorPage<>(List.of(staff), "nextCursor123", true)
        );

        mockMvc
            .perform(
                get(
                    "/api/v1/admin/workshops/{id}/staff/available",
                    WORKSHOP_ID
                ).principal(organizerAuth())
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(
                jsonPath("$.data.items[0].userId").value(staffId.toString())
            )
            .andExpect(jsonPath("$.data.items[0].fullName").value("Bob Tran"))
            .andExpect(
                jsonPath("$.data.items[0].email").value("bob@example.com")
            )
            .andExpect(
                jsonPath("$.data.items[0].avatarUrl").value(
                    "https://example.com/bob.png"
                )
            )
            .andExpect(
                jsonPath("$.data.items[0].phoneNumber").value("+84909876543")
            )
            .andExpect(
                jsonPath("$.data.items[0].activeAssignmentsCount").value(2)
            )
            .andExpect(
                jsonPath("$.data.cursor.nextCursor").value("nextCursor123")
            )
            .andExpect(jsonPath("$.data.cursor.hasMore").value(true));
    }

    @Test
    @DisplayName("GET /staff/available passes search query 'q' to use case")
    void shouldPassSearchQueryToUseCase() throws Exception {
        when(listAvailableStaffUseCase.execute(any())).thenReturn(
            new CursorPage<>(List.of(), null, false)
        );

        mockMvc
            .perform(
                get("/api/v1/admin/workshops/{id}/staff/available", WORKSHOP_ID)
                    .param("q", "bob")
                    .principal(organizerAuth())
            )
            .andExpect(status().isOk());

        ArgumentCaptor<ListAvailableStaffQuery> captor =
            ArgumentCaptor.forClass(ListAvailableStaffQuery.class);
        verify(listAvailableStaffUseCase).execute(captor.capture());
        assertThat(captor.getValue().q()).isEqualTo("bob");
    }

    @Test
    @DisplayName(
        "GET /staff/available returns 404 when workshop does not exist"
    )
    void shouldReturn404WhenNotFound() throws Exception {
        when(listAvailableStaffUseCase.execute(any())).thenThrow(
            new NotFoundException("Workshop not found: " + WORKSHOP_ID)
        );

        mockMvc
            .perform(
                get(
                    "/api/v1/admin/workshops/{id}/staff/available",
                    WORKSHOP_ID
                ).principal(organizerAuth())
            )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:not-found")
            )
            .andExpect(jsonPath("$.error.title").value("Not Found"));
    }

    @Test
    @DisplayName(
        "GET /staff/available returns 403 when workshop belongs to different organization"
    )
    void shouldReturn403WhenDifferentOrg() throws Exception {
        when(listAvailableStaffUseCase.execute(any())).thenThrow(
            new ForbiddenException(
                "Cannot access workshop belonging to a different organization"
            )
        );

        mockMvc
            .perform(
                get(
                    "/api/v1/admin/workshops/{id}/staff/available",
                    WORKSHOP_ID
                ).principal(organizerAuth())
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:forbidden")
            )
            .andExpect(jsonPath("$.error.title").value("Forbidden"));
    }

    @Test
    @DisplayName(
        "GET /staff/available returns 409 Conflict when workshop is not PUBLISHED"
    )
    void shouldReturn409WhenNotPublished() throws Exception {
        when(listAvailableStaffUseCase.execute(any())).thenThrow(
            new ConflictException(
                "Workshop must be in PUBLISHED status before querying available staff."
            )
        );

        mockMvc
            .perform(
                get(
                    "/api/v1/admin/workshops/{id}/staff/available",
                    WORKSHOP_ID
                ).principal(organizerAuth())
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:conflict")
            )
            .andExpect(jsonPath("$.error.title").value("Conflict"));
    }

    @Test
    @DisplayName(
        "GET /staff/available returns 422 Unprocessable Content when workshop already started"
    )
    void shouldReturn422WhenAlreadyStarted() throws Exception {
        when(listAvailableStaffUseCase.execute(any())).thenThrow(
            new ValidationException(
                "Cannot query available staff after workshop has already started.",
                List.of("startsAt: Workshop has already started")
            )
        );

        mockMvc
            .perform(
                get(
                    "/api/v1/admin/workshops/{id}/staff/available",
                    WORKSHOP_ID
                ).principal(organizerAuth())
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            )
            .andExpect(jsonPath("$.error.title").value("Unprocessable Content"))
            .andExpect(
                jsonPath("$.error.validationErrors[0]").value(
                    "startsAt: Workshop has already started"
                )
            );
    }
}
