package com.unihub.workshop.workshops.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.workshops.application.ports.in.GetBrochureSummaryJobQuery;
import com.unihub.workshop.workshops.application.ports.out.AiSummaryJobRepository;
import com.unihub.workshop.workshops.application.ports.out.WorkshopBrochureRepository;
import com.unihub.workshop.workshops.application.ports.out.WorkshopRepository;
import com.unihub.workshop.workshops.domain.model.AiSummaryJob;
import com.unihub.workshop.workshops.domain.model.AiSummaryJobStatus;
import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.domain.model.WorkshopBrochure;
import com.unihub.workshop.workshops.domain.model.WorkshopStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetBrochureSummaryJobUseCaseImplTest {

    @Mock
    private WorkshopRepository workshopRepository;

    @Mock
    private WorkshopBrochureRepository brochureRepository;

    @Mock
    private AiSummaryJobRepository jobRepository;

    private GetBrochureSummaryJobUseCaseImpl useCase;

    private static final UUID WORKSHOP_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID BROCHURE_ID = UUID.randomUUID();
    private static final UUID JOB_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new GetBrochureSummaryJobUseCaseImpl(
            workshopRepository,
            brochureRepository,
            jobRepository
        );
    }

    private Workshop createWorkshop(UUID organizationId) {
        return Workshop.builder()
            .id(WORKSHOP_ID)
            .organizationId(organizationId)
            .title("AI Workshop")
            .status(WorkshopStatus.PUBLISHED)
            .build();
    }

    private WorkshopBrochure createBrochure() {
        return WorkshopBrochure.builder()
            .id(BROCHURE_ID)
            .workshopId(WORKSHOP_ID)
            .storageKey(
                "workshops/" +
                    WORKSHOP_ID +
                    "/brochures/" +
                    BROCHURE_ID +
                    ".pdf"
            )
            .fileName("brochure.pdf")
            .contentType("application/pdf")
            .byteSize(1024L)
            .checksumSha256("abc123sha")
            .isActive(true)
            .uploadedAt(Instant.now())
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }

    private AiSummaryJob createJob(
        AiSummaryJobStatus status,
        String resultText,
        String errorMessage
    ) {
        return AiSummaryJob.builder()
            .id(JOB_ID)
            .workshopId(WORKSHOP_ID)
            .brochureId(BROCHURE_ID)
            .status(status)
            .resultText(resultText)
            .errorMessage(errorMessage)
            .attempts(1)
            .createdAt(Instant.now())
            .completedAt(
                status != AiSummaryJobStatus.PROCESSING ? Instant.now() : null
            )
            .build();
    }

    @Test
    @DisplayName(
        "Should return summary job when workshop, brochure, and job exist and org matches"
    )
    void shouldReturnSummaryJobSuccessfully() {
        GetBrochureSummaryJobQuery query = new GetBrochureSummaryJobQuery(
            WORKSHOP_ID,
            ORG_ID
        );
        Workshop workshop = createWorkshop(ORG_ID);
        WorkshopBrochure brochure = createBrochure();
        AiSummaryJob job = createJob(
            AiSummaryJobStatus.COMPLETED,
            "Generated summary text",
            null
        );

        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.of(workshop)
        );
        when(brochureRepository.findActiveByWorkshopId(WORKSHOP_ID)).thenReturn(
            Optional.of(brochure)
        );
        when(jobRepository.findLatestByWorkshopId(WORKSHOP_ID)).thenReturn(
            Optional.of(job)
        );

        AiSummaryJob result = useCase.getSummaryJob(query);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(JOB_ID);
        assertThat(result.workshopId()).isEqualTo(WORKSHOP_ID);
        assertThat(result.brochureId()).isEqualTo(BROCHURE_ID);
        assertThat(result.status()).isEqualTo(AiSummaryJobStatus.COMPLETED);
        assertThat(result.resultText()).isEqualTo("Generated summary text");
        assertThat(result.errorMessage()).isNull();

        verify(workshopRepository).findById(WORKSHOP_ID);
        verify(brochureRepository).findActiveByWorkshopId(WORKSHOP_ID);
        verify(jobRepository).findLatestByWorkshopId(WORKSHOP_ID);
    }

    @Test
    @DisplayName("Should throw NotFoundException when workshop does not exist")
    void shouldThrowNotFoundWhenWorkshopDoesNotExist() {
        GetBrochureSummaryJobQuery query = new GetBrochureSummaryJobQuery(
            WORKSHOP_ID,
            ORG_ID
        );

        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.empty()
        );

        assertThatThrownBy(() -> useCase.getSummaryJob(query))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("Workshop not found");

        verifyNoInteractions(brochureRepository);
        verifyNoInteractions(jobRepository);
    }

    @Test
    @DisplayName(
        "Should throw ForbiddenException when workshop belongs to different organization"
    )
    void shouldThrowForbiddenWhenOrgDoesNotMatch() {
        UUID differentOrgId = UUID.randomUUID();
        GetBrochureSummaryJobQuery query = new GetBrochureSummaryJobQuery(
            WORKSHOP_ID,
            ORG_ID
        );
        Workshop workshop = createWorkshop(differentOrgId);

        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.of(workshop)
        );

        assertThatThrownBy(() -> useCase.getSummaryJob(query))
            .isInstanceOf(ForbiddenException.class)
            .hasMessageContaining("permission");

        verifyNoInteractions(brochureRepository);
        verifyNoInteractions(jobRepository);
    }

    @Test
    @DisplayName(
        "Should throw NotFoundException when no active brochure has been uploaded"
    )
    void shouldThrowNotFoundWhenNoActiveBrochure() {
        GetBrochureSummaryJobQuery query = new GetBrochureSummaryJobQuery(
            WORKSHOP_ID,
            ORG_ID
        );
        Workshop workshop = createWorkshop(ORG_ID);

        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.of(workshop)
        );
        when(brochureRepository.findActiveByWorkshopId(WORKSHOP_ID)).thenReturn(
            Optional.empty()
        );

        assertThatThrownBy(() -> useCase.getSummaryJob(query))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("brochure");

        verifyNoInteractions(jobRepository);
    }

    @Test
    @DisplayName(
        "Should throw NotFoundException when brochure exists but no summary job exists"
    )
    void shouldThrowNotFoundWhenNoSummaryJob() {
        GetBrochureSummaryJobQuery query = new GetBrochureSummaryJobQuery(
            WORKSHOP_ID,
            ORG_ID
        );
        Workshop workshop = createWorkshop(ORG_ID);
        WorkshopBrochure brochure = createBrochure();

        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.of(workshop)
        );
        when(brochureRepository.findActiveByWorkshopId(WORKSHOP_ID)).thenReturn(
            Optional.of(brochure)
        );
        when(jobRepository.findLatestByWorkshopId(WORKSHOP_ID)).thenReturn(
            Optional.empty()
        );

        assertThatThrownBy(() -> useCase.getSummaryJob(query))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("summary job");
    }
}
