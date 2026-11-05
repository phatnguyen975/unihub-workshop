package com.unihub.workshop.attendances.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.unihub.workshop.attendances.application.events.AttendanceSyncEventPublisher;
import com.unihub.workshop.attendances.application.observability.AttendanceSyncMetrics;
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
import com.unihub.workshop.shared.exception.ServiceUnavailableException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.shared.idempotency.ClaimResult;
import com.unihub.workshop.shared.idempotency.FailedKeyPolicy;
import com.unihub.workshop.shared.idempotency.IdempotencyConflictException;
import com.unihub.workshop.shared.idempotency.IdempotencyPort;
import com.unihub.workshop.workshops.api.WorkshopApi;
import com.unihub.workshop.workshops.api.dto.WorkshopRegistrationDetailsDto;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class SyncAttendancesUseCaseImplTest {

    private static final UUID WORKSHOP_ID = UUID.randomUUID();
    private static final UUID OTHER_WORKSHOP_ID = UUID.randomUUID();
    private static final UUID STAFF_ID = UUID.randomUUID();
    private static final UUID IDEMPOTENCY_KEY_ID = UUID.randomUUID();
    private static final String IDEMPOTENCY_KEY = UUID.randomUUID().toString();
    private static final String DEVICE_ID = "device-abc-123";
    private static final Instant STARTS_AT = Instant.now().minusSeconds(3600);
    private static final Instant SCANNED_AT = Instant.now().minusSeconds(60);

    @Mock
    private WorkshopApi workshopApi;

    @Mock
    private RegistrationApi registrationApi;

    @Mock
    private AttendanceRepository attendanceRepository;

    @Mock
    private IdempotencyPort idempotencyPort;

    @Mock
    private AttendanceSyncEventPublisher attendanceSyncEventPublisher;

    @Mock
    private AttendanceSyncMetrics attendanceSyncMetrics;

    @Captor
    private ArgumentCaptor<Collection<UUID>> registrationIdsCaptor;

    @Captor
    private ArgumentCaptor<Collection<Attendance>> attendancesCaptor;

    @Captor
    private ArgumentCaptor<Collection<UUID>> attendanceLookupIdsCaptor;

    @Captor
    private ArgumentCaptor<String> requestHashCaptor;

    @Captor
    private ArgumentCaptor<AttendanceOutcome[]> completedPayloadCaptor;

    private SyncAttendancesUseCaseImpl useCase;

    private AttendanceSyncMetrics.RequestScope requestScope;

    @BeforeEach
    void setUp() {
        requestScope = new AttendanceSyncMetrics.RequestScope(
            Timer.start(new SimpleMeterRegistry()),
            STAFF_ID,
            WORKSHOP_ID,
            1
        );
        lenient()
            .when(attendanceSyncMetrics.startRequest(any()))
            .thenReturn(requestScope);

        lenient()
            .when(
                idempotencyPort.claim(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    eq(AttendanceOutcome[].class),
                    eq(FailedKeyPolicy.RECLAIM)
                )
            )
            .thenReturn(new ClaimResult.Acquired<>(IDEMPOTENCY_KEY_ID));

        useCase = new SyncAttendancesUseCaseImpl(
            workshopApi,
            registrationApi,
            attendanceRepository,
            idempotencyPort,
            attendanceSyncEventPublisher,
            attendanceSyncMetrics
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
            IDEMPOTENCY_KEY,
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

    private static Attendance storedAttendance(
        UUID registrationId,
        UUID syncId,
        AttendanceStatus status
    ) {
        return Attendance.builder()
            .id(UUID.randomUUID())
            .workshopId(WORKSHOP_ID)
            .registrationId(registrationId)
            .checkedInByUserId(STAFF_ID)
            .syncId(syncId)
            .deviceId(DEVICE_ID)
            .checkInMethod(Attendance.CHECK_IN_METHOD_OFFLINE_SYNC)
            .status(status)
            .checkedInAt(SCANNED_AT)
            .createdAt(SCANNED_AT)
            .updatedAt(SCANNED_AT)
            .createdBy(STAFF_ID)
            .updatedBy(STAFF_ID)
            .build();
    }

    private void stubClaimResult(ClaimResult<AttendanceOutcome[]> result) {
        when(
            idempotencyPort.claim(
                any(),
                any(),
                any(),
                any(),
                any(),
                eq(AttendanceOutcome[].class),
                eq(FailedKeyPolicy.RECLAIM)
            )
        ).thenReturn(result);
    }

    private void stubSuccessfulReconciliation(
        UUID registrationId,
        UUID syncId
    ) {
        when(workshopApi.getWorkshopForRegistration(WORKSHOP_ID)).thenReturn(
            Optional.of(workshop("PUBLISHED"))
        );
        when(
            workshopApi.isStaffAssignedToWorkshop(WORKSHOP_ID, STAFF_ID)
        ).thenReturn(true);
        when(
            registrationApi.getTicketContexts(eq(WORKSHOP_ID), anyCollection())
        ).thenReturn(
            Map.of(
                registrationId,
                ticket(registrationId, WORKSHOP_ID, "CONFIRMED")
            )
        );
        when(attendanceRepository.insertAll(anyCollection())).thenReturn(
            Set.of(syncId)
        );
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
        when(
            registrationApi.getTicketContexts(eq(WORKSHOP_ID), anyCollection())
        ).thenReturn(
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
                IDEMPOTENCY_KEY,
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
        when(
            registrationApi.getTicketContexts(eq(WORKSHOP_ID), anyCollection())
        ).thenReturn(
            Map.of(
                registrationId,
                ticket(registrationId, WORKSHOP_ID, "CONFIRMED")
            )
        );
        when(attendanceRepository.insertAll(anyCollection())).thenReturn(
            Set.of(syncId)
        );

        useCase.execute(
            new SyncAttendancesCommand(
                WORKSHOP_ID,
                STAFF_ID,
                IDEMPOTENCY_KEY,
                null,
                entries
            )
        );

        verify(workshopApi).getWorkshopForRegistration(WORKSHOP_ID);
        verify(workshopApi).isStaffAssignedToWorkshop(WORKSHOP_ID, STAFF_ID);
        verify(registrationApi).getTicketContexts(
            eq(WORKSHOP_ID),
            anyCollection()
        );
        verify(attendanceRepository).findByWorkshopIdAndRegistrationIds(
            eq(WORKSHOP_ID),
            anyCollection()
        );
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
        when(
            registrationApi.getTicketContexts(eq(WORKSHOP_ID), anyCollection())
        ).thenReturn(Map.of());

        useCase.execute(
            new SyncAttendancesCommand(
                WORKSHOP_ID,
                STAFF_ID,
                IDEMPOTENCY_KEY,
                null,
                entries
            )
        );

        verify(registrationApi).getTicketContexts(
            eq(WORKSHOP_ID),
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
        when(
            registrationApi.getTicketContexts(eq(WORKSHOP_ID), anyCollection())
        ).thenReturn(
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
        when(
            registrationApi.getTicketContexts(eq(WORKSHOP_ID), anyCollection())
        ).thenReturn(
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
                IDEMPOTENCY_KEY,
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
        when(
            registrationApi.getTicketContexts(eq(WORKSHOP_ID), anyCollection())
        ).thenReturn(
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
        when(
            registrationApi.getTicketContexts(eq(WORKSHOP_ID), anyCollection())
        ).thenReturn(
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
        when(
            registrationApi.getTicketContexts(eq(WORKSHOP_ID), anyCollection())
        ).thenReturn(
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
        when(
            registrationApi.getTicketContexts(eq(WORKSHOP_ID), anyCollection())
        ).thenReturn(
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
        when(
            registrationApi.getTicketContexts(eq(WORKSHOP_ID), anyCollection())
        ).thenReturn(
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
                    IDEMPOTENCY_KEY,
                    DEVICE_ID,
                    List.of()
                )
            )
        ).isInstanceOf(ValidationException.class);

        verifyNoInteractions(
            idempotencyPort,
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
                    IDEMPOTENCY_KEY,
                    DEVICE_ID,
                    null
                )
            )
        ).isInstanceOf(ValidationException.class);

        verifyNoInteractions(
            idempotencyPort,
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
        when(
            registrationApi.getTicketContexts(eq(WORKSHOP_ID), anyCollection())
        ).thenReturn(Map.of());

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
        "execute reports SUCCESS for a retried batch with identical syncIds and performs no write"
    )
    void execute_identicalRetry_reportsSuccessWithoutWriting() {
        UUID firstRegistration = UUID.randomUUID();
        UUID secondRegistration = UUID.randomUUID();
        UUID firstSync = UUID.randomUUID();
        UUID secondSync = UUID.randomUUID();
        List<SyncEntry> entries = List.of(
            entry(firstRegistration, firstSync),
            entry(secondRegistration, secondSync)
        );

        when(workshopApi.getWorkshopForRegistration(WORKSHOP_ID)).thenReturn(
            Optional.of(workshop("PUBLISHED"))
        );
        when(
            workshopApi.isStaffAssignedToWorkshop(WORKSHOP_ID, STAFF_ID)
        ).thenReturn(true);
        when(
            registrationApi.getTicketContexts(eq(WORKSHOP_ID), anyCollection())
        ).thenReturn(
            ticketMap(
                ticket(firstRegistration, WORKSHOP_ID, "CONFIRMED"),
                ticket(secondRegistration, WORKSHOP_ID, "CONFIRMED")
            )
        );
        when(
            attendanceRepository.findByWorkshopIdAndRegistrationIds(
                eq(WORKSHOP_ID),
                anyCollection()
            )
        ).thenReturn(
            Map.of(
                firstRegistration,
                storedAttendance(
                    firstRegistration,
                    firstSync,
                    AttendanceStatus.VALID
                ),
                secondRegistration,
                storedAttendance(
                    secondRegistration,
                    secondSync,
                    AttendanceStatus.VALID
                )
            )
        );

        List<AttendanceOutcome> outcomes = useCase.execute(
            new SyncAttendancesCommand(
                WORKSHOP_ID,
                STAFF_ID,
                IDEMPOTENCY_KEY,
                DEVICE_ID,
                entries
            )
        );

        assertThat(outcomes)
            .extracting(AttendanceOutcome::classification)
            .containsExactly(
                AttendanceClassification.SUCCESS,
                AttendanceClassification.SUCCESS
            );
        verify(attendanceRepository, never()).insertAll(anyCollection());
    }

    @Test
    @DisplayName(
        "execute reports ALREADY_USED for a double scan carrying a different syncId and leaves the stored record untouched"
    )
    void execute_doubleScanWithDifferentSyncId_reportsAlreadyUsed() {
        UUID registrationId = UUID.randomUUID();
        UUID storedSync = UUID.randomUUID();
        UUID submittedSync = UUID.randomUUID();

        when(workshopApi.getWorkshopForRegistration(WORKSHOP_ID)).thenReturn(
            Optional.of(workshop("PUBLISHED"))
        );
        when(
            workshopApi.isStaffAssignedToWorkshop(WORKSHOP_ID, STAFF_ID)
        ).thenReturn(true);
        when(
            registrationApi.getTicketContexts(eq(WORKSHOP_ID), anyCollection())
        ).thenReturn(
            Map.of(
                registrationId,
                ticket(registrationId, WORKSHOP_ID, "CONFIRMED")
            )
        );
        when(
            attendanceRepository.findByWorkshopIdAndRegistrationIds(
                eq(WORKSHOP_ID),
                anyCollection()
            )
        ).thenReturn(
            Map.of(
                registrationId,
                storedAttendance(
                    registrationId,
                    storedSync,
                    AttendanceStatus.VALID
                )
            )
        );

        List<AttendanceOutcome> outcomes = useCase.execute(
            command(entry(registrationId, submittedSync))
        );

        assertThat(outcomes).containsExactly(
            new AttendanceOutcome(
                submittedSync,
                AttendanceClassification.ALREADY_USED
            )
        );
        verify(attendanceRepository, never()).insertAll(anyCollection());
    }

    @Test
    @DisplayName(
        "execute reports ALREADY_USED for an INVALIDATED record and never reactivates it"
    )
    void execute_invalidatedRecord_reportsAlreadyUsedWithoutWriting() {
        UUID registrationId = UUID.randomUUID();
        UUID syncId = UUID.randomUUID();

        when(workshopApi.getWorkshopForRegistration(WORKSHOP_ID)).thenReturn(
            Optional.of(workshop("PUBLISHED"))
        );
        when(
            workshopApi.isStaffAssignedToWorkshop(WORKSHOP_ID, STAFF_ID)
        ).thenReturn(true);
        when(
            registrationApi.getTicketContexts(eq(WORKSHOP_ID), anyCollection())
        ).thenReturn(
            Map.of(
                registrationId,
                ticket(registrationId, WORKSHOP_ID, "CONFIRMED")
            )
        );
        when(
            attendanceRepository.findByWorkshopIdAndRegistrationIds(
                eq(WORKSHOP_ID),
                anyCollection()
            )
        ).thenReturn(
            Map.of(
                registrationId,
                storedAttendance(
                    registrationId,
                    syncId,
                    AttendanceStatus.INVALIDATED
                )
            )
        );

        List<AttendanceOutcome> outcomes = useCase.execute(
            command(entry(registrationId, syncId))
        );

        assertThat(outcomes).containsExactly(
            new AttendanceOutcome(syncId, AttendanceClassification.ALREADY_USED)
        );
        verify(attendanceRepository, never()).insertAll(anyCollection());
    }

    @Test
    @DisplayName(
        "execute persists only the entries without an existing record while reporting the retried one as SUCCESS"
    )
    void execute_mixedRetryAndNewScan_persistsOnlyNewEntries() {
        UUID retriedRegistration = UUID.randomUUID();
        UUID newRegistration = UUID.randomUUID();
        UUID retriedSync = UUID.randomUUID();
        UUID newSync = UUID.randomUUID();

        when(workshopApi.getWorkshopForRegistration(WORKSHOP_ID)).thenReturn(
            Optional.of(workshop("PUBLISHED"))
        );
        when(
            workshopApi.isStaffAssignedToWorkshop(WORKSHOP_ID, STAFF_ID)
        ).thenReturn(true);
        when(
            registrationApi.getTicketContexts(eq(WORKSHOP_ID), anyCollection())
        ).thenReturn(
            ticketMap(
                ticket(retriedRegistration, WORKSHOP_ID, "CONFIRMED"),
                ticket(newRegistration, WORKSHOP_ID, "CONFIRMED")
            )
        );
        when(
            attendanceRepository.findByWorkshopIdAndRegistrationIds(
                eq(WORKSHOP_ID),
                anyCollection()
            )
        ).thenReturn(
            Map.of(
                retriedRegistration,
                storedAttendance(
                    retriedRegistration,
                    retriedSync,
                    AttendanceStatus.VALID
                )
            )
        );
        when(attendanceRepository.insertAll(anyCollection())).thenReturn(
            Set.of(newSync)
        );

        List<AttendanceOutcome> outcomes = useCase.execute(
            command(
                entry(retriedRegistration, retriedSync),
                entry(newRegistration, newSync)
            )
        );

        assertThat(outcomes).containsExactly(
            new AttendanceOutcome(
                retriedSync,
                AttendanceClassification.SUCCESS
            ),
            new AttendanceOutcome(newSync, AttendanceClassification.SUCCESS)
        );
        verify(attendanceRepository).insertAll(attendancesCaptor.capture());
        assertThat(attendancesCaptor.getValue())
            .extracting(Attendance::registrationId)
            .containsExactly(newRegistration);
        assertThat(attendancesCaptor.getValue())
            .extracting(Attendance::syncId)
            .containsExactly(newSync);
    }

    @Test
    @DisplayName(
        "execute reads the workshop's existing attendances exactly once for the whole batch"
    )
    void execute_readsExistingAttendancesOnceForTheBatch() {
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
        when(
            registrationApi.getTicketContexts(eq(WORKSHOP_ID), anyCollection())
        ).thenReturn(
            ticketMap(
                ticket(firstRegistration, WORKSHOP_ID, "CONFIRMED"),
                ticket(secondRegistration, WORKSHOP_ID, "CONFIRMED")
            )
        );
        when(attendanceRepository.insertAll(anyCollection())).thenReturn(
            Set.of(firstSync, secondSync)
        );

        useCase.execute(
            command(
                entry(firstRegistration, firstSync),
                entry(secondRegistration, secondSync)
            )
        );

        verify(attendanceRepository).findByWorkshopIdAndRegistrationIds(
            eq(WORKSHOP_ID),
            attendanceLookupIdsCaptor.capture()
        );
        assertThat(
            attendanceLookupIdsCaptor.getValue()
        ).containsExactlyInAnyOrder(firstRegistration, secondRegistration);
    }

    @Test
    @DisplayName(
        "execute reports INVALID_TICKET for a registration canceled after check-in and never alters the stored record"
    )
    void execute_canceledAfterCheckIn_reportsInvalidTicket() {
        UUID registrationId = UUID.randomUUID();
        UUID storedSync = UUID.randomUUID();

        when(workshopApi.getWorkshopForRegistration(WORKSHOP_ID)).thenReturn(
            Optional.of(workshop("PUBLISHED"))
        );
        when(
            workshopApi.isStaffAssignedToWorkshop(WORKSHOP_ID, STAFF_ID)
        ).thenReturn(true);
        when(
            registrationApi.getTicketContexts(eq(WORKSHOP_ID), anyCollection())
        ).thenReturn(
            Map.of(
                registrationId,
                ticket(registrationId, WORKSHOP_ID, "CANCELED")
            )
        );
        when(
            attendanceRepository.findByWorkshopIdAndRegistrationIds(
                eq(WORKSHOP_ID),
                anyCollection()
            )
        ).thenReturn(
            Map.of(
                registrationId,
                storedAttendance(
                    registrationId,
                    storedSync,
                    AttendanceStatus.VALID
                )
            )
        );

        List<AttendanceOutcome> outcomes = useCase.execute(
            command(entry(registrationId, storedSync))
        );

        assertThat(outcomes).containsExactly(
            new AttendanceOutcome(
                storedSync,
                AttendanceClassification.INVALID_TICKET
            )
        );
        verify(attendanceRepository, never()).insertAll(anyCollection());
    }

    @Test
    @DisplayName(
        "execute replays the stored outcomes for a completed key without touching any business port"
    )
    void execute_completedKey_replaysStoredOutcomesWithoutBusinessReads() {
        UUID firstSync = UUID.randomUUID();
        UUID secondSync = UUID.randomUUID();
        AttendanceOutcome[] stored = {
            new AttendanceOutcome(firstSync, AttendanceClassification.SUCCESS),
            new AttendanceOutcome(
                secondSync,
                AttendanceClassification.ALREADY_USED
            ),
        };
        stubClaimResult(new ClaimResult.Replayed<>(stored));

        List<AttendanceOutcome> outcomes = useCase.execute(
            command(
                entry(UUID.randomUUID(), firstSync),
                entry(UUID.randomUUID(), secondSync)
            )
        );

        assertThat(outcomes).containsExactly(stored);
        verifyNoInteractions(
            workshopApi,
            registrationApi,
            attendanceRepository
        );
        verify(idempotencyPort, never()).complete(
            any(),
            any(),
            any(),
            any(),
            anyInt(),
            any()
        );
        verify(idempotencyPort, never()).fail(any(), any());
    }

    @Test
    @DisplayName(
        "execute propagates an in-flight conflict without releasing the claim or reading any business state"
    )
    void execute_inFlightKey_propagatesConflictWithoutBusinessReads() {
        IdempotencyConflictException conflict =
            new IdempotencyConflictException(
                "Request is currently in progress",
                2
            );
        when(
            idempotencyPort.claim(
                any(),
                any(),
                any(),
                any(),
                any(),
                eq(AttendanceOutcome[].class),
                eq(FailedKeyPolicy.RECLAIM)
            )
        ).thenThrow(conflict);

        assertThatThrownBy(() ->
            useCase.execute(
                command(entry(UUID.randomUUID(), UUID.randomUUID()))
            )
        ).isSameAs(conflict);

        verifyNoInteractions(
            workshopApi,
            registrationApi,
            attendanceRepository
        );
        verify(idempotencyPort, never()).fail(any(), any());
        verify(idempotencyPort, never()).complete(
            any(),
            any(),
            any(),
            any(),
            anyInt(),
            any()
        );
    }

    @Test
    @DisplayName(
        "execute claims the caller-scoped key before the first business read"
    )
    void execute_claimsKeyBeforeFirstBusinessRead() {
        UUID registrationId = UUID.randomUUID();
        UUID syncId = UUID.randomUUID();
        stubSuccessfulReconciliation(registrationId, syncId);

        useCase.execute(command(entry(registrationId, syncId)));

        InOrder inOrder = inOrder(idempotencyPort, workshopApi);
        inOrder
            .verify(idempotencyPort)
            .claim(
                eq(STAFF_ID),
                eq(IDEMPOTENCY_KEY),
                eq("POST"),
                eq("/api/v1/workshops/" + WORKSHOP_ID + "/attendances/sync"),
                requestHashCaptor.capture(),
                eq(AttendanceOutcome[].class),
                eq(FailedKeyPolicy.RECLAIM)
            );
        inOrder.verify(workshopApi).getWorkshopForRegistration(WORKSHOP_ID);
        assertThat(requestHashCaptor.getValue()).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName(
        "execute completes the claim with 207 and the domain outcome payload"
    )
    void execute_success_completesClaimWithMultiStatusAndOutcomes() {
        UUID registrationId = UUID.randomUUID();
        UUID syncId = UUID.randomUUID();
        stubSuccessfulReconciliation(registrationId, syncId);

        useCase.execute(command(entry(registrationId, syncId)));

        verify(idempotencyPort).complete(
            eq(STAFF_ID),
            eq(IDEMPOTENCY_KEY_ID),
            eq(IDEMPOTENCY_KEY),
            anyString(),
            eq(207),
            completedPayloadCaptor.capture()
        );
        assertThat(completedPayloadCaptor.getValue()).containsExactly(
            new AttendanceOutcome(syncId, AttendanceClassification.SUCCESS)
        );
    }

    @Test
    @DisplayName(
        "execute releases the claim and rethrows when reconciliation throws"
    )
    void execute_reconciliationFailure_releasesClaimAndRethrows() {
        IllegalStateException failure = new IllegalStateException(
            "reconciliation failed"
        );
        when(workshopApi.getWorkshopForRegistration(WORKSHOP_ID)).thenReturn(
            Optional.of(workshop("PUBLISHED"))
        );
        when(
            workshopApi.isStaffAssignedToWorkshop(WORKSHOP_ID, STAFF_ID)
        ).thenReturn(true);
        when(
            registrationApi.getTicketContexts(eq(WORKSHOP_ID), anyCollection())
        ).thenThrow(failure);

        assertThatThrownBy(() ->
            useCase.execute(
                command(entry(UUID.randomUUID(), UUID.randomUUID()))
            )
        ).isSameAs(failure);

        verify(idempotencyPort).fail(STAFF_ID, IDEMPOTENCY_KEY);
        verify(idempotencyPort, never()).complete(
            any(),
            any(),
            any(),
            any(),
            anyInt(),
            any()
        );
    }

    @Test
    @DisplayName(
        "execute derives one stable hash per request body and isolates the workshop in that hash"
    )
    void execute_requestHash_isolatesBodyAndWorkshop() {
        UUID registrationId = UUID.randomUUID();
        UUID syncId = UUID.randomUUID();
        List<SyncEntry> entries = List.of(entry(registrationId, syncId));
        when(workshopApi.getWorkshopForRegistration(any())).thenReturn(
            Optional.empty()
        );

        assertThatThrownBy(() ->
            useCase.execute(
                new SyncAttendancesCommand(
                    WORKSHOP_ID,
                    STAFF_ID,
                    IDEMPOTENCY_KEY,
                    DEVICE_ID,
                    entries
                )
            )
        ).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() ->
            useCase.execute(
                new SyncAttendancesCommand(
                    WORKSHOP_ID,
                    STAFF_ID,
                    IDEMPOTENCY_KEY,
                    "other-device",
                    entries
                )
            )
        ).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() ->
            useCase.execute(
                new SyncAttendancesCommand(
                    OTHER_WORKSHOP_ID,
                    STAFF_ID,
                    IDEMPOTENCY_KEY,
                    DEVICE_ID,
                    entries
                )
            )
        ).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() ->
            useCase.execute(
                new SyncAttendancesCommand(
                    WORKSHOP_ID,
                    STAFF_ID,
                    IDEMPOTENCY_KEY,
                    DEVICE_ID,
                    entries
                )
            )
        ).isInstanceOf(NotFoundException.class);

        verify(idempotencyPort, times(4)).claim(
            eq(STAFF_ID),
            eq(IDEMPOTENCY_KEY),
            eq("POST"),
            anyString(),
            requestHashCaptor.capture(),
            eq(AttendanceOutcome[].class),
            eq(FailedKeyPolicy.RECLAIM)
        );

        List<String> hashes = requestHashCaptor.getAllValues();
        assertThat(hashes.get(0)).isEqualTo(hashes.get(3));
        assertThat(hashes.get(0)).isNotEqualTo(hashes.get(1));
        assertThat(hashes.get(0)).isNotEqualTo(hashes.get(2));
    }

    @Test
    @DisplayName(
        "execute rejects a blank idempotency key before claiming or reading anything"
    )
    void execute_blankIdempotencyKey_throwsValidation() {
        SyncEntry entry = entry(UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() ->
            useCase.execute(
                new SyncAttendancesCommand(
                    WORKSHOP_ID,
                    STAFF_ID,
                    "   ",
                    DEVICE_ID,
                    List.of(entry)
                )
            )
        ).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() ->
            useCase.execute(
                new SyncAttendancesCommand(
                    WORKSHOP_ID,
                    STAFF_ID,
                    null,
                    DEVICE_ID,
                    List.of(entry)
                )
            )
        ).isInstanceOf(ValidationException.class);

        verifyNoInteractions(
            idempotencyPort,
            workshopApi,
            registrationApi,
            attendanceRepository
        );
    }

    @Test
    @DisplayName(
        "execute publishes exactly one attendance.updated event when new rows were persisted"
    )
    void execute_newRowsPersisted_publishesOneEvent() {
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
        when(
            registrationApi.getTicketContexts(eq(WORKSHOP_ID), anyCollection())
        ).thenReturn(
            ticketMap(
                ticket(firstRegistration, WORKSHOP_ID, "CONFIRMED"),
                ticket(secondRegistration, WORKSHOP_ID, "CONFIRMED")
            )
        );
        when(attendanceRepository.insertAll(anyCollection())).thenReturn(
            Set.of(firstSync, secondSync)
        );

        useCase.execute(
            command(
                entry(firstRegistration, firstSync),
                entry(secondRegistration, secondSync)
            )
        );

        verify(attendanceSyncEventPublisher, times(1)).publishAfterCommit(
            WORKSHOP_ID,
            50
        );
    }

    @Test
    @DisplayName(
        "execute publishes no event for a batch made only of same-syncId retries"
    )
    void execute_pureRetryBatch_publishesNoEvent() {
        UUID registrationId = UUID.randomUUID();
        UUID syncId = UUID.randomUUID();

        when(workshopApi.getWorkshopForRegistration(WORKSHOP_ID)).thenReturn(
            Optional.of(workshop("PUBLISHED"))
        );
        when(
            workshopApi.isStaffAssignedToWorkshop(WORKSHOP_ID, STAFF_ID)
        ).thenReturn(true);
        when(
            registrationApi.getTicketContexts(eq(WORKSHOP_ID), anyCollection())
        ).thenReturn(
            Map.of(
                registrationId,
                ticket(registrationId, WORKSHOP_ID, "CONFIRMED")
            )
        );
        when(
            attendanceRepository.findByWorkshopIdAndRegistrationIds(
                eq(WORKSHOP_ID),
                anyCollection()
            )
        ).thenReturn(
            Map.of(
                registrationId,
                storedAttendance(registrationId, syncId, AttendanceStatus.VALID)
            )
        );

        List<AttendanceOutcome> outcomes = useCase.execute(
            command(entry(registrationId, syncId))
        );

        assertThat(outcomes).containsExactly(
            new AttendanceOutcome(syncId, AttendanceClassification.SUCCESS)
        );
        verify(attendanceRepository, never()).insertAll(anyCollection());
        verifyNoInteractions(attendanceSyncEventPublisher);
    }

    @Test
    @DisplayName("execute publishes no event when the bulk write fails")
    void execute_bulkWriteFailure_publishesNoEvent() {
        UUID registrationId = UUID.randomUUID();
        UUID syncId = UUID.randomUUID();

        when(workshopApi.getWorkshopForRegistration(WORKSHOP_ID)).thenReturn(
            Optional.of(workshop("PUBLISHED"))
        );
        when(
            workshopApi.isStaffAssignedToWorkshop(WORKSHOP_ID, STAFF_ID)
        ).thenReturn(true);
        when(
            registrationApi.getTicketContexts(eq(WORKSHOP_ID), anyCollection())
        ).thenReturn(
            Map.of(
                registrationId,
                ticket(registrationId, WORKSHOP_ID, "CONFIRMED")
            )
        );
        when(attendanceRepository.insertAll(anyCollection())).thenThrow(
            new IllegalStateException("primary write failed")
        );

        assertThatThrownBy(() ->
            useCase.execute(command(entry(registrationId, syncId)))
        ).isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(attendanceSyncEventPublisher);
    }

    @Test
    @DisplayName(
        "execute publishes no event when no entry is admissible and nothing is written"
    )
    void execute_noAdmissibleEntries_publishesNoEvent() {
        UUID registrationId = UUID.randomUUID();
        UUID syncId = UUID.randomUUID();

        when(workshopApi.getWorkshopForRegistration(WORKSHOP_ID)).thenReturn(
            Optional.of(workshop("PUBLISHED"))
        );
        when(
            workshopApi.isStaffAssignedToWorkshop(WORKSHOP_ID, STAFF_ID)
        ).thenReturn(true);
        when(
            registrationApi.getTicketContexts(eq(WORKSHOP_ID), anyCollection())
        ).thenReturn(Map.of());

        List<AttendanceOutcome> outcomes = useCase.execute(
            command(entry(registrationId, syncId))
        );

        assertThat(outcomes).containsExactly(
            new AttendanceOutcome(
                syncId,
                AttendanceClassification.INVALID_TICKET
            )
        );
        verifyNoInteractions(attendanceSyncEventPublisher);
    }

    @Test
    @DisplayName(
        "execute publishes no event when the request is replayed from the idempotency store"
    )
    void execute_replayedRequest_publishesNoEvent() {
        UUID syncId = UUID.randomUUID();
        stubClaimResult(
            new ClaimResult.Replayed<>(new AttendanceOutcome[] {
                new AttendanceOutcome(syncId, AttendanceClassification.SUCCESS),
            })
        );

        useCase.execute(command(entry(UUID.randomUUID(), syncId)));

        verifyNoInteractions(attendanceSyncEventPublisher);
    }

    @Test
    @DisplayName(
        "execute counts the request, tallies every outcome and completes the scope exactly once on success"
    )
    void execute_successfulReconciliation_recordsOutcomesAndCompletesOnce() {
        UUID registrationId = UUID.randomUUID();
        UUID syncId = UUID.randomUUID();

        when(workshopApi.getWorkshopForRegistration(WORKSHOP_ID)).thenReturn(
            Optional.of(workshop("PUBLISHED"))
        );
        when(
            workshopApi.isStaffAssignedToWorkshop(WORKSHOP_ID, STAFF_ID)
        ).thenReturn(true);
        when(
            registrationApi.getTicketContexts(eq(WORKSHOP_ID), anyCollection())
        ).thenReturn(
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

        verify(attendanceSyncMetrics).startRequest(any());
        verify(attendanceSyncMetrics).recordOutcomes(outcomes);
        verify(attendanceSyncMetrics, times(1)).recordCompleted(
            requestScope,
            outcomes
        );
        verify(attendanceSyncMetrics, never()).recordBulkWriteFailure();
    }

    @Test
    @DisplayName(
        "execute counts a replayed request but never re-tallies its stored outcomes"
    )
    void execute_replayedRequest_countsButDoesNotRecordOutcomes() {
        UUID syncId = UUID.randomUUID();
        stubClaimResult(
            new ClaimResult.Replayed<>(new AttendanceOutcome[] {
                new AttendanceOutcome(syncId, AttendanceClassification.SUCCESS),
            })
        );

        List<AttendanceOutcome> outcomes = useCase.execute(
            command(entry(UUID.randomUUID(), syncId))
        );

        verify(attendanceSyncMetrics).startRequest(any());
        verify(attendanceSyncMetrics, never()).recordOutcomes(any());
        verify(attendanceSyncMetrics, times(1)).recordCompleted(
            requestScope,
            outcomes
        );
    }

    @Test
    @DisplayName(
        "execute completes the scope without outcome tallies when reconciliation fails"
    )
    void execute_failedReconciliation_completesWithoutOutcomeCounts() {
        when(workshopApi.getWorkshopForRegistration(WORKSHOP_ID)).thenReturn(
            Optional.empty()
        );

        assertThatThrownBy(() ->
            useCase.execute(
                command(entry(UUID.randomUUID(), UUID.randomUUID()))
            )
        ).isInstanceOf(NotFoundException.class);

        verify(attendanceSyncMetrics).startRequest(any());
        verify(attendanceSyncMetrics, never()).recordOutcomes(any());
        verify(attendanceSyncMetrics, times(1)).recordCompleted(
            requestScope,
            List.of()
        );
    }

    @Test
    @DisplayName(
        "execute counts a failed bulk write and completes the scope without outcome tallies"
    )
    void execute_bulkWriteFailure_incrementsBulkWriteFailureCounter() {
        UUID registrationId = UUID.randomUUID();
        UUID syncId = UUID.randomUUID();

        when(workshopApi.getWorkshopForRegistration(WORKSHOP_ID)).thenReturn(
            Optional.of(workshop("PUBLISHED"))
        );
        when(
            workshopApi.isStaffAssignedToWorkshop(WORKSHOP_ID, STAFF_ID)
        ).thenReturn(true);
        when(
            registrationApi.getTicketContexts(eq(WORKSHOP_ID), anyCollection())
        ).thenReturn(
            Map.of(
                registrationId,
                ticket(registrationId, WORKSHOP_ID, "CONFIRMED")
            )
        );
        when(attendanceRepository.insertAll(anyCollection())).thenThrow(
            new IllegalStateException("primary write failed")
        );

        assertThatThrownBy(() ->
            useCase.execute(command(entry(registrationId, syncId)))
        ).isInstanceOf(IllegalStateException.class);

        verify(attendanceSyncMetrics).recordBulkWriteFailure();
        verify(attendanceSyncMetrics, never()).recordOutcomes(any());
        verify(attendanceSyncMetrics, times(1)).recordCompleted(
            requestScope,
            List.of()
        );
    }

    @Test
    @DisplayName(
        "execute translates a primary-database failure of the bulk write into 503 with Retry-After"
    )
    void execute_primaryDatabaseFailureOnBulkWrite_returnsServiceUnavailable() {
        UUID registrationId = UUID.randomUUID();
        UUID syncId = UUID.randomUUID();
        DataAccessResourceFailureException failure =
            new DataAccessResourceFailureException(
                "primary database is unreachable"
            );

        when(workshopApi.getWorkshopForRegistration(WORKSHOP_ID)).thenReturn(
            Optional.of(workshop("PUBLISHED"))
        );
        when(
            workshopApi.isStaffAssignedToWorkshop(WORKSHOP_ID, STAFF_ID)
        ).thenReturn(true);
        when(
            registrationApi.getTicketContexts(eq(WORKSHOP_ID), anyCollection())
        ).thenReturn(
            Map.of(
                registrationId,
                ticket(registrationId, WORKSHOP_ID, "CONFIRMED")
            )
        );
        when(attendanceRepository.insertAll(anyCollection())).thenThrow(
            failure
        );

        assertThatThrownBy(() ->
            useCase.execute(command(entry(registrationId, syncId)))
        ).isInstanceOfSatisfying(
            ServiceUnavailableException.class,
            serviceUnavailable -> {
                assertThat(serviceUnavailable.getStatus()).isEqualTo(
                    HttpStatus.SERVICE_UNAVAILABLE
                );
                assertThat(serviceUnavailable.getType()).isEqualTo(
                    "urn:unihub:error:service-unavailable"
                );
                assertThat(serviceUnavailable.getRetryAfterSeconds())
                    .isNotNull()
                    .isPositive();
                assertThat(serviceUnavailable.getCause()).isSameAs(failure);
            }
        );

        verify(idempotencyPort).fail(STAFF_ID, IDEMPOTENCY_KEY);
        verify(idempotencyPort, never()).complete(
            any(),
            any(),
            any(),
            any(),
            anyInt(),
            any()
        );
        verifyNoInteractions(attendanceSyncEventPublisher);
        verify(attendanceSyncMetrics, never()).recordOutcomes(any());
    }

    @Test
    void execute_bulkDataAccessFailure_returnsServiceUnavailable() {
        UUID registrationId = UUID.randomUUID();
        UUID syncId = UUID.randomUUID();
        stubSuccessfulReconciliation(registrationId, syncId);
        DataIntegrityViolationException failure =
            new DataIntegrityViolationException("write failed");
        when(attendanceRepository.insertAll(anyCollection())).thenThrow(
            failure
        );

        assertThatThrownBy(() ->
            useCase.execute(command(entry(registrationId, syncId)))
        ).isInstanceOfSatisfying(ServiceUnavailableException.class, exception ->
            assertThat(exception.getCause()).isSameAs(failure)
        );
    }

    @Test
    @DisplayName(
        "execute translates a primary-database failure of a reconciliation read into 503"
    )
    void execute_primaryDatabaseFailureOnRead_returnsServiceUnavailable() {
        DataAccessResourceFailureException failure =
            new DataAccessResourceFailureException(
                "primary database is unreachable"
            );

        when(workshopApi.getWorkshopForRegistration(WORKSHOP_ID)).thenReturn(
            Optional.of(workshop("PUBLISHED"))
        );
        when(
            workshopApi.isStaffAssignedToWorkshop(WORKSHOP_ID, STAFF_ID)
        ).thenReturn(true);
        when(
            registrationApi.getTicketContexts(eq(WORKSHOP_ID), anyCollection())
        ).thenThrow(failure);

        assertThatThrownBy(() ->
            useCase.execute(
                command(entry(UUID.randomUUID(), UUID.randomUUID()))
            )
        ).isInstanceOfSatisfying(
            ServiceUnavailableException.class,
            serviceUnavailable ->
                assertThat(serviceUnavailable.getCause()).isSameAs(failure)
        );

        verify(idempotencyPort).fail(STAFF_ID, IDEMPOTENCY_KEY);
        verifyNoInteractions(attendanceSyncEventPublisher);
    }

    @Test
    @DisplayName(
        "execute translates a primary-database failure while completing the claim into 503"
    )
    void execute_primaryDatabaseFailureOnClaimCompletion_returnsServiceUnavailable() {
        UUID registrationId = UUID.randomUUID();
        UUID syncId = UUID.randomUUID();
        DataAccessResourceFailureException failure =
            new DataAccessResourceFailureException(
                "primary database is unreachable"
            );

        stubSuccessfulReconciliation(registrationId, syncId);
        doThrow(failure)
            .when(idempotencyPort)
            .complete(any(), any(), any(), any(), anyInt(), any());

        assertThatThrownBy(() ->
            useCase.execute(command(entry(registrationId, syncId)))
        ).isInstanceOfSatisfying(
            ServiceUnavailableException.class,
            serviceUnavailable ->
                assertThat(serviceUnavailable.getCause()).isSameAs(failure)
        );

        verify(idempotencyPort).fail(STAFF_ID, IDEMPOTENCY_KEY);
        verify(attendanceSyncMetrics, never()).recordOutcomes(any());
    }

    @Test
    @DisplayName(
        "execute keeps the original 503 when releasing the claim fails a second time"
    )
    void execute_releaseFailure_doesNotMaskTheOriginalFailure() {
        UUID registrationId = UUID.randomUUID();
        UUID syncId = UUID.randomUUID();
        DataAccessResourceFailureException failure =
            new DataAccessResourceFailureException(
                "primary database is unreachable"
            );
        DataAccessResourceFailureException releaseFailure =
            new DataAccessResourceFailureException(
                "primary database is still unreachable"
            );

        when(workshopApi.getWorkshopForRegistration(WORKSHOP_ID)).thenReturn(
            Optional.of(workshop("PUBLISHED"))
        );
        when(
            workshopApi.isStaffAssignedToWorkshop(WORKSHOP_ID, STAFF_ID)
        ).thenReturn(true);
        when(
            registrationApi.getTicketContexts(eq(WORKSHOP_ID), anyCollection())
        ).thenReturn(
            Map.of(
                registrationId,
                ticket(registrationId, WORKSHOP_ID, "CONFIRMED")
            )
        );
        when(attendanceRepository.insertAll(anyCollection())).thenThrow(
            failure
        );
        doThrow(releaseFailure)
            .when(idempotencyPort)
            .fail(STAFF_ID, IDEMPOTENCY_KEY);

        assertThatThrownBy(() ->
            useCase.execute(command(entry(registrationId, syncId)))
        ).isInstanceOfSatisfying(
            ServiceUnavailableException.class,
            serviceUnavailable -> {
                assertThat(serviceUnavailable.getCause()).isSameAs(failure);
                assertThat(failure.getSuppressed()).contains(releaseFailure);
            }
        );

        verify(attendanceSyncMetrics, times(1)).recordCompleted(
            requestScope,
            List.of()
        );
    }

    @Test
    @DisplayName(
        "execute translates a primary-database failure of the idempotency claim into 503 without attempting a release"
    )
    void execute_claimFailure_returnsServiceUnavailableWithoutReleaseAttempt() {
        DataAccessResourceFailureException failure =
            new DataAccessResourceFailureException(
                "primary database is unreachable"
            );
        when(
            idempotencyPort.claim(
                any(),
                any(),
                any(),
                any(),
                any(),
                eq(AttendanceOutcome[].class),
                eq(FailedKeyPolicy.RECLAIM)
            )
        ).thenThrow(failure);

        assertThatThrownBy(() ->
            useCase.execute(
                command(entry(UUID.randomUUID(), UUID.randomUUID()))
            )
        ).isInstanceOfSatisfying(
            ServiceUnavailableException.class,
            serviceUnavailable ->
                assertThat(serviceUnavailable.getCause()).isSameAs(failure)
        );

        verify(idempotencyPort, never()).fail(any(), any());
        verifyNoInteractions(attendanceSyncEventPublisher);
        verify(attendanceSyncMetrics, times(1)).recordCompleted(
            requestScope,
            List.of()
        );
    }

    @Test
    @DisplayName(
        "execute stays non-read-only transactional so every read and write is routed to the primary"
    )
    void execute_isNonReadOnlyTransactional() throws NoSuchMethodException {
        Transactional transactional = SyncAttendancesUseCaseImpl.class
            .getMethod("execute", SyncAttendancesCommand.class)
            .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
    }
}
