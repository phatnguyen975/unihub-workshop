package com.unihub.workshop.workshops.presentation.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.unihub.workshop.shared.exception.ConflictException;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.shared.security.JwtAuthenticatedPrincipal;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
import com.unihub.workshop.workshops.application.ports.in.AssignStaffCommand;
import com.unihub.workshop.workshops.application.ports.in.AssignStaffUseCase;
import com.unihub.workshop.workshops.domain.model.AssignedStaff;
import com.unihub.workshop.workshops.presentation.dto.AssignStaffRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminWorkshopControllerAssignStaffTest {

    private MockMvc mockMvc;
    private AssignStaffUseCase assignStaffUseCase;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(
        new JavaTimeModule()
    );

    private static final UUID CALLER_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID WORKSHOP_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        assignStaffUseCase = mock(AssignStaffUseCase.class);

        AdminWorkshopController controller =
            AdminWorkshopControllerTestBuilder.builder()
                .withAssignStaffUseCase(assignStaffUseCase)
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
    @DisplayName("POST /staff returns 201 with BatchAssignResponse on success")
    void shouldReturn201WithBatchAssignResponse() throws Exception {
        UUID staffId1 = UUID.randomUUID();
        UUID staffId2 = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-30T10:00:00Z");

        List<AssignedStaff> assigned = List.of(
            AssignedStaff.builder()
                .userId(staffId1)
                .fullName("Alice Nguyen")
                .email("alice@example.com")
                .avatarUrl("https://example.com/alice.png")
                .phoneNumber("+84901234567")
                .assignedAt(now)
                .build(),
            AssignedStaff.builder()
                .userId(staffId2)
                .fullName("Bob Tran")
                .email("bob@example.com")
                .avatarUrl(null)
                .phoneNumber(null)
                .assignedAt(now)
                .build()
        );

        when(assignStaffUseCase.execute(any())).thenReturn(assigned);

        AssignStaffRequest request = new AssignStaffRequest(
            List.of(staffId1, staffId2)
        );

        mockMvc
            .perform(
                post("/api/v1/admin/workshops/{id}/staff", WORKSHOP_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .principal(organizerAuth())
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(
                jsonPath("$.message").value(
                    "Staff members assigned successfully."
                )
            )
            .andExpect(
                jsonPath("$.data.workshopId").value(WORKSHOP_ID.toString())
            )
            .andExpect(jsonPath("$.data.assignedStaffs").isArray())
            .andExpect(
                jsonPath("$.data.assignedStaffs[0].userId").value(
                    staffId1.toString()
                )
            )
            .andExpect(
                jsonPath("$.data.assignedStaffs[0].fullName").value(
                    "Alice Nguyen"
                )
            )
            .andExpect(
                jsonPath("$.data.assignedStaffs[0].email").value(
                    "alice@example.com"
                )
            )
            .andExpect(
                jsonPath("$.data.assignedStaffs[0].avatarUrl").value(
                    "https://example.com/alice.png"
                )
            )
            .andExpect(
                jsonPath("$.data.assignedStaffs[0].phoneNumber").value(
                    "+84901234567"
                )
            )
            .andExpect(
                jsonPath("$.data.assignedStaffs[0].assignedAt").value(
                    "2026-09-30T10:00:00Z"
                )
            )
            .andExpect(
                jsonPath("$.data.assignedStaffs[1].userId").value(
                    staffId2.toString()
                )
            )
            .andExpect(jsonPath("$.data.totalAssigned").doesNotExist());

        ArgumentCaptor<AssignStaffCommand> captor = ArgumentCaptor.forClass(
            AssignStaffCommand.class
        );
        verify(assignStaffUseCase).execute(captor.capture());
        assertThat(captor.getValue().workshopId()).isEqualTo(WORKSHOP_ID);
        assertThat(captor.getValue().organizationId()).isEqualTo(ORG_ID);
        assertThat(captor.getValue().callerId()).isEqualTo(CALLER_ID);
        assertThat(captor.getValue().staffUserIds()).containsExactly(
            staffId1,
            staffId2
        );
    }

    @Test
    @DisplayName("POST /staff returns 400 when staffUserIds is empty")
    void shouldReturn400WhenStaffUserIdsEmpty() throws Exception {
        String invalidJson = "{\"staffUserIds\": []}";

        mockMvc
            .perform(
                post("/api/v1/admin/workshops/{id}/staff", WORKSHOP_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(invalidJson)
                    .principal(organizerAuth())
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:bad-request")
            );
    }

    @Test
    @DisplayName("POST /staff returns 404 when workshop does not exist")
    void shouldReturn404WhenNotFound() throws Exception {
        when(assignStaffUseCase.execute(any())).thenThrow(
            new NotFoundException("Workshop not found: " + WORKSHOP_ID)
        );

        AssignStaffRequest request = new AssignStaffRequest(
            List.of(UUID.randomUUID())
        );

        mockMvc
            .perform(
                post("/api/v1/admin/workshops/{id}/staff", WORKSHOP_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .principal(organizerAuth())
            )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:not-found")
            );
    }

    @Test
    @DisplayName(
        "POST /staff returns 403 when workshop belongs to different organization"
    )
    void shouldReturn403WhenDifferentOrg() throws Exception {
        when(assignStaffUseCase.execute(any())).thenThrow(
            new ForbiddenException(
                "Cannot access workshop belonging to a different organization"
            )
        );

        AssignStaffRequest request = new AssignStaffRequest(
            List.of(UUID.randomUUID())
        );

        mockMvc
            .perform(
                post("/api/v1/admin/workshops/{id}/staff", WORKSHOP_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .principal(organizerAuth())
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:forbidden")
            );
    }

    @Test
    @DisplayName(
        "POST /staff returns 409 Conflict when workshop is not PUBLISHED"
    )
    void shouldReturn409WhenNotPublished() throws Exception {
        when(assignStaffUseCase.execute(any())).thenThrow(
            new ConflictException(
                "Workshop must be in PUBLISHED status before assigning staff."
            )
        );

        AssignStaffRequest request = new AssignStaffRequest(
            List.of(UUID.randomUUID())
        );

        mockMvc
            .perform(
                post("/api/v1/admin/workshops/{id}/staff", WORKSHOP_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .principal(organizerAuth())
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:conflict")
            );
    }

    @Test
    @DisplayName(
        "POST /staff returns 409 Conflict when staff already assigned or schedule conflict"
    )
    void shouldReturn409WhenAlreadyAssignedOrScheduleConflict()
        throws Exception {
        when(assignStaffUseCase.execute(any())).thenThrow(
            new ConflictException(
                "One or more staff members are already assigned to this workshop."
            )
        );

        AssignStaffRequest request = new AssignStaffRequest(
            List.of(UUID.randomUUID())
        );

        mockMvc
            .perform(
                post("/api/v1/admin/workshops/{id}/staff", WORKSHOP_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .principal(organizerAuth())
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:conflict")
            );
    }

    @Test
    @DisplayName(
        "POST /staff returns 422 Unprocessable Content when workshop already started"
    )
    void shouldReturn422WhenAlreadyStarted() throws Exception {
        when(assignStaffUseCase.execute(any())).thenThrow(
            new ValidationException(
                "Cannot assign staff after the workshop has started.",
                List.of("startsAt: Workshop has already started")
            )
        );

        AssignStaffRequest request = new AssignStaffRequest(
            List.of(UUID.randomUUID())
        );

        mockMvc
            .perform(
                post("/api/v1/admin/workshops/{id}/staff", WORKSHOP_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .principal(organizerAuth())
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            )
            .andExpect(
                jsonPath("$.error.validationErrors[0]").value(
                    "startsAt: Workshop has already started"
                )
            );
    }

    @Test
    @DisplayName(
        "POST /staff returns 422 Unprocessable Content when staff user is invalid"
    )
    void shouldReturn422WhenStaffUserInvalid() throws Exception {
        UUID invalidStaffId = UUID.randomUUID();
        when(assignStaffUseCase.execute(any())).thenThrow(
            new ValidationException(
                "Validation failed for one or more staff members in the batch.",
                List.of(
                    "staffUserIds: Staff user " +
                        invalidStaffId +
                        " does not exist, is inactive, does not hold the STAFF role, or belongs to another organization"
                )
            )
        );

        AssignStaffRequest request = new AssignStaffRequest(
            List.of(invalidStaffId)
        );

        mockMvc
            .perform(
                post("/api/v1/admin/workshops/{id}/staff", WORKSHOP_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .principal(organizerAuth())
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            )
            .andExpect(
                jsonPath("$.error.validationErrors[0]").value(
                    "staffUserIds: Staff user " +
                        invalidStaffId +
                        " does not exist, is inactive, does not hold the STAFF role, or belongs to another organization"
                )
            );
    }
}
