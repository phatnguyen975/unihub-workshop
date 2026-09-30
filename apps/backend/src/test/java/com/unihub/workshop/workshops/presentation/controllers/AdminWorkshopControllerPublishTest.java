package com.unihub.workshop.workshops.presentation.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import com.unihub.workshop.workshops.application.ports.in.PublishWorkshopCommand;
import com.unihub.workshop.workshops.application.ports.in.PublishWorkshopUseCase;
import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.domain.model.WorkshopStatus;
import com.unihub.workshop.workshops.presentation.dto.PublishWorkshopRequest;
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

class AdminWorkshopControllerPublishTest {

    private MockMvc mockMvc;
    private PublishWorkshopUseCase publishWorkshopUseCase;
    private ObjectMapper objectMapper;

    private static final UUID CALLER_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        publishWorkshopUseCase = mock(PublishWorkshopUseCase.class);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        AdminWorkshopController controller =
            AdminWorkshopControllerTestBuilder.builder()
                .withPublishWorkshopUseCase(publishWorkshopUseCase)
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
        "Returns 200 OK with published workshop details when draft is successfully published"
    )
    void shouldReturn200WhenWorkshopPublishedSuccessfully() throws Exception {
        UUID workshopId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant startsAt = now.plusSeconds(86400 * 2);
        Instant endsAt = startsAt.plusSeconds(7200);

        Workshop publishedWorkshop = Workshop.builder()
            .id(workshopId)
            .organizationId(ORG_ID)
            .title("Advanced Microservices")
            .slug("advanced-microservices-a1b2")
            .description("Deep dive into Spring Modulith")
            .coverImageUrl("https://example.com/cover.jpg")
            .location("Auditorium A")
            .timezone("Asia/Ho_Chi_Minh")
            .startsAt(startsAt)
            .endsAt(endsAt)
            .capacity(100)
            .price(BigDecimal.ZERO)
            .currency("USD")
            .status(WorkshopStatus.PUBLISHED)
            .version(1)
            .publishedAt(now)
            .createdAt(now.minusSeconds(86400))
            .updatedAt(now)
            .createdBy(CALLER_ID)
            .updatedBy(CALLER_ID)
            .speakers(List.of())
            .build();

        when(
            publishWorkshopUseCase.execute(any(PublishWorkshopCommand.class))
        ).thenReturn(publishedWorkshop);

        PublishWorkshopRequest request = new PublishWorkshopRequest(0);

        mockMvc
            .perform(
                patch("/api/v1/admin/workshops/{id}/publish", workshopId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER"))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(
                jsonPath("$.message").value("Workshop published successfully.")
            )
            .andExpect(jsonPath("$.data.id").value(workshopId.toString()))
            .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
            .andExpect(jsonPath("$.data.publishedAt").isNotEmpty())
            .andExpect(jsonPath("$.data.version").value(1));
    }

    @Test
    @DisplayName(
        "Returns 422 Unprocessable Content when version in request body is missing or null"
    )
    void shouldReturn422WhenVersionIsNull() throws Exception {
        UUID workshopId = UUID.randomUUID();

        mockMvc
            .perform(
                patch("/api/v1/admin/workshops/{id}/publish", workshopId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
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
        "Returns 409 Conflict when optimistic locking version mismatch occurs"
    )
    void shouldReturn409WhenVersionMismatch() throws Exception {
        UUID workshopId = UUID.randomUUID();

        when(
            publishWorkshopUseCase.execute(any(PublishWorkshopCommand.class))
        ).thenThrow(
            new ConflictException(
                "Workshop version conflict: expected version 0 but found version 1."
            )
        );

        PublishWorkshopRequest request = new PublishWorkshopRequest(0);

        mockMvc
            .perform(
                patch("/api/v1/admin/workshops/{id}/publish", workshopId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER"))
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:conflict")
            )
            .andExpect(
                jsonPath("$.error.detail").value(
                    "Workshop version conflict: expected version 0 but found version 1."
                )
            );
    }

    @Test
    @DisplayName(
        "Returns 422 Unprocessable Content when draft is incomplete or speaker conflict occurs"
    )
    void shouldReturn422WhenDraftIncomplete() throws Exception {
        UUID workshopId = UUID.randomUUID();

        when(
            publishWorkshopUseCase.execute(any(PublishWorkshopCommand.class))
        ).thenThrow(
            new ValidationException(
                "Workshop cannot be published from DRAFT when required fields are missing.",
                List.of(
                    "location: must not be null or blank",
                    "endsAt: must not be null"
                )
            )
        );

        PublishWorkshopRequest request = new PublishWorkshopRequest(0);

        mockMvc
            .perform(
                patch("/api/v1/admin/workshops/{id}/publish", workshopId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER"))
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            )
            .andExpect(jsonPath("$.error.validationErrors").isArray());
    }

    @Test
    @DisplayName("Returns 404 Not Found when workshop does not exist")
    void shouldReturn404WhenWorkshopNotFound() throws Exception {
        UUID workshopId = UUID.randomUUID();

        when(
            publishWorkshopUseCase.execute(any(PublishWorkshopCommand.class))
        ).thenThrow(
            new NotFoundException("Workshop not found with id: " + workshopId)
        );

        PublishWorkshopRequest request = new PublishWorkshopRequest(0);

        mockMvc
            .perform(
                patch("/api/v1/admin/workshops/{id}/publish", workshopId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
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
        "Returns 403 Forbidden when organizer attempts to publish workshop of another organization"
    )
    void shouldReturn403WhenOrganizationMismatch() throws Exception {
        UUID workshopId = UUID.randomUUID();

        when(
            publishWorkshopUseCase.execute(any(PublishWorkshopCommand.class))
        ).thenThrow(
            new ForbiddenException(
                "You do not have permission to publish workshops for another organization."
            )
        );

        PublishWorkshopRequest request = new PublishWorkshopRequest(0);

        mockMvc
            .perform(
                patch("/api/v1/admin/workshops/{id}/publish", workshopId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER"))
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:forbidden")
            );
    }
}
