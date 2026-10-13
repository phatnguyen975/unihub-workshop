package com.unihub.workshop.workers.ai_processing.core.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.unihub.workshop.workers.ai_processing.core.exceptions.AiSummarizationRetryExhaustedException;
import com.unihub.workshop.workers.ai_processing.core.exceptions.AiSummarizationTransientException;
import com.unihub.workshop.workers.ai_processing.core.models.AiSummaryJobModel;
import com.unihub.workshop.workers.ai_processing.core.models.AiSummaryJobStatusModel;
import com.unihub.workshop.workers.ai_processing.core.models.BrochureSummarizationRequest;
import com.unihub.workshop.workers.ai_processing.core.ports.out.AiSummarizationMetricsPort;
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
    private AiSummarizationMetricsPort metricsPort;

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
        metricsPort = mock(AiSummarizationMetricsPort.class);

        useCase = new ProcessBrochureSummarizationUseCase(
            storagePort,
            aiSummarizationPort,
            jobRepositoryPort,
            realtimePublisherPort,
            metricsPort,
            ProcessBrochureSummarizationUseCase.DEFAULT_MAX_ATTEMPTS,
            ProcessBrochureSummarizationUseCase.DEFAULT_BACKOFF_INITIAL_SECONDS,
            ProcessBrochureSummarizationUseCase.DEFAULT_BACKOFF_MULTIPLIER
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
        return createJobModel(status, 0);
    }

    private AiSummaryJobModel createJobModel(
        AiSummaryJobStatusModel status,
        int attempts
    ) {
        return new AiSummaryJobModel(
            JOB_ID,
            WORKSHOP_ID,
            BROCHURE_ID,
            status,
            null,
            null,
            attempts,
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
        verify(metricsPort).recordJobStarted();
        verify(metricsPort).recordJobCompleted();
        verify(metricsPort, never()).recordJobFailed();
        verify(metricsPort, never()).recordJobRetried();
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
        verify(metricsPort).recordJobStarted();
        verify(metricsPort).recordJobFailed();
        verify(metricsPort, never()).recordJobCompleted();
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
        verify(metricsPort).recordJobStarted();
        verify(metricsPort).recordJobFailed();
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
        verifyNoInteractions(metricsPort);
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
        verifyNoInteractions(metricsPort);
    }

    @Test
    @DisplayName(
        "Should increment attempts and set next_retry_at when transient error occurs on attempt 1"
    )
    void shouldIncrementAttemptsAndSetNextRetryAtWhenTransientErrorOnAttempt1()
        throws Exception {
        BrochureSummarizationRequest request = new BrochureSummarizationRequest(
            JOB_ID,
            WORKSHOP_ID,
            BROCHURE_ID,
            STORAGE_KEY,
            ORGANIZER_ID
        );
        byte[] pdfBytes = createPdfWithText("Welcome to the Docker workshop.");

        when(jobRepositoryPort.findById(JOB_ID)).thenReturn(
            Optional.of(createJobModel(AiSummaryJobStatusModel.PROCESSING, 0))
        );
        when(storagePort.fetchBrochurePdf(STORAGE_KEY)).thenReturn(pdfBytes);
        when(aiSummarizationPort.summarizeText(anyString())).thenThrow(
            new RuntimeException("HTTP 429 Too Many Requests")
        );

        AiSummarizationTransientException thrown = assertThrows(
            AiSummarizationTransientException.class,
            () -> useCase.process(request)
        );

        assertThat(thrown.getMessage()).contains("429");
        verify(jobRepositoryPort).updateRetry(
            eq(JOB_ID),
            eq(1),
            any(Instant.class)
        );
        verify(jobRepositoryPort, never()).updateCompleted(any(), any(), any());
        verify(jobRepositoryPort, never()).updateFailed(any(), any(), any());
        verify(jobRepositoryPort, never()).updateFailed(
            any(),
            any(Integer.class),
            any(),
            any()
        );
        verifyNoInteractions(realtimePublisherPort);
        verify(metricsPort).recordJobStarted();
        verify(metricsPort).recordJobRetried();
    }

    @Test
    @DisplayName(
        "Should increment attempts to 2 and set next_retry_at when transient error occurs on attempt 2"
    )
    void shouldIncrementAttemptsAndSetNextRetryAtWhenTransientErrorOnAttempt2()
        throws Exception {
        BrochureSummarizationRequest request = new BrochureSummarizationRequest(
            JOB_ID,
            WORKSHOP_ID,
            BROCHURE_ID,
            STORAGE_KEY,
            ORGANIZER_ID
        );
        byte[] pdfBytes = createPdfWithText("Welcome to the Docker workshop.");

        when(jobRepositoryPort.findById(JOB_ID)).thenReturn(
            Optional.of(createJobModel(AiSummaryJobStatusModel.PROCESSING, 1))
        );
        when(storagePort.fetchBrochurePdf(STORAGE_KEY)).thenReturn(pdfBytes);
        when(aiSummarizationPort.summarizeText(anyString())).thenThrow(
            new RuntimeException("Connection timeout")
        );

        assertThrows(AiSummarizationTransientException.class, () ->
            useCase.process(request)
        );

        verify(jobRepositoryPort).updateRetry(
            eq(JOB_ID),
            eq(2),
            any(Instant.class)
        );
        verify(jobRepositoryPort, never()).updateCompleted(any(), any(), any());
        verify(jobRepositoryPort, never()).updateFailed(any(), any(), any());
        verify(jobRepositoryPort, never()).updateFailed(
            any(),
            any(Integer.class),
            any(),
            any()
        );
        verifyNoInteractions(realtimePublisherPort);
        verify(metricsPort).recordJobStarted();
        verify(metricsPort).recordJobRetried();
    }

    @Test
    @DisplayName(
        "Should transition to FAILED, record error, publish realtime event and throw retry exhausted when attempt 3 fails"
    )
    void shouldMarkFailedAndPublishEventWhenMaxAttemptsExhaustedOnAttempt3()
        throws Exception {
        BrochureSummarizationRequest request = new BrochureSummarizationRequest(
            JOB_ID,
            WORKSHOP_ID,
            BROCHURE_ID,
            STORAGE_KEY,
            ORGANIZER_ID
        );
        byte[] pdfBytes = createPdfWithText("Welcome to the Docker workshop.");

        when(jobRepositoryPort.findById(JOB_ID)).thenReturn(
            Optional.of(createJobModel(AiSummaryJobStatusModel.PROCESSING, 2))
        );
        when(storagePort.fetchBrochurePdf(STORAGE_KEY)).thenReturn(pdfBytes);
        when(aiSummarizationPort.summarizeText(anyString())).thenThrow(
            new RuntimeException("503 Service Unavailable")
        );

        AiSummarizationRetryExhaustedException thrown = assertThrows(
            AiSummarizationRetryExhaustedException.class,
            () -> useCase.process(request)
        );

        assertThat(thrown.getMessage()).contains("503");
        verify(jobRepositoryPort).updateFailed(
            eq(JOB_ID),
            eq(3),
            eq("503 Service Unavailable"),
            any(Instant.class)
        );
        verify(realtimePublisherPort).publishSummaryFailed(
            eq(ORGANIZER_ID),
            eq(JOB_ID),
            eq(WORKSHOP_ID),
            eq("503 Service Unavailable")
        );
        verify(jobRepositoryPort, never()).updateRetry(
            any(),
            any(Integer.class),
            any()
        );
        verify(metricsPort).recordJobStarted();
        verify(metricsPort).recordJobFailed();
    }

    @Test
    @DisplayName(
        "Should mark FAILED immediately and throw retry exhausted when non-transient error occurs"
    )
    void shouldMarkFailedAndPublishEventWhenNonTransientErrorOccurs()
        throws Exception {
        BrochureSummarizationRequest request = new BrochureSummarizationRequest(
            JOB_ID,
            WORKSHOP_ID,
            BROCHURE_ID,
            STORAGE_KEY,
            ORGANIZER_ID
        );
        byte[] pdfBytes = createPdfWithText("Welcome to the Docker workshop.");

        when(jobRepositoryPort.findById(JOB_ID)).thenReturn(
            Optional.of(createJobModel(AiSummaryJobStatusModel.PROCESSING, 0))
        );
        when(storagePort.fetchBrochurePdf(STORAGE_KEY)).thenReturn(pdfBytes);
        when(aiSummarizationPort.summarizeText(anyString())).thenThrow(
            new IllegalArgumentException("Invalid argument format")
        );

        assertThrows(AiSummarizationRetryExhaustedException.class, () ->
            useCase.process(request)
        );

        verify(jobRepositoryPort).updateFailed(
            eq(JOB_ID),
            eq(1),
            eq("Invalid argument format"),
            any(Instant.class)
        );
        verify(realtimePublisherPort).publishSummaryFailed(
            eq(ORGANIZER_ID),
            eq(JOB_ID),
            eq(WORKSHOP_ID),
            eq("Invalid argument format")
        );
        verify(metricsPort).recordJobStarted();
        verify(metricsPort).recordJobFailed();
    }
}
