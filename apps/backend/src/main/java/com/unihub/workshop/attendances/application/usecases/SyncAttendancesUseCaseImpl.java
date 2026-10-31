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
import com.unihub.workshop.workshops.api.WorkshopApi;
import com.unihub.workshop.workshops.api.dto.WorkshopRegistrationDetailsDto;
import java.time.Instant;
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

    private final WorkshopApi workshopApi;
    private final RegistrationApi registrationApi;
    private final AttendanceRepository attendanceRepository;
    private final AttendanceReconciliationPolicy reconciliationPolicy =
        new AttendanceReconciliationPolicy();

    @Override
    @Transactional
    public List<AttendanceOutcome> execute(SyncAttendancesCommand command) {
        List<SyncEntry> entries = validate(command);

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

        Set<UUID> persistedSyncIds = persistAdmissibleEntries(
            command,
            entries,
            admissibleRegistrationIds,
            workshop.startsAt()
        );

        return reconciliationPolicy.classify(
            entries,
            admissibleRegistrationIds,
            persistedSyncIds
        );
    }

    private static List<SyncEntry> validate(SyncAttendancesCommand command) {
        if (
            command == null ||
            command.workshopId() == null ||
            command.staffUserId() == null ||
            command.entries() == null ||
            command.entries().isEmpty()
        ) {
            throw new ValidationException(
                "entries",
                "Attendance sync batch must contain between 1 and 500 entries"
            );
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

    private Set<UUID> persistAdmissibleEntries(
        SyncAttendancesCommand command,
        List<SyncEntry> entries,
        Set<UUID> admissibleRegistrationIds,
        Instant workshopStartsAt
    ) {
        Instant receivedAt = Instant.now();
        List<Attendance> toPersist = entries
            .stream()
            .filter(entry ->
                admissibleRegistrationIds.contains(entry.registrationId())
            )
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

        if (toPersist.isEmpty()) {
            return Set.of();
        }
        return attendanceRepository.insertAll(toPersist);
    }
}
