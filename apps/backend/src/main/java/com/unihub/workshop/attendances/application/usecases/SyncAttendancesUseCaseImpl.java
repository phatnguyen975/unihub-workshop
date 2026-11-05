package com.unihub.workshop.attendances.application.usecases;

import com.unihub.workshop.attendances.application.events.AttendanceSyncEventPublisher;
import com.unihub.workshop.attendances.application.observability.AttendanceSyncMetrics;
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
import com.unihub.workshop.shared.exception.ServiceUnavailableException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.shared.idempotency.ClaimResult;
import com.unihub.workshop.shared.idempotency.FailedKeyPolicy;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
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
 *
 * <p>Every request is counted and timed from the moment it reaches the use case and is
 * closed out with exactly one structured log line, whatever its outcome. A replayed
 * request is logged but never re-tallies the outcomes of the original request.
 *
 * <p>A primary-database failure is contained: the request is answered with a
 * {@link ServiceUnavailableException} carrying a {@code Retry-After} indication instead
 * of a generic server error, the idempotency claim is released so the client can retry,
 * and the flow never falls back to a replica.
 */
@Slf4j
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
    private static final int RETRY_AFTER_SECONDS = 30;
    private static final String PRIMARY_DATABASE_UNAVAILABLE_MESSAGE =
        "Attendance sync is temporarily unavailable. Please retry.";

    private final WorkshopApi workshopApi;
    private final RegistrationApi registrationApi;
    private final AttendanceRepository attendanceRepository;
    private final IdempotencyPort idempotencyPort;
    private final AttendanceSyncEventPublisher attendanceSyncEventPublisher;
    private final AttendanceSyncMetrics attendanceSyncMetrics;
    private final AttendanceReconciliationPolicy reconciliationPolicy =
        new AttendanceReconciliationPolicy();

    @Override
    @Transactional
    public List<AttendanceOutcome> execute(SyncAttendancesCommand command) {
        AttendanceSyncMetrics.RequestScope scope =
            attendanceSyncMetrics.startRequest(command);
        List<AttendanceOutcome> outcomes = List.of();
        try {
            outcomes = reconcileRequest(command);
            return outcomes;
        } catch (RuntimeException ex) {
            throw translateFailure(ex);
        } finally {
            attendanceSyncMetrics.recordCompleted(scope, outcomes);
        }
    }

    private List<AttendanceOutcome> reconcileRequest(
        SyncAttendancesCommand command
    ) {
        List<SyncEntry> entries = validate(command);

        String requestPath = requestPath(command.workshopId());
        String requestHash = computeRequestHash(command, requestPath);

        ClaimResult<AttendanceOutcome[]> claimResult = idempotencyPort.claim(
            command.staffUserId(),
            command.idempotencyKey(),
            REQUEST_METHOD,
            requestPath,
            requestHash,
            AttendanceOutcome[].class,
            FailedKeyPolicy.RECLAIM
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
            attendanceSyncMetrics.recordOutcomes(outcomes);
            return outcomes;
        } catch (RuntimeException ex) {
            releaseClaim(command, ex);
            throw ex;
        }
    }

    /**
     * Releases the idempotency claim acquired for this request so the client can retry.
     *
     * <p>A failure of the release itself is never allowed to replace the failure that
     * triggered it: it is attached to the original error as a suppressed exception and
     * logged, and the original error keeps propagating.
     *
     * @param command the request whose claim is being released
     * @param failure the failure that triggered the release
     */
    private void releaseClaim(
        SyncAttendancesCommand command,
        RuntimeException failure
    ) {
        try {
            idempotencyPort.fail(
                command.staffUserId(),
                command.idempotencyKey()
            );
        } catch (RuntimeException releaseFailure) {
            if (releaseFailure != failure) {
                failure.addSuppressed(releaseFailure);
            }
            log.warn(
                "Failed to release the attendance sync idempotency claim for user {} on workshop {}",
                command.staffUserId(),
                command.workshopId(),
                releaseFailure
            );
        }
    }

    /**
     * Translates an unreachable primary database into the endpoint's documented
     * {@code 503} response, so the caller receives a retryable error instead of a
     * generic server error. Every other failure is returned unchanged.
     *
     * @param failure failure raised while processing the request
     * @return the failure to propagate
     */
    private static RuntimeException translateFailure(RuntimeException failure) {
        if (failure instanceof DataAccessResourceFailureException) {
            return new ServiceUnavailableException(
                PRIMARY_DATABASE_UNAVAILABLE_MESSAGE,
                RETRY_AFTER_SECONDS,
                failure
            );
        }
        return failure;
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
            registrationApi.getTicketContexts(
                command.workshopId(),
                registrationIds
            );

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

        if (!persistedSyncIds.isEmpty()) {
            attendanceSyncEventPublisher.publishAfterCommit(
                command.workshopId(),
                workshop.capacity()
            );
        }

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
        try {
            return attendanceRepository.insertAll(toPersist);
        } catch (DataAccessException ex) {
            attendanceSyncMetrics.recordBulkWriteFailure();
            if (ex instanceof DataAccessResourceFailureException) {
                throw ex;
            }
            throw new ServiceUnavailableException(
                PRIMARY_DATABASE_UNAVAILABLE_MESSAGE,
                RETRY_AFTER_SECONDS,
                ex
            );
        } catch (RuntimeException ex) {
            attendanceSyncMetrics.recordBulkWriteFailure();
            throw ex;
        }
    }
}
