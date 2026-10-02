package com.unihub.workshop.workshops.presentation.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unihub.workshop.shared.exception.ConflictException;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.shared.security.JwtAuthenticatedPrincipal;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
import com.unihub.workshop.workshops.application.ports.in.CancelWorkshopCommand;
import com.unihub.workshop.workshops.application.ports.in.CancelWorkshopUseCase;
import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.domain.model.WorkshopStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminWorkshopControllerCancelTest {

    private MockMvc mockMvc;
    private CancelWorkshopUseCase cancelWorkshopUseCase;

    private static final UUID CALLER_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        cancelWorkshopUseCase = mock(CancelWorkshopUseCase.class);

        AdminWorkshopController controller =
            AdminWorkshopControllerTestBuilder.builder()
                .withCancelWorkshopUseCase(cancelWorkshopUseCase)
                .build();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(
                new GlobalExceptionHandler(),
                new GlobalResponseBodyAdvice()
            )
            .build();
    }

    private TestingAuthenticationToken createAuth(
        UUID userId,
        UUID orgId,
        String role
    ) {
        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
            userId != null ? userId.toString() : null,
            orgId != null ? orgId.toString() : null,
            "jti-123",
            role,
            UUID.randomUUID().toString(),
            Instant.now(),
            Instant.now().plusSeconds(3600)
        );
        return new TestingAuthenticationToken(principal, null, "ROLE_" + role);
    }

    @Test
    @DisplayName(
        "Returns 200 OK when workshop is canceled successfully with reason"
    )
    void shouldReturn200WhenWorkshopCanceledSuccessfullyWithReason()
        throws Exception {
        UUID workshopId = UUID.randomUUID();
        Instant now = Instant.now();

        Workshop canceled = Workshop.builder()
            .id(workshopId)
            .organizationId(ORG_ID)
            .title("Advanced Microservices")
            .slug("advanced-microservices-a1b2")
            .description("Deep dive into Spring Modulith")
            .coverImageUrl("https://example.com/cover.jpg")
            .location("Auditorium A, Building C")
            .timezone("Asia/Ho_Chi_Minh")
            .startsAt(now.plusSeconds(86400 * 5))
            .endsAt(now.plusSeconds(86400 * 5 + 7200))
            .capacity(100)
            .price(BigDecimal.ZERO)
            .currency("USD")
            .status(WorkshopStatus.CANCELED)
            .version(3)
            .publishedAt(now.minusSeconds(86400))
            .canceledAt(now)
            .createdAt(now.minusSeconds(86400 * 2))
            .updatedAt(now)
            .createdBy(CALLER_ID)
            .updatedBy(CALLER_ID)
            .build();

        when(
            cancelWorkshopUseCase.execute(any(CancelWorkshopCommand.class))
        ).thenReturn(canceled);

        String requestBody = """
        {
          "version": 2,
          "reason": "Venue is unavailable due to unforeseen circumstances."
        }
        """;

        mockMvc
            .perform(
                patch("/api/v1/admin/workshops/{id}/cancel", workshopId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER"))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(
                jsonPath("$.message").value("Workshop canceled successfully.")
            )
            .andExpect(jsonPath("$.data.id").value(workshopId.toString()))
            .andExpect(jsonPath("$.data.status").value("CANCELED"))
            .andExpect(jsonPath("$.data.canceledAt").isNotEmpty())
            .andExpect(jsonPath("$.data.version").value(3));
    }

    @Test
    @DisplayName(
        "Returns 200 OK when workshop is canceled successfully without reason"
    )
    void shouldReturn200WhenWorkshopCanceledSuccessfullyWithoutReason()
        throws Exception {
        UUID workshopId = UUID.randomUUID();
        Instant now = Instant.now();

        Workshop canceled = Workshop.builder()
            .id(workshopId)
            .organizationId(ORG_ID)
            .title("Advanced Microservices")
            .slug("advanced-microservices-a1b2")
            .startsAt(now.plusSeconds(86400 * 5))
            .endsAt(now.plusSeconds(86400 * 5 + 7200))
            .status(WorkshopStatus.CANCELED)
            .version(2)
            .canceledAt(now)
            .build();

        when(
            cancelWorkshopUseCase.execute(any(CancelWorkshopCommand.class))
        ).thenReturn(canceled);

        String requestBody = """
        {
          "version": 1
        }
        """;

        mockMvc
            .perform(
                patch("/api/v1/admin/workshops/{id}/cancel", workshopId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER"))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("CANCELED"))
            .andExpect(jsonPath("$.data.canceledAt").isNotEmpty());
    }

    @Test
    @DisplayName("Returns 422 Unprocessable Content when version is null")
    void shouldReturn422WhenVersionIsNull() throws Exception {
        UUID workshopId = UUID.randomUUID();

        String requestBody = """
        {
          "reason": "Testing cancellation"
        }
        """;

        mockMvc
            .perform(
                patch("/api/v1/admin/workshops/{id}/cancel", workshopId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER"))
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            );
    }

    @Test
    @DisplayName(
        "Returns 422 Unprocessable Content when prohibited field is provided"
    )
    void shouldReturn422WhenProhibitedFieldProvided() throws Exception {
        UUID workshopId = UUID.randomUUID();

        when(
            cancelWorkshopUseCase.execute(any(CancelWorkshopCommand.class))
        ).thenThrow(
            new ValidationException(
                "Field 'title' cannot be modified via the cancel endpoint.",
                List.of(
                    "title: Field is prohibited on cancel. Only version and reason are accepted."
                )
            )
        );

        String requestBody = """
        {
          "version": 2,
          "title": "Prohibited Title"
        }
        """;

        mockMvc
            .perform(
                patch("/api/v1/admin/workshops/{id}/cancel", workshopId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER"))
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            );
    }

    @Test
    @DisplayName("Returns 404 Not Found when workshop does not exist")
    void shouldReturn404WhenWorkshopNotFound() throws Exception {
        UUID workshopId = UUID.randomUUID();

        when(
            cancelWorkshopUseCase.execute(any(CancelWorkshopCommand.class))
        ).thenThrow(
            new NotFoundException("Workshop not found with id: " + workshopId)
        );

        String requestBody = """
        {
          "version": 2
        }
        """;

        mockMvc
            .perform(
                patch("/api/v1/admin/workshops/{id}/cancel", workshopId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER"))
            )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:not-found")
            );
    }

    @Test
    @DisplayName(
        "Returns 403 Forbidden when caller belongs to different organization"
    )
    void shouldReturn403WhenCallerBelongsToDifferentOrganization()
        throws Exception {
        UUID workshopId = UUID.randomUUID();

        when(
            cancelWorkshopUseCase.execute(any(CancelWorkshopCommand.class))
        ).thenThrow(
            new ForbiddenException(
                "You do not have permission to cancel workshops for another organization."
            )
        );

        String requestBody = """
        {
          "version": 2
        }
        """;

        mockMvc
            .perform(
                patch("/api/v1/admin/workshops/{id}/cancel", workshopId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER"))
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:forbidden")
            );
    }

    @Test
    @DisplayName(
        "Returns 409 Conflict when version does not match stored version"
    )
    void shouldReturn409WhenVersionMismatch() throws Exception {
        UUID workshopId = UUID.randomUUID();

        when(
            cancelWorkshopUseCase.execute(any(CancelWorkshopCommand.class))
        ).thenThrow(
            new ConflictException(
                "Workshop version conflict: expected version 2 but found version 3."
            )
        );

        String requestBody = """
        {
          "version": 2
        }
        """;

        mockMvc
            .perform(
                patch("/api/v1/admin/workshops/{id}/cancel", workshopId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER"))
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:conflict")
            );
    }

    @Test
    @DisplayName(
        "Returns 422 Unprocessable Content when reason exceeds 1000 characters"
    )
    void shouldReturn422WhenReasonExceeds1000Chars() throws Exception {
        UUID workshopId = UUID.randomUUID();

        when(
            cancelWorkshopUseCase.execute(any(CancelWorkshopCommand.class))
        ).thenThrow(
            new ValidationException(
                "The cancellation reason exceeds the maximum allowed length of 1000 characters.",
                List.of(
                    "reason: Must not exceed 1000 characters after trimming"
                )
            )
        );

        String requestBody = """
        {
          "version": 2,
          "reason": "%s"
        }
        """.formatted("a".repeat(1001));

        mockMvc
            .perform(
                patch("/api/v1/admin/workshops/{id}/cancel", workshopId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER"))
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            );
    }

    @Test
    @DisplayName(
        "Returns 422 Unprocessable Content when workshop is in DRAFT status"
    )
    void shouldReturn422WhenWorkshopInDraft() throws Exception {
        UUID workshopId = UUID.randomUUID();

        when(
            cancelWorkshopUseCase.execute(any(CancelWorkshopCommand.class))
        ).thenThrow(
            new ValidationException(
                "status",
                "Workshop is in DRAFT status. Please use DELETE /api/v1/admin/workshops/{id} to delete drafts."
            )
        );

        String requestBody = """
        {
          "version": 0
        }
        """;

        mockMvc
            .perform(
                patch("/api/v1/admin/workshops/{id}/cancel", workshopId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER"))
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            );
    }

    @Test
    @DisplayName(
        "Returns 422 Unprocessable Content when workshop has already concluded"
    )
    void shouldReturn422WhenWorkshopAlreadyConcluded() throws Exception {
        UUID workshopId = UUID.randomUUID();

        when(
            cancelWorkshopUseCase.execute(any(CancelWorkshopCommand.class))
        ).thenThrow(
            new ValidationException(
                "ends_at",
                "Workshop has already concluded and cannot be canceled."
            )
        );

        String requestBody = """
        {
          "version": 2
        }
        """;

        mockMvc
            .perform(
                patch("/api/v1/admin/workshops/{id}/cancel", workshopId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER"))
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            );
    }
}
