package com.unihub.workshop.attendances.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.unihub.workshop.attendances.application.ports.in.SyncAttendancesCommand;
import com.unihub.workshop.attendances.application.ports.out.AttendanceRepository;
import com.unihub.workshop.attendances.domain.model.Attendance;
import com.unihub.workshop.attendances.domain.model.AttendanceClassification;
import com.unihub.workshop.attendances.domain.model.AttendanceOutcome;
import com.unihub.workshop.attendances.domain.model.AttendanceStatus;
import com.unihub.workshop.attendances.domain.model.SyncEntry;
import com.unihub.workshop.registrations.api.RegistrationApi;
import com.unihub.workshop.registrations.api.dto.RegistrationTicketContextDto;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.workshops.api.WorkshopApi;
import com.unihub.workshop.workshops.api.dto.WorkshopRegistrationDetailsDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SyncAttendancesUseCaseImplTest {

    private static final UUID WORKSHOP_ID = UUID.randomUUID();
    private static final UUID OTHER_WORKSHOP_ID = UUID.randomUUID();
    private static final UUID STAFF_ID = UUID.randomUUID();
    private static final String DEVICE_ID = "device-abc-123";
    private static final Instant STARTS_AT = Instant.now().minusSeconds(3600);
    private static final Instant SCANNED_AT = Instant.now().minusSeconds(60);

    @Mock
    private WorkshopApi workshopApi;

    @Mock
    private RegistrationApi registrationApi;

    @Mock
    private AttendanceRepository attendanceRepository;

    @Captor
    private ArgumentCaptor<Collection<UUID>> registrationIdsCaptor;

    @Captor
    private ArgumentCaptor<Collection<Attendance>> attendancesCaptor;

    private SyncAttendancesUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new SyncAttendancesUseCaseImpl(
            workshopApi,
            registrationApi,
            attendanceRepository
        );
    }

    private static WorkshopRegistrationDetailsDto workshop(String status) {
        return new WorkshopRegistrationDetailsDto(
            WORKSHOP_ID,
            UUID.randomUUID(),
            "Python for Data Science",
            "python-for-data-science",
            status,
            STARTS_AT,
            STARTS_AT.plusSeconds(7200),
            BigDecimal.ZERO,
            50,
            "Hall A"
        );
    }

    private static SyncEntry entry(UUID registrationId, UUID syncId) {
        return new SyncEntry(syncId, registrationId, SCANNED_AT);
    }

    private static RegistrationTicketContextDto ticket(
        UUID registrationId,
        UUID workshopId,
        String status
    ) {
        return new RegistrationTicketContextDto(
            registrationId,
            workshopId,
            status
        );
    }

    private static SyncAttendancesCommand command(SyncEntry... entries) {
        return new SyncAttendancesCommand(
            WORKSHOP_ID,
            STAFF_ID,
            DEVICE_ID,
            List.of(entries)
        );
    }

    private static Map<UUID, RegistrationTicketContextDto> ticketMap(
        RegistrationTicketContextDto... tickets
    ) {
        Map<UUID, RegistrationTicketContextDto> map = new LinkedHashMap<>();
        for (RegistrationTicketContextDto ticket : tickets) {
            map.put(ticket.registrationId(), ticket);
        }
        return map;
    }

    @Test
    @DisplayName(
        "execute persists every admissible entry in one bulk write and reports one SUCCESS outcome per entry"
    )
    void execute_admissibleEntries_persistsAndReportsSuccess() {
        UUID firstRegistration = UUID.randomUUID();
        UUID secondRegistration = UUID.randomUUID();
        UUID thirdRegistration = UUID.randomUUID();
        UUID firstSync = UUID.randomUUID();
        UUID secondSync = UUID.randomUUID();
        UUID thirdSync = UUID.randomUUID();
        List<SyncEntry> entries = List.of(
            entry(firstRegistration, firstSync),
            entry(secondRegistration, secondSync),
            entry(thirdRegistration, thirdSync)
        );

        when(workshopApi.getWorkshopForRegistration(WORKSHOP_ID)).thenReturn(
            Optional.of(workshop("PUBLISHED"))
        );
        when(
            workshopApi.isStaffAssignedToWorkshop(WORKSHOP_ID, STAFF_ID)
        ).thenReturn(true);
        when(registrationApi.getTicketContexts(anyCollection())).thenReturn(
            ticketMap(
                ticket(firstRegistration, WORKSHOP_ID, "CONFIRMED"),
                ticket(secondRegistration, WORKSHOP_ID, "CONFIRMED"),
                ticket(thirdRegistration, WORKSHOP_ID, "CONFIRMED")
            )
        );
        when(attendanceRepository.insertAll(anyCollection())).thenReturn(
            Set.of(firstSync, secondSync, thirdSync)
        );

        List<AttendanceOutcome> outcomes = useCase.execute(
            new SyncAttendancesCommand(
                WORKSHOP_ID,
                STAFF_ID,
                DEVICE_ID,
                entries
            )
        );

        assertThat(outcomes)
            .extracting(AttendanceOutcome::syncId)
            .containsExactly(firstSync, secondSync, thirdSync);
        assertThat(outcomes)
            .extracting(AttendanceOutcome::classification)
            .containsExactly(
                AttendanceClassification.SUCCESS,
                AttendanceClassification.SUCCESS,
                AttendanceClassification.SUCCESS
            );
    }

    @Test
    @DisplayName(
        "execute performs exactly one workshop read, one assignment check, one registration bulk read and one bulk write"
    )
    void execute_performsBoundedDatabaseChatter() {
        UUID registrationId = UUID.randomUUID();
        UUID syncId = UUID.randomUUID();
        List<SyncEntry> entries = List.of(
            entry(registrationId, syncId),
            entry(UUID.randomUUID(), UUID.randomUUID()),
            entry(UUID.randomUUID(), UUID.randomUUID())
        );

        when(workshopApi.getWorkshopForRegistration(WORKSHOP_ID)).thenReturn(
            Optional.of(workshop("PUBLISHED"))
        );
        when(
            workshopApi.isStaffAssignedToWorkshop(WORKSHOP_ID, STAFF_ID)
        ).thenReturn(true);
        when(registrationApi.getTicketContexts(anyCollection())).thenReturn(
            Map.of(
                registrationId,
                ticket(registrationId, WORKSHOP_ID, "CONFIRMED")
            )
        );
        when(attendanceRepository.insertAll(anyCollection())).thenReturn(
            Set.of(syncId)
        );

        useCase.execute(
            new SyncAttendancesCommand(WORKSHOP_ID, STAFF_ID, null, entries)
        );

        verify(workshopApi).getWorkshopForRegistration(WORKSHOP_ID);
        verify(workshopApi).isStaffAssignedToWorkshop(WORKSHOP_ID, STAFF_ID);
        verify(registrationApi).getTicketContexts(anyCollection());
        verify(attendanceRepository).insertAll(anyCollection());
    }

    @Test
    @DisplayName(
        "execute reads every distinct registration identifier in a single bulk lookup"
    )
    void execute_readsDistinctRegistrationIdsInOneLookup() {
        UUID firstRegistration = UUID.randomUUID();
        UUID secondRegistration = UUID.randomUUID();
        List<SyncEntry> entries = List.of(
            entry(firstRegistration, UUID.randomUUID()),
            entry(secondRegistration, UUID.randomUUID())
        );

        when(workshopApi.getWorkshopForRegistration(WORKSHOP_ID)).thenReturn(
            Optional.of(workshop("PUBLISHED"))
        );
        when(
            workshopApi.isStaffAssignedToWorkshop(WORKSHOP_ID, STAFF_ID)
        ).thenReturn(true);
        when(registrationApi.getTicketContexts(anyCollection())).thenReturn(
            Map.of()
        );

        useCase.execute(
            new SyncAttendancesCommand(WORKSHOP_ID, STAFF_ID, null, entries)
        );

        verify(registrationApi).getTicketContexts(
            registrationIdsCaptor.capture()
        );
        assertThat(registrationIdsCaptor.getValue()).containsExactlyInAnyOrder(
            firstRegistration,
            secondRegistration
        );
    }

    @Test
    @DisplayName(
        "execute throws not-found and stops before any assignment, ticket or attendance work when the workshop does not exist"
    )
    void execute_unknownWorkshop_throwsNotFound() {
        when(workshopApi.getWorkshopForRegistration(WORKSHOP_ID)).thenReturn(
            Optional.empty()
        );

        assertThatThrownBy(() ->
            useCase.execute(
                command(entry(UUID.randomUUID(), UUID.randomUUID()))
            )
        )
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining(WORKSHOP_ID.toString());

        verify(workshopApi, never()).isStaffAssignedToWorkshop(any(), any());
        verifyNoInteractions(registrationApi, attendanceRepository);
    }

    @Test
    @DisplayName(
        "execute throws forbidden and writes nothing when the caller is not assigned to the workshop"
    )
    void execute_unassignedStaff_throwsForbidden() {
        when(workshopApi.getWorkshopForRegistration(WORKSHOP_ID)).thenReturn(
            Optional.of(workshop("PUBLISHED"))
        );
        when(
            workshopApi.isStaffAssignedToWorkshop(WORKSHOP_ID, STAFF_ID)
        ).thenReturn(false);

        assertThatThrownBy(() ->
            useCase.execute(
                command(entry(UUID.randomUUID(), UUID.randomUUID()))
            )
        ).isInstanceOf(ForbiddenException.class);

        verifyNoInteractions(registrationApi, attendanceRepository);
    }

    @Test
    @DisplayName(
        "execute rejects a DRAFT workshop with a validation error and writes nothing"
    )
    void execute_draftWorkshop_throwsValidation() {
        when(workshopApi.getWorkshopForRegistration(WORKSHOP_ID)).thenReturn(
            Optional.of(workshop("DRAFT"))
        );
        when(
            workshopApi.isStaffAssignedToWorkshop(WORKSHOP_ID, STAFF_ID)
        ).thenReturn(true);

        assertThatThrownBy(() ->
            useCase.execute(
                command(entry(UUID.randomUUID(), UUID.randomUUID()))
            )
        ).isInstanceOf(ValidationException.class);

        verifyNoInteractions(registrationApi, attendanceRepository);
    }

    @Test
    @DisplayName(
        "execute rejects a CANCELED workshop with a validation error and writes nothing"
    )
    void execute_canceledWorkshop_throwsValidation() {
        when(workshopApi.getWorkshopForRegistration(WORKSHOP_ID)).thenReturn(
            Optional.of(workshop("CANCELED"))
        );
        when(
            workshopApi.isStaffAssignedToWorkshop(WORKSHOP_ID, STAFF_ID)
        ).thenReturn(true);

        assertThatThrownBy(() ->
            useCase.execute(
                command(entry(UUID.randomUUID(), UUID.randomUUID()))
            )
        ).isInstanceOf(ValidationException.class);

        verifyNoInteractions(registrationApi, attendanceRepository);
    }

    @Test
    @DisplayName(
        "execute accepts a COMPLETED workshop so late offline batches are reconciled"
    )
    void execute_completedWorkshop_isAccepted() {
        UUID registrationId = UUID.randomUUID();
        UUID syncId = UUID.randomUUID();

        when(workshopApi.getWorkshopForRegistration(WORKSHOP_ID)).thenReturn(
            Optional.of(workshop("COMPLETED"))
        );
        when(
            workshopApi.isStaffAssignedToWorkshop(WORKSHOP_ID, STAFF_ID)
        ).thenReturn(true);
        when(registrationApi.getTicketContexts(anyCollection())).thenReturn(
            Map.of(
                registrationId,
                ticket(registrationId, WORKSHOP_ID, "CONFIRMED")
            )
        );
        when(attendanceRepository.insertAll(anyCollection())).thenReturn(
            Set.of(syncId)
        );

        List<AttendanceOutcome> outcomes = useCase.execute(
            command(entry(registrationId, syncId))
        );

        assertThat(outcomes).containsExactly(
            new AttendanceOutcome(syncId, AttendanceClassification.SUCCESS)
        );
    }

    @Test
    @DisplayName(
        "execute reports INVALID_TICKET for an absent registration while persisting the remaining entries"
    )
    void execute_absentRegistration_reportsInvalidTicketAndPersistsRest() {
        UUID knownRegistration = UUID.randomUUID();
        UUID unknownRegistration = UUID.randomUUID();
        UUID knownSync = UUID.randomUUID();
        UUID unknownSync = UUID.randomUUID();
        List<SyncEntry> entries = List.of(
            entry(knownRegistration, knownSync),
            entry(unknownRegistration, unknownSync)
        );

        when(workshopApi.getWorkshopForRegistration(WORKSHOP_ID)).thenReturn(
            Optional.of(workshop("PUBLISHED"))
        );
        when(
            workshopApi.isStaffAssignedToWorkshop(WORKSHOP_ID, STAFF_ID)
        ).thenReturn(true);
        when(registrationApi.getTicketContexts(anyCollection())).thenReturn(
            Map.of(
                knownRegistration,
                ticket(knownRegistration, WORKSHOP_ID, "CONFIRMED")
            )
        );
        when(attendanceRepository.insertAll(anyCollection())).thenReturn(
            Set.of(knownSync)
        );

        List<AttendanceOutcome> outcomes = useCase.execute(
            new SyncAttendancesCommand(
                WORKSHOP_ID,
                STAFF_ID,
                DEVICE_ID,
                entries
            )
        );

        assertThat(outcomes).containsExactly(
            new AttendanceOutcome(knownSync, AttendanceClassification.SUCCESS),
            new AttendanceOutcome(
                unknownSync,
                AttendanceClassification.INVALID_TICKET
            )
        );

        verify(attendanceRepository).insertAll(attendancesCaptor.capture());
        assertThat(attendancesCaptor.getValue())
            .extracting(Attendance::registrationId)
            .containsExactly(knownRegistration);
    }

    @Test
    @DisplayName(
        "execute reports INVALID_TICKET for a registration that is no longer CONFIRMED"
    )
    void execute_nonConfirmedRegistration_reportsInvalidTicket() {
        UUID registrationId = UUID.randomUUID();
        UUID syncId = UUID.randomUUID();

        when(workshopApi.getWorkshopForRegistration(WORKSHOP_ID)).thenReturn(
            Optional.of(workshop("PUBLISHED"))
        );
        when(
            workshopApi.isStaffAssignedToWorkshop(WORKSHOP_ID, STAFF_ID)
        ).thenReturn(true);
        when(registrationApi.getTicketContexts(anyCollection())).thenReturn(
            Map.of(
                registrationId,
                ticket(registrationId, WORKSHOP_ID, "CANCELED")
            )
        );

        List<AttendanceOutcome> outcomes = useCase.execute(
            command(entry(registrationId, syncId))
        );

        assertThat(outcomes).containsExactly(
            new AttendanceOutcome(
                syncId,
                AttendanceClassification.INVALID_TICKET
            )
        );
        verify(attendanceRepository, never()).insertAll(anyCollection());
    }

    @Test
    @DisplayName(
        "execute reports INVALID_TICKET for a registration bound to another workshop"
    )
    void execute_registrationOfAnotherWorkshop_reportsInvalidTicket() {
        UUID registrationId = UUID.randomUUID();
        UUID syncId = UUID.randomUUID();

        when(workshopApi.getWorkshopForRegistration(WORKSHOP_ID)).thenReturn(
            Optional.of(workshop("PUBLISHED"))
        );
        when(
            workshopApi.isStaffAssignedToWorkshop(WORKSHOP_ID, STAFF_ID)
        ).thenReturn(true);
        when(registrationApi.getTicketContexts(anyCollection())).thenReturn(
            Map.of(
                registrationId,
                ticket(registrationId, OTHER_WORKSHOP_ID, "CONFIRMED")
            )
        );

        List<AttendanceOutcome> outcomes = useCase.execute(
            command(entry(registrationId, syncId))
        );

        assertThat(outcomes).containsExactly(
            new AttendanceOutcome(
                syncId,
                AttendanceClassification.INVALID_TICKET
            )
        );
        verify(attendanceRepository, never()).insertAll(anyCollection());
    }

    @Test
    @DisplayName(
        "execute reports ALREADY_USED for an entry the bulk write did not persist and leaves the other outcomes intact"
    )
    void execute_syncIdNotPersisted_reportsAlreadyUsed() {
        UUID firstRegistration = UUID.randomUUID();
        UUID secondRegistration = UUID.randomUUID();
        UUID firstSync = UUID.randomUUID();
        UUID secondSync = UUID.randomUUID();

        when(workshopApi.getWorkshopForRegistration(WORKSHOP_ID)).thenReturn(
            Optional.of(workshop("PUBLISHED"))
        );
        when(
            workshopApi.isStaffAssignedToWorkshop(WORKSHOP_ID, STAFF_ID)
        ).thenReturn(true);
        when(registrationApi.getTicketContexts(anyCollection())).thenReturn(
            ticketMap(
                ticket(firstRegistration, WORKSHOP_ID, "CONFIRMED"),
                ticket(secondRegistration, WORKSHOP_ID, "CONFIRMED")
            )
        );
        when(attendanceRepository.insertAll(anyCollection())).thenReturn(
            Set.of(firstSync)
        );

        List<AttendanceOutcome> outcomes = useCase.execute(
            command(
                entry(firstRegistration, firstSync),
                entry(secondRegistration, secondSync)
            )
        );

        assertThat(outcomes).containsExactly(
            new AttendanceOutcome(firstSync, AttendanceClassification.SUCCESS),
            new AttendanceOutcome(
                secondSync,
                AttendanceClassification.ALREADY_USED
            )
        );
    }

    @Test
    @DisplayName(
        "execute builds a VALID OFFLINE_SYNC record attributed to the caller with the clamped scan time"
    )
    void execute_buildsAttendanceRecordWithServerAuthoritativeFields() {
        UUID registrationId = UUID.randomUUID();
        UUID syncId = UUID.randomUUID();

        when(workshopApi.getWorkshopForRegistration(WORKSHOP_ID)).thenReturn(
            Optional.of(workshop("PUBLISHED"))
        );
        when(
            workshopApi.isStaffAssignedToWorkshop(WORKSHOP_ID, STAFF_ID)
        ).thenReturn(true);
        when(registrationApi.getTicketContexts(anyCollection())).thenReturn(
            Map.of(
                registrationId,
                ticket(registrationId, WORKSHOP_ID, "CONFIRMED")
            )
        );
        when(attendanceRepository.insertAll(anyCollection())).thenReturn(
            Set.of(syncId)
        );

        useCase.execute(command(entry(registrationId, syncId)));

        verify(attendanceRepository).insertAll(attendancesCaptor.capture());

        Attendance persisted = attendancesCaptor.getValue().iterator().next();
        assertThat(persisted.workshopId()).isEqualTo(WORKSHOP_ID);
        assertThat(persisted.registrationId()).isEqualTo(registrationId);
        assertThat(persisted.checkedInByUserId()).isEqualTo(STAFF_ID);
        assertThat(persisted.syncId()).isEqualTo(syncId);
        assertThat(persisted.deviceId()).isEqualTo(DEVICE_ID);
        assertThat(persisted.checkInMethod()).isEqualTo(
            Attendance.CHECK_IN_METHOD_OFFLINE_SYNC
        );
        assertThat(persisted.status()).isEqualTo(AttendanceStatus.VALID);
        assertThat(persisted.checkedInAt()).isEqualTo(SCANNED_AT);
        assertThat(persisted.createdBy()).isEqualTo(STAFF_ID);
        assertThat(persisted.updatedBy()).isEqualTo(STAFF_ID);
        assertThat(persisted.createdAt()).isNotNull();
    }

    @Test
    @DisplayName(
        "execute never writes a record whose checked_in_at is later than the server clock"
    )
    void execute_futureScan_isClampedToServerClock() {
        UUID registrationId = UUID.randomUUID();
        UUID syncId = UUID.randomUUID();
        Instant futureScan = Instant.now().plusSeconds(86_400);

        when(workshopApi.getWorkshopForRegistration(WORKSHOP_ID)).thenReturn(
            Optional.of(workshop("PUBLISHED"))
        );
        when(
            workshopApi.isStaffAssignedToWorkshop(WORKSHOP_ID, STAFF_ID)
        ).thenReturn(true);
        when(registrationApi.getTicketContexts(anyCollection())).thenReturn(
            Map.of(
                registrationId,
                ticket(registrationId, WORKSHOP_ID, "CONFIRMED")
            )
        );
        when(attendanceRepository.insertAll(anyCollection())).thenReturn(
            Set.of(syncId)
        );

        Instant before = Instant.now();
        useCase.execute(
            command(new SyncEntry(syncId, registrationId, futureScan))
        );
        Instant after = Instant.now();

        verify(attendanceRepository).insertAll(attendancesCaptor.capture());

        assertThat(
            attendancesCaptor.getValue().iterator().next().checkedInAt()
        ).isBetween(before, after);
    }

    @Test
    @DisplayName(
        "execute rejects an empty batch with a validation error and touches no port"
    )
    void execute_emptyBatch_throwsValidation() {
        assertThatThrownBy(() ->
            useCase.execute(
                new SyncAttendancesCommand(
                    WORKSHOP_ID,
                    STAFF_ID,
                    DEVICE_ID,
                    List.of()
                )
            )
        ).isInstanceOf(ValidationException.class);

        verifyNoInteractions(
            workshopApi,
            registrationApi,
            attendanceRepository
        );
    }

    @Test
    @DisplayName(
        "execute rejects a missing command or batch with a validation error and touches no port"
    )
    void execute_missingCommand_throwsValidation() {
        assertThatThrownBy(() -> useCase.execute(null)).isInstanceOf(
            ValidationException.class
        );
        assertThatThrownBy(() ->
            useCase.execute(
                new SyncAttendancesCommand(
                    WORKSHOP_ID,
                    STAFF_ID,
                    DEVICE_ID,
                    null
                )
            )
        ).isInstanceOf(ValidationException.class);

        verifyNoInteractions(
            workshopApi,
            registrationApi,
            attendanceRepository
        );
    }

    @Test
    @DisplayName(
        "execute skips the bulk write when every entry is an invalid ticket"
    )
    void execute_allInvalidTickets_skipsBulkWrite() {
        UUID registrationId = UUID.randomUUID();
        UUID syncId = UUID.randomUUID();

        when(workshopApi.getWorkshopForRegistration(WORKSHOP_ID)).thenReturn(
            Optional.of(workshop("PUBLISHED"))
        );
        when(
            workshopApi.isStaffAssignedToWorkshop(WORKSHOP_ID, STAFF_ID)
        ).thenReturn(true);
        when(registrationApi.getTicketContexts(anyCollection())).thenReturn(
            Map.of()
        );

        List<AttendanceOutcome> outcomes = useCase.execute(
            command(entry(registrationId, syncId))
        );

        assertThat(outcomes).containsExactly(
            new AttendanceOutcome(
                syncId,
                AttendanceClassification.INVALID_TICKET
            )
        );
        verify(attendanceRepository, never()).insertAll(anyCollection());
    }
}
