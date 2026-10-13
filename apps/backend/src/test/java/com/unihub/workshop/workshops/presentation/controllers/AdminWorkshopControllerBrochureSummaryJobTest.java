package com.unihub.workshop.workshops.presentation.controllers;

import static org.mockito.ArgumentMatchers.any;
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
import com.unihub.workshop.workshops.application.ports.in.GetBrochureSummaryJobQuery;
import com.unihub.workshop.workshops.application.ports.in.GetBrochureSummaryJobUseCase;
import com.unihub.workshop.workshops.domain.model.AiSummaryJob;
import com.unihub.workshop.workshops.domain.model.AiSummaryJobStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminWorkshopControllerBrochureSummaryJobTest {

    private MockMvc mockMvc;
    private GetBrochureSummaryJobUseCase getBrochureSummaryJobUseCase;

    private static final UUID CALLER_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID WORKSHOP_ID = UUID.randomUUID();
    private static final UUID BROCHURE_ID = UUID.randomUUID();
    private static final UUID JOB_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        getBrochureSummaryJobUseCase = mock(GetBrochureSummaryJobUseCase.class);

        AdminWorkshopController controller =
            AdminWorkshopControllerTestBuilder.builder()
                .withGetBrochureSummaryJobUseCase(getBrochureSummaryJobUseCase)
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
        "GET /api/v1/admin/workshops/{id}/brochure/summary-job should return 200 with completed job details"
    )
    void getSummaryJobCompletedSuccess() throws Exception {
        Instant createdAt = Instant.parse("2026-10-12T00:00:00Z");
        Instant completedAt = Instant.parse("2026-10-12T00:05:00Z");

        AiSummaryJob job = AiSummaryJob.builder()
            .id(JOB_ID)
            .workshopId(WORKSHOP_ID)
            .brochureId(BROCHURE_ID)
            .status(AiSummaryJobStatus.COMPLETED)
            .resultText("## Workshop Summary\n\n**Objectives:** Test")
            .errorMessage(null)
            .attempts(1)
            .completedAt(completedAt)
            .createdAt(createdAt)
            .build();

        when(
            getBrochureSummaryJobUseCase.getSummaryJob(
                any(GetBrochureSummaryJobQuery.class)
            )
        ).thenReturn(job);

        mockMvc
            .perform(
                get(
                    "/api/v1/admin/workshops/{id}/brochure/summary-job",
                    WORKSHOP_ID
                ).principal(organizerAuth())
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Summary job retrieved."))
            .andExpect(jsonPath("$.data.jobId").value(JOB_ID.toString()))
            .andExpect(
                jsonPath("$.data.brochureId").value(BROCHURE_ID.toString())
            )
            .andExpect(
                jsonPath("$.data.workshopId").value(WORKSHOP_ID.toString())
            )
            .andExpect(jsonPath("$.data.status").value("COMPLETED"))
            .andExpect(
                jsonPath("$.data.resultText").value(
                    "## Workshop Summary\n\n**Objectives:** Test"
                )
            )
            .andExpect(jsonPath("$.data.errorMessage").doesNotExist())
            .andExpect(jsonPath("$.data.attempts").value(1))
            .andExpect(
                jsonPath("$.data.completedAt").value("2026-10-12T00:05:00Z")
            )
            .andExpect(
                jsonPath("$.data.createdAt").value("2026-10-12T00:00:00Z")
            );

        verify(getBrochureSummaryJobUseCase).getSummaryJob(
            new GetBrochureSummaryJobQuery(WORKSHOP_ID, ORG_ID)
        );
    }

    @Test
    @DisplayName(
        "GET /api/v1/admin/workshops/{id}/brochure/summary-job should omit resultText, errorMessage, completedAt when status is PROCESSING"
    )
    void getSummaryJobProcessingSuccess() throws Exception {
        Instant createdAt = Instant.parse("2026-10-12T00:00:00Z");

        AiSummaryJob job = AiSummaryJob.builder()
            .id(JOB_ID)
            .workshopId(WORKSHOP_ID)
            .brochureId(BROCHURE_ID)
            .status(AiSummaryJobStatus.PROCESSING)
            .resultText(null)
            .errorMessage(null)
            .attempts(0)
            .completedAt(null)
            .createdAt(createdAt)
            .build();

        when(
            getBrochureSummaryJobUseCase.getSummaryJob(
                any(GetBrochureSummaryJobQuery.class)
            )
        ).thenReturn(job);

        mockMvc
            .perform(
                get(
                    "/api/v1/admin/workshops/{id}/brochure/summary-job",
                    WORKSHOP_ID
                ).principal(organizerAuth())
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.jobId").value(JOB_ID.toString()))
            .andExpect(jsonPath("$.data.status").value("PROCESSING"))
            .andExpect(jsonPath("$.data.attempts").value(0))
            .andExpect(jsonPath("$.data.resultText").doesNotExist())
            .andExpect(jsonPath("$.data.errorMessage").doesNotExist())
            .andExpect(jsonPath("$.data.completedAt").doesNotExist());
    }

    @Test
    @DisplayName(
        "GET /api/v1/admin/workshops/{id}/brochure/summary-job should return errorMessage and omit resultText when status is FAILED"
    )
    void getSummaryJobFailedSuccess() throws Exception {
        Instant createdAt = Instant.parse("2026-10-12T00:00:00Z");
        Instant completedAt = Instant.parse("2026-10-12T00:02:00Z");

        AiSummaryJob job = AiSummaryJob.builder()
            .id(JOB_ID)
            .workshopId(WORKSHOP_ID)
            .brochureId(BROCHURE_ID)
            .status(AiSummaryJobStatus.FAILED)
            .resultText(null)
            .errorMessage("No extractable text found in PDF brochure")
            .attempts(1)
            .completedAt(completedAt)
            .createdAt(createdAt)
            .build();

        when(
            getBrochureSummaryJobUseCase.getSummaryJob(
                any(GetBrochureSummaryJobQuery.class)
            )
        ).thenReturn(job);

        mockMvc
            .perform(
                get(
                    "/api/v1/admin/workshops/{id}/brochure/summary-job",
                    WORKSHOP_ID
                ).principal(organizerAuth())
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("FAILED"))
            .andExpect(
                jsonPath("$.data.errorMessage").value(
                    "No extractable text found in PDF brochure"
                )
            )
            .andExpect(jsonPath("$.data.resultText").doesNotExist())
            .andExpect(
                jsonPath("$.data.completedAt").value("2026-10-12T00:02:00Z")
            );
    }

    @Test
    @DisplayName(
        "GET /api/v1/admin/workshops/{id}/brochure/summary-job should return 404 when workshop or brochure does not exist"
    )
    void getSummaryJobNotFound() throws Exception {
        when(
            getBrochureSummaryJobUseCase.getSummaryJob(
                any(GetBrochureSummaryJobQuery.class)
            )
        ).thenThrow(
            new NotFoundException("Workshop not found with id: " + WORKSHOP_ID)
        );

        mockMvc
            .perform(
                get(
                    "/api/v1/admin/workshops/{id}/brochure/summary-job",
                    WORKSHOP_ID
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
        "GET /api/v1/admin/workshops/{id}/brochure/summary-job should return 403 when organization does not match"
    )
    void getSummaryJobForbidden() throws Exception {
        when(
            getBrochureSummaryJobUseCase.getSummaryJob(
                any(GetBrochureSummaryJobQuery.class)
            )
        ).thenThrow(new ForbiddenException("You do not have permission"));

        mockMvc
            .perform(
                get(
                    "/api/v1/admin/workshops/{id}/brochure/summary-job",
                    WORKSHOP_ID
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
        "GET /api/v1/admin/workshops/{id}/brochure/summary-job should return 403 when user is not an ORGANIZER"
    )
    void getSummaryJobForbiddenForNonOrganizer() throws Exception {
        mockMvc
            .perform(
                get(
                    "/api/v1/admin/workshops/{id}/brochure/summary-job",
                    WORKSHOP_ID
                ).principal(studentAuth())
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:forbidden")
            );
    }
}
