package com.unihub.workshop.workshops.application.usecases;

import com.unihub.workshop.shared.events.BrochureSummarizationRequestedEvent;
import com.unihub.workshop.shared.exception.ConflictException;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.InternalServerException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.workshops.application.ports.in.BrochureUploadResult;
import com.unihub.workshop.workshops.application.ports.in.UploadBrochureCommand;
import com.unihub.workshop.workshops.application.ports.in.UploadBrochureUseCase;
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
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UploadBrochureUseCaseImpl implements UploadBrochureUseCase {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024; // 10 MB
    private static final String PDF_MIME_TYPE = "application/pdf";
    private static final byte[] PDF_MAGIC_BYTES = new byte[] {
        0x25,
        0x50,
        0x44,
        0x46,
        0x2D,
    }; // %PDF-

    private final WorkshopRepository workshopRepository;
    private final WorkshopBrochureRepository brochureRepository;
    private final AiSummaryJobRepository jobRepository;
    private final BrochureStoragePort brochureStoragePort;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public BrochureUploadResult uploadBrochure(UploadBrochureCommand command) {
        Workshop workshop = workshopRepository
            .findById(command.workshopId())
            .orElseThrow(() ->
                new NotFoundException(
                    "Workshop not found with id: " + command.workshopId()
                )
            );

        if (!workshop.organizationId().equals(command.organizationId())) {
            throw new ForbiddenException(
                "You do not have permission to upload brochures for this workshop."
            );
        }

        if (
            workshop.status() == WorkshopStatus.COMPLETED ||
            workshop.status() == WorkshopStatus.CANCELED
        ) {
            throw new ValidationException(
                "status",
                "Workshop must be in DRAFT or PUBLISHED status to upload a brochure. Current status: " +
                    workshop.status()
            );
        }

        if (
            jobRepository.existsByWorkshopIdAndStatus(
                command.workshopId(),
                AiSummaryJobStatus.PROCESSING
            )
        ) {
            throw new ConflictException(
                "An AI summary job is currently in PROCESSING status for this workshop."
            );
        }

        if (
            command.contentType() == null ||
            !PDF_MIME_TYPE.equalsIgnoreCase(command.contentType().trim())
        ) {
            throw new ValidationException(
                "file",
                "File must have declared MIME type application/pdf"
            );
        }

        if (command.byteSize() > MAX_FILE_SIZE) {
            throw new ValidationException(
                "file",
                "File size exceeds maximum allowed 10 MB"
            );
        }

        byte[] fileBytes;
        try {
            fileBytes = command.inputStream().readAllBytes();
        } catch (IOException e) {
            throw new InternalServerException(
                "Failed to read brochure file stream",
                e
            );
        }

        if (fileBytes.length == 0) {
            throw new ValidationException("file", "Uploaded file is empty");
        }

        if (fileBytes.length > MAX_FILE_SIZE) {
            throw new ValidationException(
                "file",
                "File size exceeds maximum allowed 10 MB"
            );
        }

        if (!hasPdfMagicBytes(fileBytes)) {
            throw new ValidationException(
                "file",
                "File does not contain valid PDF header magic bytes %PDF-"
            );
        }

        String checksumSha256 = calculateSha256(fileBytes);
        UUID brochureId = UUID.randomUUID();
        String storageKey =
            "workshops/" +
            command.workshopId() +
            "/brochures/" +
            brochureId +
            ".pdf";

        // Upload PDF to MinIO bucket unihub-brochures
        // Note: If the following DB transaction fails, this MinIO object may become an orphan.
        // As per SPEC.md §8 Out of Scope, automated MinIO cleanup on DB failure is handled via lifecycle policies.
        brochureStoragePort.uploadBrochure(
            storageKey,
            new ByteArrayInputStream(fileBytes),
            fileBytes.length,
            PDF_MIME_TYPE
        );

        // Deactivate previous active brochure(s) for this workshop
        brochureRepository.deactivateAllByWorkshopId(command.workshopId());

        Instant now = Instant.now();
        WorkshopBrochure brochure = WorkshopBrochure.builder()
            .id(brochureId)
            .workshopId(command.workshopId())
            .storageKey(storageKey)
            .fileName(command.fileName())
            .contentType(PDF_MIME_TYPE)
            .byteSize(fileBytes.length)
            .checksumSha256(checksumSha256)
            .isActive(true)
            .uploadedAt(now)
            .createdAt(now)
            .updatedAt(now)
            .createdBy(command.organizerId())
            .updatedBy(command.organizerId())
            .build();
        brochureRepository.save(brochure);

        AiSummaryJob job = AiSummaryJob.createProcessing(
            command.workshopId(),
            brochureId,
            command.organizerId()
        );
        AiSummaryJob savedJob = jobRepository.save(job);

        // Publish event to transactional outbox (@Externalized("unihub.events::ai.summarization"))
        eventPublisher.publishEvent(
            new BrochureSummarizationRequestedEvent(
                savedJob.id(),
                command.workshopId(),
                brochureId,
                storageKey,
                command.organizerId()
            )
        );

        log.info(
            "Brochure uploaded and AI summary job created: workshopId={}, brochureId={}, jobId={}",
            command.workshopId(),
            brochureId,
            savedJob.id()
        );

        return new BrochureUploadResult(
            savedJob.id(),
            brochureId,
            savedJob.status().name(),
            savedJob.createdAt()
        );
    }

    private boolean hasPdfMagicBytes(byte[] bytes) {
        if (bytes.length < PDF_MAGIC_BYTES.length) {
            return false;
        }
        for (int i = 0; i < PDF_MAGIC_BYTES.length; i++) {
            if (bytes[i] != PDF_MAGIC_BYTES[i]) {
                return false;
            }
        }
        return true;
    }

    private String calculateSha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new InternalServerException(
                "SHA-256 algorithm not available",
                e
            );
        }
    }
}
