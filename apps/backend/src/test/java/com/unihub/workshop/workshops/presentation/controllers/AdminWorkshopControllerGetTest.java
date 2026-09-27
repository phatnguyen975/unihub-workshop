package com.unihub.workshop.workshops.presentation.controllers;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.security.JwtAuthenticatedPrincipal;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
import com.unihub.workshop.workshops.application.ports.in.GetWorkshopUseCase;
import com.unihub.workshop.workshops.domain.model.Speaker;
import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.domain.model.WorkshopStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminWorkshopControllerGetTest {

    private MockMvc mockMvc;
    private GetWorkshopUseCase getWorkshopUseCase;

    private static final UUID CALLER_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID WORKSHOP_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        getWorkshopUseCase = mock(GetWorkshopUseCase.class);

        AdminWorkshopController controller =
            AdminWorkshopControllerTestBuilder.builder()
                .withGetWorkshopUseCase(getWorkshopUseCase)
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

    private Workshop sampleWorkshop() {
        Speaker speaker = Speaker.builder()
            .id(UUID.fromString("11111111-2222-3333-4444-555555555555"))
            .name("Dr. Alan Turing")
            .email("alan.turing@university.edu")
            .bio("AI Researcher")
            .avatarUrl(
                "http://localhost:9000/unihub-media/speakers/avatars/alan.jpg"
            )
            .linkedinUrl("https://linkedin.com/in/alan-turing")
            .sortOrder(0)
            .build();

        return Workshop.builder()
            .id(WORKSHOP_ID)
            .organizationId(ORG_ID)
            .title("Advanced AI Workshop")
            .slug("advanced-ai-workshop")
            .description("Comprehensive workshop on neural networks")
            .coverImageUrl(
                "http://localhost:9000/unihub-media/workshops/covers/ai.jpg"
            )
            .location("Hall A")
            .timezone("Asia/Ho_Chi_Minh")
            .startsAt(Instant.parse("2026-10-15T09:00:00Z"))
            .endsAt(Instant.parse("2026-10-15T12:00:00Z"))
            .capacity(100)
            .price(BigDecimal.ZERO)
            .currency("USD")
            .status(WorkshopStatus.DRAFT)
            .version(0)
            .speakers(List.of(speaker))
            .createdAt(Instant.parse("2026-09-20T08:00:00Z"))
            .updatedAt(Instant.parse("2026-09-20T08:00:00Z"))
            .build();
    }

    @Test
    @DisplayName(
        "GET /api/v1/admin/workshops/{id} returns 200 OK with full workshop details and speakers"
    )
    void getWorkshop_whenOwnedByCallerOrg_returns200OkWithDetails()
        throws Exception {
        when(
            getWorkshopUseCase.execute(eq(WORKSHOP_ID), eq(ORG_ID))
        ).thenReturn(sampleWorkshop());

        mockMvc
            .perform(
                get("/api/v1/admin/workshops/{id}", WORKSHOP_ID).principal(
                    organizerAuth()
                )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(
                jsonPath("$.message").value("Workshop retrieved successfully.")
            )
            .andExpect(jsonPath("$.data.id").value(WORKSHOP_ID.toString()))
            .andExpect(
                jsonPath("$.data.organizationId").value(ORG_ID.toString())
            )
            .andExpect(jsonPath("$.data.title").value("Advanced AI Workshop"))
            .andExpect(jsonPath("$.data.slug").value("advanced-ai-workshop"))
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.version").value(0))
            .andExpect(
                jsonPath("$.data.speakers[0].id").value(
                    "11111111-2222-3333-4444-555555555555"
                )
            )
            .andExpect(
                jsonPath("$.data.speakers[0].name").value("Dr. Alan Turing")
            )
            .andExpect(
                jsonPath("$.data.speakers[0].email").value(
                    "alan.turing@university.edu"
                )
            )
            .andExpect(
                jsonPath("$.data.speakers[0].bio").value("AI Researcher")
            )
            .andExpect(jsonPath("$.data.speakers[0].sortOrder").value(0));

        verify(getWorkshopUseCase).execute(eq(WORKSHOP_ID), eq(ORG_ID));
    }

    @Test
    @DisplayName(
        "GET /api/v1/admin/workshops/{id} for non-existent workshop returns 404 Not Found"
    )
    void getWorkshop_whenNotFound_returns404NotFound() throws Exception {
        when(getWorkshopUseCase.execute(eq(WORKSHOP_ID), eq(ORG_ID))).thenThrow(
            new NotFoundException("Workshop not found with id: " + WORKSHOP_ID)
        );

        mockMvc
            .perform(
                get("/api/v1/admin/workshops/{id}", WORKSHOP_ID).principal(
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
        "GET /api/v1/admin/workshops/{id} for different organization returns 403 Forbidden"
    )
    void getWorkshop_whenDifferentOrg_returns403Forbidden() throws Exception {
        when(getWorkshopUseCase.execute(eq(WORKSHOP_ID), eq(ORG_ID))).thenThrow(
            new ForbiddenException(
                "You do not have permission to access this workshop."
            )
        );

        mockMvc
            .perform(
                get("/api/v1/admin/workshops/{id}", WORKSHOP_ID).principal(
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
        "GET /api/v1/admin/workshops/{id} when caller is not an ORGANIZER returns 403 Forbidden"
    )
    void getWorkshop_whenCallerNotOrganizer_returns403Forbidden()
        throws Exception {
        mockMvc
            .perform(
                get("/api/v1/admin/workshops/{id}", WORKSHOP_ID).principal(
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
