package com.unihub.workshop.workshops.presentation.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unihub.workshop.shared.exception.ConflictException;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.shared.security.JwtAuthenticatedPrincipal;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
import com.unihub.workshop.workshops.application.ports.in.RevokeStaffCommand;
import com.unihub.workshop.workshops.application.ports.in.RevokeStaffUseCase;
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

class AdminWorkshopControllerRevokeStaffTest {

    private MockMvc mockMvc;
    private RevokeStaffUseCase revokeStaffUseCase;

    private static final UUID CALLER_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID WORKSHOP_ID = UUID.randomUUID();
    private static final UUID STAFF_USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        revokeStaffUseCase = mock(RevokeStaffUseCase.class);

        AdminWorkshopController controller =
            AdminWorkshopControllerTestBuilder.builder()
                .withRevokeStaffUseCase(revokeStaffUseCase)
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
        "DELETE /staff/{userId} returns 204 No Content with empty body on success"
    )
    void shouldReturn204OnSuccess() throws Exception {
        doNothing().when(revokeStaffUseCase).execute(any());

        mockMvc
            .perform(
                delete(
                    "/api/v1/admin/workshops/{id}/staff/{userId}",
                    WORKSHOP_ID,
                    STAFF_USER_ID
                ).principal(organizerAuth())
            )
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));

        ArgumentCaptor<RevokeStaffCommand> captor = ArgumentCaptor.forClass(
            RevokeStaffCommand.class
        );
        verify(revokeStaffUseCase).execute(captor.capture());
        assertThat(captor.getValue().workshopId()).isEqualTo(WORKSHOP_ID);
        assertThat(captor.getValue().staffUserId()).isEqualTo(STAFF_USER_ID);
        assertThat(captor.getValue().organizationId()).isEqualTo(ORG_ID);
        assertThat(captor.getValue().callerId()).isEqualTo(CALLER_ID);
    }

    @Test
    @DisplayName(
        "DELETE /staff/{userId} returns 404 when workshop does not exist or staff is not assigned"
    )
    void shouldReturn404WhenNotFound() throws Exception {
        doThrow(new NotFoundException("Staff assignment not found"))
            .when(revokeStaffUseCase)
            .execute(any());

        mockMvc
            .perform(
                delete(
                    "/api/v1/admin/workshops/{id}/staff/{userId}",
                    WORKSHOP_ID,
                    STAFF_USER_ID
                ).principal(organizerAuth())
            )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:not-found")
            );
    }

    @Test
    @DisplayName(
        "DELETE /staff/{userId} returns 403 when workshop belongs to different organization"
    )
    void shouldReturn403WhenDifferentOrg() throws Exception {
        doThrow(
            new ForbiddenException(
                "Cannot access workshop belonging to a different organization"
            )
        )
            .when(revokeStaffUseCase)
            .execute(any());

        mockMvc
            .perform(
                delete(
                    "/api/v1/admin/workshops/{id}/staff/{userId}",
                    WORKSHOP_ID,
                    STAFF_USER_ID
                ).principal(organizerAuth())
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:forbidden")
            );
    }

    @Test
    @DisplayName(
        "DELETE /staff/{userId} returns 409 Conflict when workshop is not PUBLISHED"
    )
    void shouldReturn409WhenNotPublished() throws Exception {
        doThrow(
            new ConflictException(
                "Workshop must be in PUBLISHED status before revoking staff."
            )
        )
            .when(revokeStaffUseCase)
            .execute(any());

        mockMvc
            .perform(
                delete(
                    "/api/v1/admin/workshops/{id}/staff/{userId}",
                    WORKSHOP_ID,
                    STAFF_USER_ID
                ).principal(organizerAuth())
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:conflict")
            );
    }

    @Test
    @DisplayName(
        "DELETE /staff/{userId} returns 422 Unprocessable Content when workshop already started"
    )
    void shouldReturn422WhenAlreadyStarted() throws Exception {
        doThrow(
            new ValidationException(
                "Cannot revoke staff after the workshop has started.",
                List.of("startsAt: Workshop has already started")
            )
        )
            .when(revokeStaffUseCase)
            .execute(any());

        mockMvc
            .perform(
                delete(
                    "/api/v1/admin/workshops/{id}/staff/{userId}",
                    WORKSHOP_ID,
                    STAFF_USER_ID
                ).principal(organizerAuth())
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
}
