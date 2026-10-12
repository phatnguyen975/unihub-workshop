package com.unihub.workshop.workers.ai_processing.core.usecases;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.unihub.workshop.workers.ai_processing.core.models.AiSummaryJobModel;
import com.unihub.workshop.workers.ai_processing.core.models.AiSummaryJobStatusModel;
import com.unihub.workshop.workers.ai_processing.core.models.BrochureSummarizationRequest;
import com.unihub.workshop.workers.ai_processing.core.ports.out.AiSummarizationPort;
import com.unihub.workshop.workers.ai_processing.core.ports.out.AiSummaryJobRepositoryPort;
import com.unihub.workshop.workers.ai_processing.core.ports.out.BrochureStoragePort;
import com.unihub.workshop.workers.ai_processing.core.ports.out.RealtimePublisherPort;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProcessBrochureSummarizationUseCaseTest {

    private BrochureStoragePort storagePort;
    private AiSummarizationPort aiSummarizationPort;
    private AiSummaryJobRepositoryPort jobRepositoryPort;
    private RealtimePublisherPort realtimePublisherPort;

    private ProcessBrochureSummarizationUseCase useCase;

    private static final UUID JOB_ID = UUID.randomUUID();
    private static final UUID WORKSHOP_ID = UUID.randomUUID();
    private static final UUID BROCHURE_ID = UUID.randomUUID();
    private static final UUID ORGANIZER_ID = UUID.randomUUID();
    private static final String STORAGE_KEY =
        "workshops/ws-1/brochures/br-1.pdf";

    @BeforeEach
    void setUp() {
        storagePort = mock(BrochureStoragePort.class);
        aiSummarizationPort = mock(AiSummarizationPort.class);
        jobRepositoryPort = mock(AiSummaryJobRepositoryPort.class);
        realtimePublisherPort = mock(RealtimePublisherPort.class);

        useCase = new ProcessBrochureSummarizationUseCase(
            storagePort,
            aiSummarizationPort,
            jobRepositoryPort,
            realtimePublisherPort
        );
    }

    private byte[] createPdfWithText(String text) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(
                    new PDType1Font(Standard14Fonts.FontName.HELVETICA),
                    12
                );
                cs.newLineAtOffset(100, 700);
                cs.showText(text);
                cs.endText();
            }
            doc.save(baos);
        }
        return baos.toByteArray();
    }

    private byte[] createEmptyPdf() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            doc.save(baos);
        }
        return baos.toByteArray();
    }

    private AiSummaryJobModel createJobModel(AiSummaryJobStatusModel status) {
        return new AiSummaryJobModel(
            JOB_ID,
            WORKSHOP_ID,
            BROCHURE_ID,
            status,
            null,
            null,
            0,
            null,
            null,
            Instant.now(),
            Instant.now(),
            ORGANIZER_ID,
            ORGANIZER_ID
        );
    }

    @Test
    @DisplayName(
        "Should process PDF ≤ 50,000 chars, call AI once, update COMPLETED and publish realtime event"
    )
    void shouldProcessPdfWithinLimitSuccessfully() throws Exception {
        BrochureSummarizationRequest request = new BrochureSummarizationRequest(
            JOB_ID,
            WORKSHOP_ID,
            BROCHURE_ID,
            STORAGE_KEY,
            ORGANIZER_ID
        );
        byte[] pdfBytes = createPdfWithText(
            "Welcome to the Docker and Kubernetes workshop."
        );
        String expectedSummary =
            "## Workshop Summary\n\n**Objectives:** Learn Docker";

        when(jobRepositoryPort.findById(JOB_ID)).thenReturn(
            Optional.of(createJobModel(AiSummaryJobStatusModel.PROCESSING))
        );
        when(storagePort.fetchBrochurePdf(STORAGE_KEY)).thenReturn(pdfBytes);
        when(aiSummarizationPort.summarizeText(anyString())).thenReturn(
            expectedSummary
        );

        useCase.process(request);

        verify(storagePort).fetchBrochurePdf(STORAGE_KEY);
        verify(aiSummarizationPort).summarizeText(anyString());
        verify(jobRepositoryPort).updateCompleted(
            eq(JOB_ID),
            eq(expectedSummary),
            any(Instant.class)
        );
        verify(realtimePublisherPort).publishSummaryCompleted(
            eq(ORGANIZER_ID),
            eq(JOB_ID),
            eq(WORKSHOP_ID),
            eq(expectedSummary)
        );
    }

    @Test
    @DisplayName(
        "Should fast-fail image-only PDF with no extractable text, mark FAILED and publish failed realtime event"
    )
    void shouldFastFailWhenPdfHasNoExtractableText() throws Exception {
        BrochureSummarizationRequest request = new BrochureSummarizationRequest(
            JOB_ID,
            WORKSHOP_ID,
            BROCHURE_ID,
            STORAGE_KEY,
            ORGANIZER_ID
        );
        byte[] emptyPdf = createEmptyPdf();

        when(jobRepositoryPort.findById(JOB_ID)).thenReturn(
            Optional.of(createJobModel(AiSummaryJobStatusModel.PROCESSING))
        );
        when(storagePort.fetchBrochurePdf(STORAGE_KEY)).thenReturn(emptyPdf);

        useCase.process(request);

        verify(storagePort).fetchBrochurePdf(STORAGE_KEY);
        verifyNoInteractions(aiSummarizationPort);
        verify(jobRepositoryPort).updateFailed(
            eq(JOB_ID),
            eq("No extractable text found in PDF brochure"),
            any(Instant.class)
        );
        verify(realtimePublisherPort).publishSummaryFailed(
            eq(ORGANIZER_ID),
            eq(JOB_ID),
            eq(WORKSHOP_ID),
            eq("No extractable text found in PDF brochure")
        );
    }

    @Test
    @DisplayName(
        "Should treat malformed PDF / PDFBox exception as fast-fail with no extractable text"
    )
    void shouldFastFailWhenPdfIsMalformed() {
        BrochureSummarizationRequest request = new BrochureSummarizationRequest(
            JOB_ID,
            WORKSHOP_ID,
            BROCHURE_ID,
            STORAGE_KEY,
            ORGANIZER_ID
        );
        byte[] malformedBytes = new byte[] { 1, 2, 3, 4, 5 };

        when(jobRepositoryPort.findById(JOB_ID)).thenReturn(
            Optional.of(createJobModel(AiSummaryJobStatusModel.PROCESSING))
        );
        when(storagePort.fetchBrochurePdf(STORAGE_KEY)).thenReturn(
            malformedBytes
        );

        useCase.process(request);

        verify(storagePort).fetchBrochurePdf(STORAGE_KEY);
        verifyNoInteractions(aiSummarizationPort);
        verify(jobRepositoryPort).updateFailed(
            eq(JOB_ID),
            eq("No extractable text found in PDF brochure"),
            any(Instant.class)
        );
        verify(realtimePublisherPort).publishSummaryFailed(
            eq(ORGANIZER_ID),
            eq(JOB_ID),
            eq(WORKSHOP_ID),
            eq("No extractable text found in PDF brochure")
        );
    }

    @Test
    @DisplayName(
        "Should skip processing when job is already COMPLETED (idempotency guard)"
    )
    void shouldSkipWhenJobAlreadyCompleted() {
        BrochureSummarizationRequest request = new BrochureSummarizationRequest(
            JOB_ID,
            WORKSHOP_ID,
            BROCHURE_ID,
            STORAGE_KEY,
            ORGANIZER_ID
        );

        when(jobRepositoryPort.findById(JOB_ID)).thenReturn(
            Optional.of(createJobModel(AiSummaryJobStatusModel.COMPLETED))
        );

        useCase.process(request);

        verifyNoInteractions(storagePort);
        verifyNoInteractions(aiSummarizationPort);
        verify(jobRepositoryPort, never()).updateCompleted(any(), any(), any());
        verify(jobRepositoryPort, never()).updateFailed(any(), any(), any());
        verifyNoInteractions(realtimePublisherPort);
    }

    @Test
    @DisplayName(
        "Should skip processing when job is already FAILED (idempotency guard)"
    )
    void shouldSkipWhenJobAlreadyFailed() {
        BrochureSummarizationRequest request = new BrochureSummarizationRequest(
            JOB_ID,
            WORKSHOP_ID,
            BROCHURE_ID,
            STORAGE_KEY,
            ORGANIZER_ID
        );

        when(jobRepositoryPort.findById(JOB_ID)).thenReturn(
            Optional.of(createJobModel(AiSummaryJobStatusModel.FAILED))
        );

        useCase.process(request);

        verifyNoInteractions(storagePort);
        verifyNoInteractions(aiSummarizationPort);
        verify(jobRepositoryPort, never()).updateCompleted(any(), any(), any());
        verify(jobRepositoryPort, never()).updateFailed(any(), any(), any());
        verifyNoInteractions(realtimePublisherPort);
    }
}
