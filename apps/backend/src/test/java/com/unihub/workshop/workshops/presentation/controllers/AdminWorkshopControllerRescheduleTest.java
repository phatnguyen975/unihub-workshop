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
import com.unihub.workshop.workshops.application.ports.in.RescheduleWorkshopCommand;
import com.unihub.workshop.workshops.application.ports.in.RescheduleWorkshopUseCase;
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

class AdminWorkshopControllerRescheduleTest {

    private MockMvc mockMvc;
    private RescheduleWorkshopUseCase rescheduleWorkshopUseCase;

    private static final UUID CALLER_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        rescheduleWorkshopUseCase = mock(RescheduleWorkshopUseCase.class);

        AdminWorkshopController controller =
            AdminWorkshopControllerTestBuilder.builder()
                .withRescheduleWorkshopUseCase(rescheduleWorkshopUseCase)
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
    @DisplayName("Returns 200 OK when workshop is rescheduled successfully")
    void shouldReturn200WhenWorkshopRescheduledSuccessfully() throws Exception {
        UUID workshopId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant newStartsAt = now.plusSeconds(86400 * 5);
        Instant newEndsAt = newStartsAt.plusSeconds(7200);

        Workshop rescheduled = Workshop.builder()
            .id(workshopId)
            .organizationId(ORG_ID)
            .title("Advanced Microservices")
            .slug("advanced-microservices-a1b2")
            .description("Deep dive into Spring Modulith")
            .coverImageUrl("https://example.com/cover.jpg")
            .location("Auditorium A, Building C")
            .timezone("Asia/Ho_Chi_Minh")
            .startsAt(newStartsAt)
            .endsAt(newEndsAt)
            .capacity(100)
            .price(BigDecimal.ZERO)
            .currency("USD")
            .status(WorkshopStatus.PUBLISHED)
            .version(3)
            .publishedAt(now.minusSeconds(86400))
            .createdAt(now.minusSeconds(86400 * 2))
            .updatedAt(now)
            .createdBy(CALLER_ID)
            .updatedBy(CALLER_ID)
            .speakers(List.of())
            .build();

        when(
            rescheduleWorkshopUseCase.execute(
                any(RescheduleWorkshopCommand.class)
            )
        ).thenReturn(rescheduled);

        String requestBody = String.format(
            """
            {
              "version": 2,
              "startsAt": "%s",
              "endsAt": "%s"
            }
            """,
            newStartsAt,
            newEndsAt
        );

        mockMvc
            .perform(
                patch("/api/v1/admin/workshops/{id}/reschedule", workshopId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER"))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(
                jsonPath("$.message").value(
                    "Workshop rescheduled successfully."
                )
            )
            .andExpect(jsonPath("$.data.id").value(workshopId.toString()))
            .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
            .andExpect(jsonPath("$.data.version").value(3))
            .andExpect(jsonPath("$.data.startsAt").isNotEmpty())
            .andExpect(jsonPath("$.data.endsAt").isNotEmpty());
    }

    @Test
    @DisplayName(
        "Returns 422 Unprocessable Content when version is missing or null"
    )
    void shouldReturn422WhenVersionIsNull() throws Exception {
        UUID workshopId = UUID.randomUUID();
        Instant newStartsAt = Instant.now().plusSeconds(86400 * 5);
        Instant newEndsAt = newStartsAt.plusSeconds(7200);

        String requestBody = String.format(
            """
            {
              "startsAt": "%s",
              "endsAt": "%s"
            }
            """,
            newStartsAt,
            newEndsAt
        );

        mockMvc
            .perform(
                patch("/api/v1/admin/workshops/{id}/reschedule", workshopId)
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
        "Returns 422 Unprocessable Content when startsAt or endsAt is missing"
    )
    void shouldReturn422WhenScheduleTimesMissing() throws Exception {
        UUID workshopId = UUID.randomUUID();

        String missingStartsAt = """
        {
          "version": 1,
          "endsAt": "2026-12-01T12:00:00Z"
        }
        """;

        mockMvc
            .perform(
                patch("/api/v1/admin/workshops/{id}/reschedule", workshopId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(missingStartsAt)
                    .principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER"))
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            );

        String missingEndsAt = """
        {
          "version": 1,
          "startsAt": "2026-12-01T10:00:00Z"
        }
        """;

        mockMvc
            .perform(
                patch("/api/v1/admin/workshops/{id}/reschedule", workshopId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(missingEndsAt)
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
        Instant newStartsAt = Instant.now().plusSeconds(86400 * 5);
        Instant newEndsAt = newStartsAt.plusSeconds(7200);

        when(
            rescheduleWorkshopUseCase.execute(
                any(RescheduleWorkshopCommand.class)
            )
        ).thenThrow(
            new ValidationException(
                "Field 'location' cannot be modified via the reschedule endpoint.",
                List.of("location: Field is prohibited on reschedule")
            )
        );

        String requestBody = String.format(
            """
            {
              "version": 2,
              "startsAt": "%s",
              "endsAt": "%s",
              "location": "Auditorium B"
            }
            """,
            newStartsAt,
            newEndsAt
        );

        mockMvc
            .perform(
                patch("/api/v1/admin/workshops/{id}/reschedule", workshopId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER"))
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            )
            .andExpect(
                jsonPath("$.error.detail").value(
                    "Field 'location' cannot be modified via the reschedule endpoint."
                )
            );
    }

    @Test
    @DisplayName(
        "Returns 409 Conflict when optimistic locking version mismatch occurs"
    )
    void shouldReturn409WhenVersionMismatch() throws Exception {
        UUID workshopId = UUID.randomUUID();
        Instant newStartsAt = Instant.now().plusSeconds(86400 * 5);
        Instant newEndsAt = newStartsAt.plusSeconds(7200);

        when(
            rescheduleWorkshopUseCase.execute(
                any(RescheduleWorkshopCommand.class)
            )
        ).thenThrow(
            new ConflictException(
                "Workshop version conflict: expected version 1 but found version 2."
            )
        );

        String requestBody = String.format(
            """
            {
              "version": 1,
              "startsAt": "%s",
              "endsAt": "%s"
            }
            """,
            newStartsAt,
            newEndsAt
        );

        mockMvc
            .perform(
                patch("/api/v1/admin/workshops/{id}/reschedule", workshopId)
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
        "Returns 403 Forbidden when organizer attempts to reschedule workshop of another organization"
    )
    void shouldReturn403WhenOrganizationMismatch() throws Exception {
        UUID workshopId = UUID.randomUUID();
        Instant newStartsAt = Instant.now().plusSeconds(86400 * 5);
        Instant newEndsAt = newStartsAt.plusSeconds(7200);

        when(
            rescheduleWorkshopUseCase.execute(
                any(RescheduleWorkshopCommand.class)
            )
        ).thenThrow(
            new ForbiddenException(
                "You do not have permission to reschedule workshops for another organization."
            )
        );

        String requestBody = String.format(
            """
            {
              "version": 2,
              "startsAt": "%s",
              "endsAt": "%s"
            }
            """,
            newStartsAt,
            newEndsAt
        );

        mockMvc
            .perform(
                patch("/api/v1/admin/workshops/{id}/reschedule", workshopId)
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
    @DisplayName("Returns 404 Not Found when workshop does not exist")
    void shouldReturn404WhenWorkshopNotFound() throws Exception {
        UUID workshopId = UUID.randomUUID();
        Instant newStartsAt = Instant.now().plusSeconds(86400 * 5);
        Instant newEndsAt = newStartsAt.plusSeconds(7200);

        when(
            rescheduleWorkshopUseCase.execute(
                any(RescheduleWorkshopCommand.class)
            )
        ).thenThrow(
            new NotFoundException("Workshop not found with id: " + workshopId)
        );

        String requestBody = String.format(
            """
            {
              "version": 2,
              "startsAt": "%s",
              "endsAt": "%s"
            }
            """,
            newStartsAt,
            newEndsAt
        );

        mockMvc
            .perform(
                patch("/api/v1/admin/workshops/{id}/reschedule", workshopId)
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
        "Returns 422 Unprocessable Content when speaker schedule collision is detected"
    )
    void shouldReturn422WhenSpeakerConflictDetected() throws Exception {
        UUID workshopId = UUID.randomUUID();
        Instant newStartsAt = Instant.now().plusSeconds(86400 * 5);
        Instant newEndsAt = newStartsAt.plusSeconds(7200);

        when(
            rescheduleWorkshopUseCase.execute(
                any(RescheduleWorkshopCommand.class)
            )
        ).thenThrow(
            new ValidationException(
                "Speaker schedule collision detected for speaker email: speaker@university.edu in the new time window.",
                List.of(
                    "speakers: Speaker 'speaker@university.edu' has an overlapping schedule with another active workshop in the new time window"
                )
            )
        );

        String requestBody = String.format(
            """
            {
              "version": 2,
              "startsAt": "%s",
              "endsAt": "%s"
            }
            """,
            newStartsAt,
            newEndsAt
        );

        mockMvc
            .perform(
                patch("/api/v1/admin/workshops/{id}/reschedule", workshopId)
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
        "Returns 422 Unprocessable Content when campus-wide location collision is detected"
    )
    void shouldReturn422WhenLocationConflictDetected() throws Exception {
        UUID workshopId = UUID.randomUUID();
        Instant newStartsAt = Instant.now().plusSeconds(86400 * 5);
        Instant newEndsAt = newStartsAt.plusSeconds(7200);

        when(
            rescheduleWorkshopUseCase.execute(
                any(RescheduleWorkshopCommand.class)
            )
        ).thenThrow(
            new ValidationException(
                "location",
                "Location collision detected: another active workshop is scheduled at the same location during the new time window."
            )
        );

        String requestBody = String.format(
            """
            {
              "version": 2,
              "startsAt": "%s",
              "endsAt": "%s"
            }
            """,
            newStartsAt,
            newEndsAt
        );

        mockMvc
            .perform(
                patch("/api/v1/admin/workshops/{id}/reschedule", workshopId)
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
