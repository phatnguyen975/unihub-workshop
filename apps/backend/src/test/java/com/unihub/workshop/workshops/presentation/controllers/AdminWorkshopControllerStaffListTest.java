package com.unihub.workshop.workshops.presentation.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.shared.security.JwtAuthenticatedPrincipal;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
import com.unihub.workshop.workshops.application.ports.in.ListAssignedStaffUseCase;
import com.unihub.workshop.workshops.domain.model.AssignedStaff;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminWorkshopControllerStaffListTest {

    private MockMvc mockMvc;
    private ListAssignedStaffUseCase listAssignedStaffUseCase;

    private static final UUID CALLER_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID WORKSHOP_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        listAssignedStaffUseCase = mock(ListAssignedStaffUseCase.class);

        AdminWorkshopController controller =
            AdminWorkshopControllerTestBuilder.builder()
                .withListAssignedStaffUseCase(listAssignedStaffUseCase)
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
        "GET /staff returns 200 with empty items list when workshop has no staff"
    )
    void shouldReturn200WithEmptyList() throws Exception {
        when(listAssignedStaffUseCase.execute(any())).thenReturn(
            new CursorPage<>(List.of(), null, false)
        );

        mockMvc
            .perform(
                get(
                    "/api/v1/admin/workshops/{id}/staff",
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
    @DisplayName("GET /staff returns 200 with assigned staff items and cursor")
    void shouldReturn200WithAssignedStaffItems() throws Exception {
        UUID staffId = UUID.randomUUID();
        Instant assignedAt = Instant.parse("2026-09-29T10:00:00Z");

        AssignedStaff staff = AssignedStaff.builder()
            .userId(staffId)
            .fullName("Jane Doe")
            .email("jane@example.com")
            .avatarUrl("https://example.com/avatar.png")
            .phoneNumber("+84987654321")
            .assignedAt(assignedAt)
            .build();

        when(listAssignedStaffUseCase.execute(any())).thenReturn(
            new CursorPage<>(List.of(staff), "nextCursorKey", true)
        );

        mockMvc
            .perform(
                get(
                    "/api/v1/admin/workshops/{id}/staff",
                    WORKSHOP_ID
                ).principal(organizerAuth())
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(
                jsonPath("$.data.items[0].userId").value(staffId.toString())
            )
            .andExpect(jsonPath("$.data.items[0].fullName").value("Jane Doe"))
            .andExpect(
                jsonPath("$.data.items[0].email").value("jane@example.com")
            )
            .andExpect(
                jsonPath("$.data.items[0].avatarUrl").value(
                    "https://example.com/avatar.png"
                )
            )
            .andExpect(
                jsonPath("$.data.items[0].phoneNumber").value("+84987654321")
            )
            .andExpect(
                jsonPath("$.data.items[0].assignedAt").value(
                    "2026-09-29T10:00:00Z"
                )
            )
            .andExpect(
                jsonPath("$.data.cursor.nextCursor").value("nextCursorKey")
            )
            .andExpect(jsonPath("$.data.cursor.hasMore").value(true));
    }

    @Test
    @DisplayName("GET /staff returns 404 when workshop does not exist")
    void shouldReturn404WhenNotFound() throws Exception {
        when(listAssignedStaffUseCase.execute(any())).thenThrow(
            new NotFoundException("Workshop not found: " + WORKSHOP_ID)
        );

        mockMvc
            .perform(
                get(
                    "/api/v1/admin/workshops/{id}/staff",
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
        "GET /staff returns 403 when workshop belongs to different organization"
    )
    void shouldReturn403WhenDifferentOrg() throws Exception {
        when(listAssignedStaffUseCase.execute(any())).thenThrow(
            new ForbiddenException(
                "Cannot access workshop belonging to a different organization"
            )
        );

        mockMvc
            .perform(
                get(
                    "/api/v1/admin/workshops/{id}/staff",
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
}
