package com.unihub.workshop.workshops.presentation.controllers;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unihub.workshop.shared.exception.ConflictException;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.security.JwtAuthenticatedPrincipal;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
import com.unihub.workshop.workshops.application.ports.in.DeleteDraftUseCase;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminWorkshopControllerDeleteTest {

    private MockMvc mockMvc;
    private DeleteDraftUseCase deleteDraftUseCase;

    private static final UUID CALLER_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID WORKSHOP_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        deleteDraftUseCase = mock(DeleteDraftUseCase.class);

        AdminWorkshopController controller =
            AdminWorkshopControllerTestBuilder.builder()
                .withDeleteDraftUseCase(deleteDraftUseCase)
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

    private TestingAuthenticationToken studentAuth() {
        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
            CALLER_ID.toString(),
            null,
            "jti-123",
            "STUDENT",
            UUID.randomUUID().toString(),
            Instant.now(),
            Instant.now().plusSeconds(3600)
        );
        return new TestingAuthenticationToken(principal, null, "ROLE_STUDENT");
    }

    @Test
    @DisplayName(
        "DELETE /api/v1/admin/workshops/{id} returns 204 No Content on successful deletion (FR-43)"
    )
    void deleteWorkshop_whenValid_returns204NoContent() throws Exception {
        doNothing()
            .when(deleteDraftUseCase)
            .execute(eq(WORKSHOP_ID), eq(ORG_ID));

        mockMvc
            .perform(
                delete("/api/v1/admin/workshops/{id}", WORKSHOP_ID).principal(
                    organizerAuth()
                )
            )
            .andExpect(status().isNoContent());

        verify(deleteDraftUseCase).execute(eq(WORKSHOP_ID), eq(ORG_ID));
    }

    @Test
    @DisplayName(
        "DELETE /api/v1/admin/workshops/{id} returns 404 Not Found when workshop does not exist (FR-45)"
    )
    void deleteWorkshop_whenNotFound_returns404NotFound() throws Exception {
        doThrow(
            new NotFoundException("Workshop not found with id: " + WORKSHOP_ID)
        )
            .when(deleteDraftUseCase)
            .execute(eq(WORKSHOP_ID), eq(ORG_ID));

        mockMvc
            .perform(
                delete("/api/v1/admin/workshops/{id}", WORKSHOP_ID).principal(
                    organizerAuth()
                )
            )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:not-found")
            )
            .andExpect(jsonPath("$.error.status").value(404));
    }

    @Test
    @DisplayName(
        "DELETE /api/v1/admin/workshops/{id} returns 403 Forbidden when workshop belongs to another organization (FR-44)"
    )
    void deleteWorkshop_whenDifferentOrg_returns403Forbidden()
        throws Exception {
        doThrow(
            new ForbiddenException(
                "You do not have permission to delete this workshop."
            )
        )
            .when(deleteDraftUseCase)
            .execute(eq(WORKSHOP_ID), eq(ORG_ID));

        mockMvc
            .perform(
                delete("/api/v1/admin/workshops/{id}", WORKSHOP_ID).principal(
                    organizerAuth()
                )
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:forbidden")
            )
            .andExpect(jsonPath("$.error.status").value(403));
    }

    @Test
    @DisplayName(
        "DELETE /api/v1/admin/workshops/{id} returns 409 Conflict when workshop is not in DRAFT status (FR-46)"
    )
    void deleteWorkshop_whenNotDraft_returns409Conflict() throws Exception {
        doThrow(
            new ConflictException(
                "Workshop cannot be deleted because it is not in DRAFT status."
            )
        )
            .when(deleteDraftUseCase)
            .execute(eq(WORKSHOP_ID), eq(ORG_ID));

        mockMvc
            .perform(
                delete("/api/v1/admin/workshops/{id}", WORKSHOP_ID).principal(
                    organizerAuth()
                )
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:conflict")
            )
            .andExpect(jsonPath("$.error.status").value(409));
    }

    @Test
    @DisplayName(
        "DELETE /api/v1/admin/workshops/{id} returns 403 Forbidden when caller is not an ORGANIZER"
    )
    void deleteWorkshop_whenCallerNotOrganizer_returns403Forbidden()
        throws Exception {
        mockMvc
            .perform(
                delete("/api/v1/admin/workshops/{id}", WORKSHOP_ID).principal(
                    studentAuth()
                )
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:forbidden")
            );
    }
}
