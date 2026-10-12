package com.unihub.workshop.workshops.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unihub.workshop.shared.events.BrochureSummarizationRequestedEvent;
import com.unihub.workshop.shared.exception.ConflictException;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.workshops.application.ports.in.BrochureUploadResult;
import com.unihub.workshop.workshops.application.ports.in.UploadBrochureCommand;
import com.unihub.workshop.workshops.application.ports.out.AiSummaryJobRepository;
import com.unihub.workshop.workshops.application.ports.out.BrochureStoragePort;
import com.unihub.workshop.workshops.application.ports.out.WorkshopBrochureRepository;
import com.unihub.workshop.workshops.application.ports.out.WorkshopRepository;
import com.unihub.workshop.workshops.domain.model.AiSummaryJob;
import com.unihub.workshop.workshops.domain.model.AiSummaryJobStatus;
import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.domain.model.WorkshopBrochure;
import com.unihub.workshop.workshops.domain.model.WorkshopStatus;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class UploadBrochureUseCaseImplTest {

    @Mock
    private WorkshopRepository workshopRepository;

    @Mock
    private WorkshopBrochureRepository brochureRepository;

    @Mock
    private AiSummaryJobRepository jobRepository;

    @Mock
    private BrochureStoragePort brochureStoragePort;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private UploadBrochureUseCaseImpl useCase;

    private static final UUID WORKSHOP_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID ORGANIZER_ID = UUID.randomUUID();
    private static final byte[] VALID_PDF_BYTES =
        "%PDF-1.4 sample content".getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void setUp() {
        useCase = new UploadBrochureUseCaseImpl(
            workshopRepository,
            brochureRepository,
            jobRepository,
            brochureStoragePort,
            eventPublisher
        );
    }

    private Workshop createWorkshop(WorkshopStatus status, UUID orgId) {
        return Workshop.builder()
            .id(WORKSHOP_ID)
            .organizationId(orgId)
            .title("Title")
            .slug("title")
            .timezone("Asia/Ho_Chi_Minh")
            .status(status)
            .version(1)
            .build();
    }

    private UploadBrochureCommand createValidCommand(byte[] bytes) {
        return new UploadBrochureCommand(
            WORKSHOP_ID,
            ORGANIZER_ID,
            ORG_ID,
            "brochure.pdf",
            "application/pdf",
            bytes.length,
            new ByteArrayInputStream(bytes)
        );
    }

    @Test
    @DisplayName(
        "should accept upload, deactivate previous brochure, save job, and publish event on happy path"
    )
    void shouldUploadBrochureSuccessfully() {
        Workshop workshop = createWorkshop(WorkshopStatus.PUBLISHED, ORG_ID);
        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.of(workshop)
        );
        when(
            jobRepository.existsByWorkshopIdAndStatus(
                WORKSHOP_ID,
                AiSummaryJobStatus.PROCESSING
            )
        ).thenReturn(false);
        when(brochureRepository.save(any(WorkshopBrochure.class))).thenAnswer(
            inv -> inv.getArgument(0)
        );
        when(jobRepository.save(any(AiSummaryJob.class))).thenAnswer(inv ->
            inv.getArgument(0)
        );

        UploadBrochureCommand command = createValidCommand(VALID_PDF_BYTES);
        BrochureUploadResult result = useCase.uploadBrochure(command);

        assertThat(result).isNotNull();
        assertThat(result.jobId()).isNotNull();
        assertThat(result.brochureId()).isNotNull();
        assertThat(result.status()).isEqualTo("PROCESSING");
        assertThat(result.createdAt()).isNotNull();

        verify(brochureRepository).deactivateAllByWorkshopId(WORKSHOP_ID);
        verify(brochureStoragePort).uploadBrochure(
            any(),
            any(),
            eq((long) VALID_PDF_BYTES.length),
            eq("application/pdf")
        );

        ArgumentCaptor<BrochureSummarizationRequestedEvent> eventCaptor =
            ArgumentCaptor.forClass(BrochureSummarizationRequestedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        BrochureSummarizationRequestedEvent published = eventCaptor.getValue();
        assertThat(published.jobId()).isEqualTo(result.jobId());
        assertThat(published.workshopId()).isEqualTo(WORKSHOP_ID);
        assertThat(published.brochureId()).isEqualTo(result.brochureId());
        assertThat(published.organizerId()).isEqualTo(ORGANIZER_ID);
    }

    @Test
    @DisplayName("should throw NotFoundException when workshop does not exist")
    void shouldThrowNotFoundWhenWorkshopDoesNotExist() {
        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.empty()
        );

        UploadBrochureCommand command = createValidCommand(VALID_PDF_BYTES);

        assertThatThrownBy(() -> useCase.uploadBrochure(command)).isInstanceOf(
            NotFoundException.class
        );

        verify(brochureStoragePort, never()).uploadBrochure(
            any(),
            any(),
            anyLong(),
            any()
        );
    }

    @Test
    @DisplayName(
        "should throw ForbiddenException when organization does not match"
    )
    void shouldThrowForbiddenWhenOrgMismatch() {
        UUID otherOrgId = UUID.randomUUID();
        Workshop workshop = createWorkshop(WorkshopStatus.DRAFT, otherOrgId);
        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.of(workshop)
        );

        UploadBrochureCommand command = createValidCommand(VALID_PDF_BYTES);

        assertThatThrownBy(() -> useCase.uploadBrochure(command)).isInstanceOf(
            ForbiddenException.class
        );

        verify(brochureStoragePort, never()).uploadBrochure(
            any(),
            any(),
            anyLong(),
            any()
        );
    }

    @Test
    @DisplayName(
        "should throw ValidationException when workshop is in COMPLETED or CANCELED status"
    )
    void shouldThrowValidationWhenWorkshopCompletedOrCanceled() {
        Workshop completedWorkshop = createWorkshop(
            WorkshopStatus.COMPLETED,
            ORG_ID
        );
        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.of(completedWorkshop)
        );

        UploadBrochureCommand command = createValidCommand(VALID_PDF_BYTES);

        assertThatThrownBy(() -> useCase.uploadBrochure(command)).isInstanceOf(
            ValidationException.class
        );

        Workshop canceledWorkshop = createWorkshop(
            WorkshopStatus.CANCELED,
            ORG_ID
        );
        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.of(canceledWorkshop)
        );

        assertThatThrownBy(() -> useCase.uploadBrochure(command)).isInstanceOf(
            ValidationException.class
        );
    }

    @Test
    @DisplayName(
        "should throw ConflictException when an active PROCESSING job already exists"
    )
    void shouldThrowConflictWhenProcessingJobExists() {
        Workshop workshop = createWorkshop(WorkshopStatus.DRAFT, ORG_ID);
        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.of(workshop)
        );
        when(
            jobRepository.existsByWorkshopIdAndStatus(
                WORKSHOP_ID,
                AiSummaryJobStatus.PROCESSING
            )
        ).thenReturn(true);

        UploadBrochureCommand command = createValidCommand(VALID_PDF_BYTES);

        assertThatThrownBy(() -> useCase.uploadBrochure(command)).isInstanceOf(
            ConflictException.class
        );
    }

    @Test
    @DisplayName(
        "should throw ValidationException when MIME type is not application/pdf"
    )
    void shouldThrowValidationWhenInvalidMimeType() {
        Workshop workshop = createWorkshop(WorkshopStatus.DRAFT, ORG_ID);
        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.of(workshop)
        );
        when(
            jobRepository.existsByWorkshopIdAndStatus(
                WORKSHOP_ID,
                AiSummaryJobStatus.PROCESSING
            )
        ).thenReturn(false);

        UploadBrochureCommand command = new UploadBrochureCommand(
            WORKSHOP_ID,
            ORGANIZER_ID,
            ORG_ID,
            "brochure.png",
            "image/png",
            VALID_PDF_BYTES.length,
            new ByteArrayInputStream(VALID_PDF_BYTES)
        );

        assertThatThrownBy(() -> useCase.uploadBrochure(command)).isInstanceOf(
            ValidationException.class
        );
    }

    @Test
    @DisplayName(
        "should throw ValidationException when magic bytes do not begin with %PDF-"
    )
    void shouldThrowValidationWhenInvalidMagicBytes() {
        Workshop workshop = createWorkshop(WorkshopStatus.DRAFT, ORG_ID);
        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.of(workshop)
        );
        when(
            jobRepository.existsByWorkshopIdAndStatus(
                WORKSHOP_ID,
                AiSummaryJobStatus.PROCESSING
            )
        ).thenReturn(false);

        byte[] fakePdfBytes = "FAKE-CONTENT-NOT-A-PDF".getBytes(
            StandardCharsets.UTF_8
        );
        UploadBrochureCommand command = createValidCommand(fakePdfBytes);

        assertThatThrownBy(() -> useCase.uploadBrochure(command)).isInstanceOf(
            ValidationException.class
        );
    }

    @Test
    @DisplayName(
        "should throw ValidationException when file size exceeds 10 MB"
    )
    void shouldThrowValidationWhenFileSizeExceeds10Mb() {
        Workshop workshop = createWorkshop(WorkshopStatus.DRAFT, ORG_ID);
        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.of(workshop)
        );
        when(
            jobRepository.existsByWorkshopIdAndStatus(
                WORKSHOP_ID,
                AiSummaryJobStatus.PROCESSING
            )
        ).thenReturn(false);

        long oversize = 10L * 1024 * 1024 + 1;
        UploadBrochureCommand command = new UploadBrochureCommand(
            WORKSHOP_ID,
            ORGANIZER_ID,
            ORG_ID,
            "brochure.pdf",
            "application/pdf",
            oversize,
            new ByteArrayInputStream(VALID_PDF_BYTES)
        );

        assertThatThrownBy(() -> useCase.uploadBrochure(command)).isInstanceOf(
            ValidationException.class
        );
    }
}
