package com.unihub.workshop.workers.ai_processing.core.usecases;

import com.unihub.workshop.workers.ai_processing.core.exceptions.AiSummarizationRetryExhaustedException;
import com.unihub.workshop.workers.ai_processing.core.exceptions.AiSummarizationTransientException;
import com.unihub.workshop.workers.ai_processing.core.models.AiSummaryJobModel;
import com.unihub.workshop.workers.ai_processing.core.models.AiSummaryJobStatusModel;
import com.unihub.workshop.workers.ai_processing.core.models.BrochureSummarizationRequest;
import com.unihub.workshop.workers.ai_processing.core.ports.in.ProcessBrochureSummarizationPort;
import com.unihub.workshop.workers.ai_processing.core.ports.out.AiSummarizationPort;
import com.unihub.workshop.workers.ai_processing.core.ports.out.AiSummaryJobRepositoryPort;
import com.unihub.workshop.workers.ai_processing.core.ports.out.BrochureStoragePort;
import com.unihub.workshop.workers.ai_processing.core.ports.out.RealtimePublisherPort;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Service
public class ProcessBrochureSummarizationUseCase
    implements ProcessBrochureSummarizationPort
{

    public static final int MAX_CHUNK_CHARS = 50_000;
    public static final String NO_EXTRACTABLE_TEXT_ERROR =
        "No extractable text found in PDF brochure";
    public static final int DEFAULT_MAX_ATTEMPTS = 3;
    public static final long DEFAULT_BACKOFF_INITIAL_SECONDS = 5;
    public static final double DEFAULT_BACKOFF_MULTIPLIER = 2.0;

    private final BrochureStoragePort storagePort;
    private final AiSummarizationPort aiSummarizationPort;
    private final AiSummaryJobRepositoryPort jobRepositoryPort;
    private final RealtimePublisherPort realtimePublisherPort;
    private final int maxAttempts;
    private final long backoffInitialIntervalSeconds;
    private final double backoffMultiplier;

    public ProcessBrochureSummarizationUseCase(
        BrochureStoragePort storagePort,
        AiSummarizationPort aiSummarizationPort,
        AiSummaryJobRepositoryPort jobRepositoryPort,
        RealtimePublisherPort realtimePublisherPort,
        @Value("${app.ai.max-attempts:3}") int maxAttempts,
        @Value(
            "${app.ai.backoff-initial-seconds:5}"
        ) long backoffInitialIntervalSeconds,
        @Value("${app.ai.backoff-multiplier:2.0}") double backoffMultiplier
    ) {
        this.storagePort = storagePort;
        this.aiSummarizationPort = aiSummarizationPort;
        this.jobRepositoryPort = jobRepositoryPort;
        this.realtimePublisherPort = realtimePublisherPort;
        this.maxAttempts = maxAttempts;
        this.backoffInitialIntervalSeconds = backoffInitialIntervalSeconds;
        this.backoffMultiplier = backoffMultiplier;
    }

    public ProcessBrochureSummarizationUseCase(
        BrochureStoragePort storagePort,
        AiSummarizationPort aiSummarizationPort,
        AiSummaryJobRepositoryPort jobRepositoryPort,
        RealtimePublisherPort realtimePublisherPort
    ) {
        this(
            storagePort,
            aiSummarizationPort,
            jobRepositoryPort,
            realtimePublisherPort,
            DEFAULT_MAX_ATTEMPTS,
            DEFAULT_BACKOFF_INITIAL_SECONDS,
            DEFAULT_BACKOFF_MULTIPLIER
        );
    }

    @Override
    public void process(BrochureSummarizationRequest request) {
        log.info(
            "Processing brochure summarization: jobId={}, workshopId={}, storageKey={}",
            request.jobId(),
            request.workshopId(),
            request.storageKey()
        );

        // Idempotency check: skip if job already in terminal state
        Optional<AiSummaryJobModel> jobOpt = jobRepositoryPort.findById(
            request.jobId()
        );
        if (jobOpt.isPresent()) {
            AiSummaryJobModel job = jobOpt.get();
            if (
                job.status() == AiSummaryJobStatusModel.COMPLETED ||
                job.status() == AiSummaryJobStatusModel.FAILED
            ) {
                log.info(
                    "Job {} is already in terminal state {}, skipping processing",
                    request.jobId(),
                    job.status()
                );
                return;
            }
        } else {
            log.warn(
                "Job {} not found in database, terminating message",
                request.jobId()
            );
            return;
        }

        int currentAttempts = jobOpt.get().attempts();

        try {
            // Fetch PDF from MinIO
            byte[] pdfBytes = storagePort.fetchBrochurePdf(
                request.storageKey()
            );

            // Extract text locally via PDFBox
            String extractedText = extractText(pdfBytes, request);
            if (extractedText == null || extractedText.trim().isEmpty()) {
                handleNoExtractableText(request);
                return;
            }

            String cleanedText = cleanText(extractedText);
            if (cleanedText.isEmpty()) {
                handleNoExtractableText(request);
                return;
            }

            // Summarize text
            String summaryResult;
            if (cleanedText.length() <= MAX_CHUNK_CHARS) {
                summaryResult = aiSummarizationPort.summarizeText(cleanedText);
            } else {
                summaryResult = summarizeChunkedText(cleanedText);
            }

            // Persist completed result
            Instant completedAt = Instant.now();
            jobRepositoryPort.updateCompleted(
                request.jobId(),
                summaryResult,
                completedAt
            );

            // Push real-time event to Redis Pub/Sub
            realtimePublisherPort.publishSummaryCompleted(
                request.organizerId(),
                request.jobId(),
                request.workshopId(),
                summaryResult
            );

            log.info(
                "Successfully summarized brochure: jobId={}, workshopId={}",
                request.jobId(),
                request.workshopId()
            );
        } catch (
            AiSummarizationTransientException
            | AiSummarizationRetryExhaustedException e
        ) {
            throw e;
        } catch (Exception e) {
            handleProcessingError(request, currentAttempts, e);
        }
    }

    private void handleProcessingError(
        BrochureSummarizationRequest request,
        int currentAttempts,
        Exception e
    ) {
        int newAttempts = currentAttempts + 1;
        String errorMessage =
            e.getMessage() != null && !e.getMessage().isBlank()
                ? e.getMessage()
                : e.getClass().getSimpleName();

        if (isTransientError(e) && newAttempts < maxAttempts) {
            Instant nextRetryAt = calculateNextRetryAt(newAttempts);
            log.warn(
                "Transient error processing brochure summarization for jobId {} (attempt {} of {}): {}. Next retry at {}",
                request.jobId(),
                newAttempts,
                maxAttempts,
                errorMessage,
                nextRetryAt
            );
            jobRepositoryPort.updateRetry(
                request.jobId(),
                newAttempts,
                nextRetryAt
            );
            throw new AiSummarizationTransientException(
                "Transient error on attempt " +
                    newAttempts +
                    ": " +
                    errorMessage,
                e
            );
        } else {
            // Max retries exhausted or non-transient unrecoverable failure
            log.error(
                "Terminating brochure summarization for jobId {} with failure (attempts={}/{}): {}",
                request.jobId(),
                newAttempts,
                maxAttempts,
                errorMessage
            );
            Instant now = Instant.now();
            jobRepositoryPort.updateFailed(
                request.jobId(),
                newAttempts,
                errorMessage,
                now
            );
            realtimePublisherPort.publishSummaryFailed(
                request.organizerId(),
                request.jobId(),
                request.workshopId(),
                errorMessage
            );
            throw new AiSummarizationRetryExhaustedException(
                "Summarization failed for jobId " +
                    request.jobId() +
                    ": " +
                    errorMessage,
                e
            );
        }
    }

    private boolean isTransientError(Throwable throwable) {
        if (throwable == null) {
            return false;
        }
        if (throwable instanceof AiSummarizationTransientException) {
            return true;
        }
        if (throwable instanceof CallNotPermittedException) {
            return true;
        }
        if (throwable instanceof ResourceAccessException) {
            return true;
        }
        if (throwable instanceof RestClientResponseException resEx) {
            int status = resEx.getStatusCode().value();
            return status == 429 || (status >= 500 && status < 600);
        }
        String msg =
            throwable.getMessage() != null
                ? throwable.getMessage().toLowerCase()
                : "";
        if (
            msg.contains("429") ||
            msg.contains("too many requests") ||
            msg.contains("timeout") ||
            msg.contains("500") ||
            msg.contains("502") ||
            msg.contains("503") ||
            msg.contains("504") ||
            msg.contains("rate limit") ||
            msg.contains("connection refused") ||
            msg.contains("temporarily unavailable")
        ) {
            return true;
        }
        return isTransientError(throwable.getCause());
    }

    private Instant calculateNextRetryAt(int attempt) {
        long delaySeconds = (long) (backoffInitialIntervalSeconds *
            Math.pow(backoffMultiplier, Math.max(0, attempt - 1)));
        return Instant.now().plusSeconds(delaySeconds);
    }

    private String extractText(
        byte[] pdfBytes,
        BrochureSummarizationRequest request
    ) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            return null;
        }
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        } catch (IOException | RuntimeException e) {
            log.warn(
                "Unable to extract text layer from PDF for jobId {}: {}",
                request.jobId(),
                e.getMessage()
            );
            return null;
        }
    }

    private void handleNoExtractableText(BrochureSummarizationRequest request) {
        log.info(
            "No extractable text found in brochure for jobId {}. Fast-failing without AI invocation.",
            request.jobId()
        );
        Instant now = Instant.now();
        jobRepositoryPort.updateFailed(
            request.jobId(),
            NO_EXTRACTABLE_TEXT_ERROR,
            now
        );
        realtimePublisherPort.publishSummaryFailed(
            request.organizerId(),
            request.jobId(),
            request.workshopId(),
            NO_EXTRACTABLE_TEXT_ERROR
        );
    }

    private String cleanText(String text) {
        if (text == null) {
            return "";
        }
        // Normalize line endings and collapse excessive whitespace
        return text
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replaceAll("\n{3,}", "\n\n")
            .trim();
    }

    private String summarizeChunkedText(String fullText) {
        List<String> chunks = splitIntoChunks(fullText, MAX_CHUNK_CHARS);
        log.info(
            "Splitting text into {} chunks for summarization",
            chunks.size()
        );

        List<String> chunkSummaries = new ArrayList<>();
        for (String chunk : chunks) {
            String chunkSummary = aiSummarizationPort.summarizeText(chunk);
            if (chunkSummary != null && !chunkSummary.isBlank()) {
                chunkSummaries.add(chunkSummary.trim());
            }
        }

        if (chunkSummaries.isEmpty()) {
            return "";
        }
        if (chunkSummaries.size() == 1) {
            return chunkSummaries.getFirst();
        }

        String combined = String.join("\n\n---\n\n", chunkSummaries);
        if (combined.length() <= MAX_CHUNK_CHARS) {
            String consolidationPrompt =
                "Consolidate the following section summaries into a single cohesive Markdown summary " +
                "(covering Objectives, Target Audience, Key Highlights/Agenda, Prerequisites/Speaker info):\n\n" +
                combined;
            return aiSummarizationPort.summarizeText(consolidationPrompt);
        }

        return combined;
    }

    private List<String> splitIntoChunks(String text, int maxChunkSize) {
        List<String> chunks = new ArrayList<>();
        int length = text.length();
        int start = 0;

        while (start < length) {
            int end = Math.min(start + maxChunkSize, length);
            if (end < length) {
                // Try to find paragraph break or newline near the boundary
                int lastNewline = text.lastIndexOf('\n', end);
                if (lastNewline > start + maxChunkSize / 2) {
                    end = lastNewline + 1;
                } else {
                    int lastSpace = text.lastIndexOf(' ', end);
                    if (lastSpace > start + maxChunkSize / 2) {
                        end = lastSpace + 1;
                    }
                }
            }
            chunks.add(text.substring(start, end).trim());
            start = end;
        }

        return chunks;
    }
}
