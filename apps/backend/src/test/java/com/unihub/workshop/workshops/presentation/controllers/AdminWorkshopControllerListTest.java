package com.unihub.workshop.workshops.presentation.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.shared.security.JwtAuthenticatedPrincipal;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
import com.unihub.workshop.workshops.application.ports.in.ListAdminWorkshopsQuery;
import com.unihub.workshop.workshops.application.ports.in.ListAdminWorkshopsUseCase;
import com.unihub.workshop.workshops.domain.model.WorkshopStatus;
import com.unihub.workshop.workshops.domain.model.WorkshopSummary;
import java.math.BigDecimal;
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

class AdminWorkshopControllerListTest {

    private MockMvc mockMvc;
    private ListAdminWorkshopsUseCase listAdminWorkshopsUseCase;

    private static final UUID CALLER_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        listAdminWorkshopsUseCase = mock(ListAdminWorkshopsUseCase.class);

        AdminWorkshopController controller =
            AdminWorkshopControllerTestBuilder.builder()
                .withListAdminWorkshopsUseCase(listAdminWorkshopsUseCase)
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
            ORG_ID.toString(),
            "jti-456",
            "STUDENT",
            UUID.randomUUID().toString(),
            Instant.now(),
            Instant.now().plusSeconds(3600)
        );
        return new TestingAuthenticationToken(principal, null, "ROLE_STUDENT");
    }

    private TestingAuthenticationToken noOrgAuth() {
        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
            CALLER_ID.toString(),
            null,
            "jti-789",
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
        "GET /api/v1/admin/workshops returns 200 OK with items and cursor envelope"
    )
    void shouldReturn200WithWorkshopList() throws Exception {
        UUID wId = UUID.randomUUID();
        WorkshopSummary summary = WorkshopSummary.builder()
            .id(wId)
            .title("Introduction to AI")
            .slug("introduction-to-ai-a1b2")
            .description("Comprehensive intro")
            .coverImageUrl("http://localhost:9000/media/cover.jpg")
            .location("Auditorium B")
            .timezone("Asia/Ho_Chi_Minh")
            .startsAt(Instant.parse("2026-10-15T09:00:00Z"))
            .endsAt(Instant.parse("2026-10-15T12:00:00Z"))
            .capacity(100)
            .price(BigDecimal.ZERO)
            .currency("USD")
            .status(WorkshopStatus.DRAFT)
            .version(0)
            .speakerCount(2)
            .createdAt(Instant.parse("2026-09-25T10:00:00Z"))
            .updatedAt(Instant.parse("2026-09-25T10:30:00Z"))
            .build();

        CursorPage<WorkshopSummary> page = new CursorPage<>(
            List.of(summary),
            "next-cursor-token",
            true
        );
        when(listAdminWorkshopsUseCase.execute(any())).thenReturn(page);

        mockMvc
            .perform(get("/api/v1/admin/workshops").principal(organizerAuth()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(
                jsonPath("$.message").value("Workshops retrieved successfully.")
            )
            .andExpect(jsonPath("$.data.items[0].id").value(wId.toString()))
            .andExpect(
                jsonPath("$.data.items[0].slug").value(
                    "introduction-to-ai-a1b2"
                )
            )
            .andExpect(
                jsonPath("$.data.items[0].title").value("Introduction to AI")
            )
            .andExpect(jsonPath("$.data.items[0].speakerCount").value(2))
            .andExpect(jsonPath("$.data.items[0].status").value("DRAFT"))
            .andExpect(
                jsonPath("$.data.cursor.nextCursor").value("next-cursor-token")
            )
            .andExpect(jsonPath("$.data.cursor.hasMore").value(true));
    }

    @Test
    @DisplayName(
        "GET /api/v1/admin/workshops with all query parameters correctly constructs query"
    )
    void shouldPassQueryParametersToUseCase() throws Exception {
        when(listAdminWorkshopsUseCase.execute(any())).thenReturn(
            new CursorPage<>(List.of(), null, false)
        );

        mockMvc
            .perform(
                get("/api/v1/admin/workshops")
                    .principal(organizerAuth())
                    .param("status", "DRAFT")
                    .param("priceType", "FREE")
                    .param("q", "spring")
                    .param("from", "2026-10-01T00:00:00Z")
                    .param("to", "2026-10-31T23:59:59Z")
                    .param("sort", "price,asc")
                    .param("cursor", "existing-cursor")
                    .param("size", "15")
            )
            .andExpect(status().isOk());

        ArgumentCaptor<ListAdminWorkshopsQuery> captor =
            ArgumentCaptor.forClass(ListAdminWorkshopsQuery.class);
        verify(listAdminWorkshopsUseCase).execute(captor.capture());
        ListAdminWorkshopsQuery captured = captor.getValue();

        assertThat(captured.organizationId()).isEqualTo(ORG_ID);
        assertThat(captured.status()).isEqualTo(WorkshopStatus.DRAFT);
        assertThat(captured.priceType()).isEqualTo("FREE");
        assertThat(captured.q()).isEqualTo("spring");
        assertThat(captured.from()).isEqualTo(
            Instant.parse("2026-10-01T00:00:00Z")
        );
        assertThat(captured.to()).isEqualTo(
            Instant.parse("2026-10-31T23:59:59Z")
        );
        assertThat(captured.sort()).isEqualTo("price,asc");
        assertThat(captured.cursor()).isEqualTo("existing-cursor");
        assertThat(captured.size()).isEqualTo(15);
    }

    @Test
    @DisplayName(
        "GET /api/v1/admin/workshops returns 403 Forbidden for non-ORGANIZER role"
    )
    void shouldReturn403ForNonOrganizer() throws Exception {
        mockMvc
            .perform(get("/api/v1/admin/workshops").principal(studentAuth()))
            .andExpect(status().isForbidden())
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:forbidden")
            );
    }

    @Test
    @DisplayName(
        "GET /api/v1/admin/workshops returns 403 Forbidden when org claim is missing"
    )
    void shouldReturn403WhenOrgClaimMissing() throws Exception {
        mockMvc
            .perform(get("/api/v1/admin/workshops").principal(noOrgAuth()))
            .andExpect(status().isForbidden())
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:forbidden")
            );
    }

    @Test
    @DisplayName(
        "GET /api/v1/admin/workshops returns 400 Bad Request for invalid status"
    )
    void shouldReturn400ForInvalidStatus() throws Exception {
        mockMvc
            .perform(
                get("/api/v1/admin/workshops")
                    .principal(organizerAuth())
                    .param("status", "INVALID_STATUS")
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:bad-request")
            )
            .andExpect(
                jsonPath("$.error.detail").value(
                    "Invalid status: INVALID_STATUS"
                )
            );
    }

    @Test
    @DisplayName(
        "GET /api/v1/admin/workshops returns 400 Bad Request for invalid timestamp format"
    )
    void shouldReturn400ForInvalidTimestamp() throws Exception {
        mockMvc
            .perform(
                get("/api/v1/admin/workshops")
                    .principal(organizerAuth())
                    .param("from", "not-a-timestamp")
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:bad-request")
            )
            .andExpect(
                jsonPath("$.error.detail").value(
                    "Invalid 'from' timestamp format: not-a-timestamp. Expected ISO-8601 UTC timestamp."
                )
            );
    }
}
