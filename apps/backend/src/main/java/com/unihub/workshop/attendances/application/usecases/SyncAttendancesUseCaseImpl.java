package com.unihub.workshop.attendances.application.usecases;

import com.unihub.workshop.attendances.application.policy.AttendanceReconciliationPolicy;
import com.unihub.workshop.attendances.application.ports.in.SyncAttendancesCommand;
import com.unihub.workshop.attendances.application.ports.in.SyncAttendancesUseCase;
import com.unihub.workshop.attendances.application.ports.out.AttendanceRepository;
import com.unihub.workshop.attendances.domain.model.Attendance;
import com.unihub.workshop.attendances.domain.model.AttendanceOutcome;
import com.unihub.workshop.attendances.domain.model.SyncEntry;
import com.unihub.workshop.registrations.api.RegistrationApi;
import com.unihub.workshop.registrations.api.dto.RegistrationTicketContextDto;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.shared.idempotency.ClaimResult;
import com.unihub.workshop.shared.idempotency.IdempotencyPort;
import com.unihub.workshop.workshops.api.WorkshopApi;
import com.unihub.workshop.workshops.api.dto.WorkshopRegistrationDetailsDto;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrator implementing {@link SyncAttendancesUseCase}.
 *
 * <p>The use case is deliberately non-read-only transactional: every read and write of
 * this check-then-act mutation must be routed to the primary database so a
 * just-committed check-in can never be missed.
 *
 * <p>The request-level idempotency claim is taken before the first business read, so a
 * replayed batch returns the stored outcomes without touching the workshop,
 * registration or attendance tables at all.
 */
@Service
@RequiredArgsConstructor
public class SyncAttendancesUseCaseImpl implements SyncAttendancesUseCase {

    private static final String PUBLISHED_STATUS = "PUBLISHED";
    private static final String COMPLETED_STATUS = "COMPLETED";
    private static final Set<String> SYNC_ELIGIBLE_STATUSES = Set.of(
        PUBLISHED_STATUS,
        COMPLETED_STATUS
    );
    private static final String REQUEST_METHOD = "POST";
    private static final String SYNC_PATH_TEMPLATE =
        "/api/v1/workshops/{workshopId}/attendances/sync";
    private static final String WORKSHOP_ID_PLACEHOLDER = "{workshopId}";
    private static final String SHA_256_ALGORITHM = "SHA-256";
    private static final int MULTI_STATUS_CODE = 207;
    private static final String ENTRIES_FIELD = "entries";
    private static final String IDEMPOTENCY_KEY_FIELD = "Idempotency-Key";
    private static final String BATCH_BOUNDS_MESSAGE =
        "Attendance sync batch must contain between 1 and 500 entries";

    private final WorkshopApi workshopApi;
    private final RegistrationApi registrationApi;
    private final AttendanceRepository attendanceRepository;
    private final IdempotencyPort idempotencyPort;
    private final AttendanceReconciliationPolicy reconciliationPolicy =
        new AttendanceReconciliationPolicy();

    @Override
    @Transactional
    public List<AttendanceOutcome> execute(SyncAttendancesCommand command) {
        List<SyncEntry> entries = validate(command);

        String requestPath = requestPath(command.workshopId());
        String requestHash = computeRequestHash(command, requestPath);

        ClaimResult<AttendanceOutcome[]> claimResult = idempotencyPort.claim(
            command.staffUserId(),
            command.idempotencyKey(),
            REQUEST_METHOD,
            requestPath,
            requestHash,
            AttendanceOutcome[].class
        );

        if (
            claimResult instanceof
                ClaimResult.Replayed<AttendanceOutcome[]> replayed
        ) {
            return List.of(replayed.response());
        }

        ClaimResult.Acquired<AttendanceOutcome[]> acquired =
            (ClaimResult.Acquired<AttendanceOutcome[]>) claimResult;
        UUID idempotencyKeyRecordId = acquired.idempotencyKeyId();

        try {
            List<AttendanceOutcome> outcomes = reconcile(command, entries);
            idempotencyPort.complete(
                command.staffUserId(),
                idempotencyKeyRecordId,
                command.idempotencyKey(),
                requestHash,
                MULTI_STATUS_CODE,
                outcomes.toArray(AttendanceOutcome[]::new)
            );
            return outcomes;
        } catch (RuntimeException ex) {
            idempotencyPort.fail(
                command.staffUserId(),
                command.idempotencyKey()
            );
            throw ex;
        }
    }

    private List<AttendanceOutcome> reconcile(
        SyncAttendancesCommand command,
        List<SyncEntry> entries
    ) {
        WorkshopRegistrationDetailsDto workshop = workshopApi
            .getWorkshopForRegistration(command.workshopId())
            .orElseThrow(() ->
                new NotFoundException(
                    "Workshop not found: " + command.workshopId()
                )
            );

        if (
            !workshopApi.isStaffAssignedToWorkshop(
                command.workshopId(),
                command.staffUserId()
            )
        ) {
            throw new ForbiddenException(
                "Staff member is not assigned to this workshop"
            );
        }

        requireSyncEligibleStatus(workshop);

        Set<UUID> registrationIds = entries
            .stream()
            .map(SyncEntry::registrationId)
            .collect(Collectors.toSet());
        Map<UUID, RegistrationTicketContextDto> ticketContexts =
            registrationApi.getTicketContexts(registrationIds);

        Set<UUID> admissibleRegistrationIds =
            reconciliationPolicy.resolveAdmissibleRegistrationIds(
                entries,
                ticketContexts,
                command.workshopId()
            );

        Map<UUID, Attendance> existingByRegistrationId =
            attendanceRepository.findByWorkshopIdAndRegistrationIds(
                command.workshopId(),
                registrationIds
            );

        List<SyncEntry> entriesToInsert =
            reconciliationPolicy.resolveEntriesToInsert(
                entries,
                admissibleRegistrationIds,
                existingByRegistrationId
            );

        Set<UUID> persistedSyncIds = persistEntries(
            command,
            entriesToInsert,
            workshop.startsAt()
        );

        return reconciliationPolicy.classify(
            entries,
            admissibleRegistrationIds,
            existingByRegistrationId,
            persistedSyncIds
        );
    }

    private static List<SyncEntry> validate(SyncAttendancesCommand command) {
        if (command == null) {
            throw new ValidationException(ENTRIES_FIELD, BATCH_BOUNDS_MESSAGE);
        }
        if (
            command.idempotencyKey() == null ||
            command.idempotencyKey().isBlank()
        ) {
            throw new ValidationException(
                IDEMPOTENCY_KEY_FIELD,
                "Idempotency-Key header is required"
            );
        }
        if (
            command.workshopId() == null ||
            command.staffUserId() == null ||
            command.entries() == null ||
            command.entries().isEmpty()
        ) {
            throw new ValidationException(ENTRIES_FIELD, BATCH_BOUNDS_MESSAGE);
        }
        return command.entries();
    }

    private static void requireSyncEligibleStatus(
        WorkshopRegistrationDetailsDto workshop
    ) {
        String status = workshop.status();
        if (status == null || !SYNC_ELIGIBLE_STATUSES.contains(status)) {
            throw new ValidationException(
                "workshopId",
                "Workshop does not accept attendance sync in status " + status
            );
        }
    }

    private static String requestPath(UUID workshopId) {
        return SYNC_PATH_TEMPLATE.replace(
            WORKSHOP_ID_PLACEHOLDER,
            workshopId.toString()
        );
    }

    private static String computeRequestHash(
        SyncAttendancesCommand command,
        String requestPath
    ) {
        String canonicalRequest =
            REQUEST_METHOD + requestPath + canonicalBody(command);
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256_ALGORITHM);
            return HexFormat.of().formatHex(
                digest.digest(canonicalRequest.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                "SHA-256 algorithm not available",
                e
            );
        }
    }

    private static String canonicalBody(SyncAttendancesCommand command) {
        StringBuilder body = new StringBuilder("{\"deviceId\":");
        if (command.deviceId() == null) {
            body.append("null");
        } else {
            body.append('"').append(command.deviceId()).append('"');
        }
        body.append(",\"entries\":[");
        List<SyncEntry> entries = command.entries();
        for (int index = 0; index < entries.size(); index++) {
            if (index > 0) {
                body.append(',');
            }
            SyncEntry entry = entries.get(index);
            body.append("{\"syncId\":\"")
                .append(entry.syncId())
                .append("\",\"registrationId\":\"")
                .append(entry.registrationId())
                .append("\",\"scannedAt\":\"")
                .append(entry.scannedAt())
                .append("\"}");
        }
        return body.append("]}").toString();
    }

    private Set<UUID> persistEntries(
        SyncAttendancesCommand command,
        List<SyncEntry> entriesToInsert,
        Instant workshopStartsAt
    ) {
        if (entriesToInsert.isEmpty()) {
            return Set.of();
        }
        Instant receivedAt = Instant.now();
        List<Attendance> toPersist = entriesToInsert
            .stream()
            .map(entry ->
                Attendance.createOfflineSync(
                    command.workshopId(),
                    entry.registrationId(),
                    command.staffUserId(),
                    entry.syncId(),
                    command.deviceId(),
                    entry.scannedAt(),
                    workshopStartsAt,
                    receivedAt
                )
            )
            .toList();
        return attendanceRepository.insertAll(toPersist);
    }
}
