package com.unihub.workshop.workshops.presentation.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unihub.workshop.shared.exception.ConflictException;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.shared.security.JwtAuthenticatedPrincipal;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
import com.unihub.workshop.workshops.application.ports.in.UpdateWorkshopCommand;
import com.unihub.workshop.workshops.application.ports.in.UpdateWorkshopUseCase;
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

class AdminWorkshopControllerUpdatePublishedTest {

    private MockMvc mockMvc;
    private UpdateWorkshopUseCase updateWorkshopUseCase;

    private static final UUID CALLER_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        updateWorkshopUseCase = mock(UpdateWorkshopUseCase.class);

        AdminWorkshopController controller =
            AdminWorkshopControllerTestBuilder.builder()
                .withUpdateWorkshopUseCase(updateWorkshopUseCase)
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
        "Returns 200 OK when published workshop presentation and capacity fields are updated"
    )
    void shouldReturn200WhenWorkshopUpdatedSuccessfully() throws Exception {
        UUID workshopId = UUID.randomUUID();
        Instant now = Instant.now();

        Workshop updatedWorkshop = Workshop.builder()
            .id(workshopId)
            .organizationId(ORG_ID)
            .title("New Title")
            .slug("new-title-a1b2")
            .description("New Desc")
            .coverImageUrl("https://example.com/new.png")
            .location("Auditorium A")
            .timezone("Asia/Ho_Chi_Minh")
            .startsAt(now.plusSeconds(86400 * 2))
            .endsAt(now.plusSeconds(86400 * 2 + 7200))
            .capacity(150)
            .price(BigDecimal.ZERO)
            .currency("USD")
            .status(WorkshopStatus.PUBLISHED)
            .version(2)
            .publishedAt(now.minusSeconds(86400))
            .createdAt(now.minusSeconds(86400 * 2))
            .updatedAt(now)
            .createdBy(CALLER_ID)
            .updatedBy(CALLER_ID)
            .speakers(List.of())
            .build();

        when(
            updateWorkshopUseCase.execute(any(UpdateWorkshopCommand.class))
        ).thenReturn(updatedWorkshop);

        String requestBody = """
        {
          "version": 1,
          "title": "New Title",
          "capacity": 150
        }
        """;

        mockMvc
            .perform(
                put("/api/v1/admin/workshops/{id}", workshopId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER"))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(
                jsonPath("$.message").value("Workshop updated successfully.")
            )
            .andExpect(jsonPath("$.data.title").value("New Title"))
            .andExpect(jsonPath("$.data.slug").value("new-title-a1b2"))
            .andExpect(jsonPath("$.data.capacity").value(150))
            .andExpect(jsonPath("$.data.version").value(2));
    }

    @Test
    @DisplayName("Returns 200 OK on partial update with only capacity provided")
    void shouldReturn200OnPartialUpdateWithCapacityOnly() throws Exception {
        UUID workshopId = UUID.randomUUID();
        Instant now = Instant.now();

        Workshop updatedWorkshop = Workshop.builder()
            .id(workshopId)
            .organizationId(ORG_ID)
            .title("Original Title")
            .slug("original-title")
            .description("Original Desc")
            .coverImageUrl("https://example.com/cover.png")
            .location("Auditorium A")
            .timezone("Asia/Ho_Chi_Minh")
            .startsAt(now.plusSeconds(86400 * 2))
            .endsAt(now.plusSeconds(86400 * 2 + 7200))
            .capacity(200)
            .price(BigDecimal.ZERO)
            .currency("USD")
            .status(WorkshopStatus.PUBLISHED)
            .version(2)
            .publishedAt(now.minusSeconds(86400))
            .createdAt(now.minusSeconds(86400 * 2))
            .updatedAt(now)
            .createdBy(CALLER_ID)
            .updatedBy(CALLER_ID)
            .speakers(List.of())
            .build();

        when(
            updateWorkshopUseCase.execute(any(UpdateWorkshopCommand.class))
        ).thenReturn(updatedWorkshop);

        String requestBody = """
        {
          "version": 1,
          "capacity": 200
        }
        """;

        mockMvc
            .perform(
                put("/api/v1/admin/workshops/{id}", workshopId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER"))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.title").value("Original Title"))
            .andExpect(jsonPath("$.data.capacity").value(200))
            .andExpect(jsonPath("$.data.version").value(2));
    }

    @Test
    @DisplayName(
        "Returns 422 Unprocessable Content when version in request body is missing"
    )
    void shouldReturn422WhenVersionIsMissing() throws Exception {
        UUID workshopId = UUID.randomUUID();

        mockMvc
            .perform(
                put("/api/v1/admin/workshops/{id}", workshopId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"capacity\": 150}")
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
    void shouldReturn422WhenProhibitedFieldIsProvided() throws Exception {
        UUID workshopId = UUID.randomUUID();

        when(
            updateWorkshopUseCase.execute(any(UpdateWorkshopCommand.class))
        ).thenThrow(
            new ValidationException(
                "Field 'price' cannot be modified on a published workshop.",
                List.of("price: Commercial pricing is immutable once published")
            )
        );

        String requestBody = """
        {
          "version": 1,
          "price": 25.00
        }
        """;

        mockMvc
            .perform(
                put("/api/v1/admin/workshops/{id}", workshopId)
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
                    "Field 'price' cannot be modified on a published workshop."
                )
            );
    }

    @Test
    @DisplayName(
        "Returns 422 Unprocessable Content when capacity is reduced below active registrations"
    )
    void shouldReturn422WhenCapacityBelowActiveRegistrations()
        throws Exception {
        UUID workshopId = UUID.randomUUID();

        when(
            updateWorkshopUseCase.execute(any(UpdateWorkshopCommand.class))
        ).thenThrow(
            new ValidationException(
                "capacity",
                "Capacity cannot be reduced below current active registration count: 70"
            )
        );

        String requestBody = """
        {
          "version": 1,
          "capacity": 50
        }
        """;

        mockMvc
            .perform(
                put("/api/v1/admin/workshops/{id}", workshopId)
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
        "Returns 409 Conflict when optimistic locking version mismatch occurs"
    )
    void shouldReturn409WhenVersionMismatch() throws Exception {
        UUID workshopId = UUID.randomUUID();

        when(
            updateWorkshopUseCase.execute(any(UpdateWorkshopCommand.class))
        ).thenThrow(
            new ConflictException(
                "Workshop version conflict: current version is 2, but request provided 1."
            )
        );

        String requestBody = """
        {
          "version": 1,
          "title": "Title"
        }
        """;

        mockMvc
            .perform(
                put("/api/v1/admin/workshops/{id}", workshopId)
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
        "Returns 403 Forbidden when organizer attempts to update workshop of another organization"
    )
    void shouldReturn403WhenOrganizationMismatch() throws Exception {
        UUID workshopId = UUID.randomUUID();

        when(
            updateWorkshopUseCase.execute(any(UpdateWorkshopCommand.class))
        ).thenThrow(
            new ForbiddenException(
                "You do not have permission to update workshops for another organization."
            )
        );

        String requestBody = """
        {
          "version": 1,
          "title": "Title"
        }
        """;

        mockMvc
            .perform(
                put("/api/v1/admin/workshops/{id}", workshopId)
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

        when(
            updateWorkshopUseCase.execute(any(UpdateWorkshopCommand.class))
        ).thenThrow(
            new NotFoundException("Workshop not found with id: " + workshopId)
        );

        String requestBody = """
        {
          "version": 1,
          "title": "Title"
        }
        """;

        mockMvc
            .perform(
                put("/api/v1/admin/workshops/{id}", workshopId)
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
}
