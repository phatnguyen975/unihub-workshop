package com.unihub.workshop.attendances.application.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.unihub.workshop.attendances.domain.model.Attendance;
import com.unihub.workshop.attendances.domain.model.AttendanceClassification;
import com.unihub.workshop.attendances.domain.model.AttendanceOutcome;
import com.unihub.workshop.attendances.domain.model.AttendanceStatus;
import com.unihub.workshop.attendances.domain.model.SyncEntry;
import com.unihub.workshop.registrations.api.dto.RegistrationTicketContextDto;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AttendanceReconciliationPolicyTest {

    private static final UUID WORKSHOP_ID = UUID.randomUUID();
    private static final UUID OTHER_WORKSHOP_ID = UUID.randomUUID();
    private static final Instant SCANNED_AT = Instant.parse(
        "2026-11-15T09:30:00Z"
    );

    private AttendanceReconciliationPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new AttendanceReconciliationPolicy();
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

    private static Attendance existing(
        UUID registrationId,
        UUID syncId,
        AttendanceStatus status
    ) {
        return Attendance.builder()
            .id(UUID.randomUUID())
            .workshopId(WORKSHOP_ID)
            .registrationId(registrationId)
            .checkedInByUserId(UUID.randomUUID())
            .syncId(syncId)
            .deviceId("device-abc-123")
            .checkInMethod(Attendance.CHECK_IN_METHOD_OFFLINE_SYNC)
            .status(status)
            .checkedInAt(SCANNED_AT)
            .createdAt(SCANNED_AT)
            .updatedAt(SCANNED_AT)
            .createdBy(UUID.randomUUID())
            .updatedBy(UUID.randomUUID())
            .build();
    }

    @Test
    @DisplayName(
        "resolveAdmissibleRegistrationIds admits a CONFIRMED registration of the target workshop"
    )
    void resolveAdmissibleRegistrationIds_admitsConfirmedRegistrationOfWorkshop() {
        UUID registrationId = UUID.randomUUID();
        List<SyncEntry> entries = List.of(
            entry(registrationId, UUID.randomUUID())
        );

        Set<UUID> admissible = policy.resolveAdmissibleRegistrationIds(
            entries,
            Map.of(
                registrationId,
                ticket(registrationId, WORKSHOP_ID, "CONFIRMED")
            ),
            WORKSHOP_ID
        );

        assertThat(admissible).containsExactly(registrationId);
    }

    @Test
    @DisplayName(
        "resolveAdmissibleRegistrationIds rejects a registration absent from the ticket context"
    )
    void resolveAdmissibleRegistrationIds_rejectsAbsentRegistration() {
        UUID registrationId = UUID.randomUUID();
        List<SyncEntry> entries = List.of(
            entry(registrationId, UUID.randomUUID())
        );

        Set<UUID> admissible = policy.resolveAdmissibleRegistrationIds(
            entries,
            Map.of(),
            WORKSHOP_ID
        );

        assertThat(admissible).isEmpty();
    }

    @Test
    @DisplayName(
        "resolveAdmissibleRegistrationIds rejects registrations that are not CONFIRMED"
    )
    void resolveAdmissibleRegistrationIds_rejectsNonConfirmedRegistrations() {
        UUID pending = UUID.randomUUID();
        UUID failed = UUID.randomUUID();
        UUID canceled = UUID.randomUUID();
        List<SyncEntry> entries = List.of(
            entry(pending, UUID.randomUUID()),
            entry(failed, UUID.randomUUID()),
            entry(canceled, UUID.randomUUID())
        );

        Set<UUID> admissible = policy.resolveAdmissibleRegistrationIds(
            entries,
            Map.of(
                pending,
                ticket(pending, WORKSHOP_ID, "PENDING"),
                failed,
                ticket(failed, WORKSHOP_ID, "FAILED"),
                canceled,
                ticket(canceled, WORKSHOP_ID, "CANCELED")
            ),
            WORKSHOP_ID
        );

        assertThat(admissible).isEmpty();
    }

    @Test
    @DisplayName(
        "resolveAdmissibleRegistrationIds rejects a CONFIRMED registration bound to another workshop"
    )
    void resolveAdmissibleRegistrationIds_rejectsRegistrationOfAnotherWorkshop() {
        UUID registrationId = UUID.randomUUID();
        List<SyncEntry> entries = List.of(
            entry(registrationId, UUID.randomUUID())
        );

        Set<UUID> admissible = policy.resolveAdmissibleRegistrationIds(
            entries,
            Map.of(
                registrationId,
                ticket(registrationId, OTHER_WORKSHOP_ID, "CONFIRMED")
            ),
            WORKSHOP_ID
        );

        assertThat(admissible).isEmpty();
    }

    @Test
    @DisplayName(
        "resolveAdmissibleRegistrationIds returns an empty set for an empty batch"
    )
    void resolveAdmissibleRegistrationIds_emptyBatch_returnsEmptySet() {
        assertThat(
            policy.resolveAdmissibleRegistrationIds(
                List.of(),
                Map.of(),
                WORKSHOP_ID
            )
        ).isEmpty();
        assertThat(
            policy.resolveAdmissibleRegistrationIds(null, null, WORKSHOP_ID)
        ).isEmpty();
    }

    @Test
    @DisplayName(
        "classify reports SUCCESS for an admissible entry whose syncId was persisted"
    )
    void classify_admissibleAndPersisted_reportsSuccess() {
        UUID registrationId = UUID.randomUUID();
        UUID syncId = UUID.randomUUID();
        List<SyncEntry> entries = List.of(entry(registrationId, syncId));

        List<AttendanceOutcome> outcomes = policy.classify(
            entries,
            Set.of(registrationId),
            Map.of(),
            Set.of(syncId)
        );

        assertThat(outcomes).containsExactly(
            new AttendanceOutcome(syncId, AttendanceClassification.SUCCESS)
        );
    }

    @Test
    @DisplayName(
        "classify reports ALREADY_USED for an admissible entry whose syncId was not persisted"
    )
    void classify_admissibleAndNotPersisted_reportsAlreadyUsed() {
        UUID registrationId = UUID.randomUUID();
        UUID syncId = UUID.randomUUID();
        List<SyncEntry> entries = List.of(entry(registrationId, syncId));

        List<AttendanceOutcome> outcomes = policy.classify(
            entries,
            Set.of(registrationId),
            Map.of(),
            Set.of()
        );

        assertThat(outcomes).containsExactly(
            new AttendanceOutcome(syncId, AttendanceClassification.ALREADY_USED)
        );
    }

    @Test
    @DisplayName(
        "classify reports INVALID_TICKET for an inadmissible entry even when its syncId was persisted"
    )
    void classify_inadmissible_reportsInvalidTicket() {
        UUID registrationId = UUID.randomUUID();
        UUID syncId = UUID.randomUUID();
        List<SyncEntry> entries = List.of(entry(registrationId, syncId));

        List<AttendanceOutcome> outcomes = policy.classify(
            entries,
            Set.of(),
            Map.of(),
            Set.of(syncId)
        );

        assertThat(outcomes).containsExactly(
            new AttendanceOutcome(
                syncId,
                AttendanceClassification.INVALID_TICKET
            )
        );
    }

    @Test
    @DisplayName(
        "classify returns exactly one outcome per entry in the submitted order"
    )
    void classify_preservesSubmittedOrder() {
        UUID validRegistration = UUID.randomUUID();
        UUID invalidRegistration = UUID.randomUUID();
        UUID firstSyncId = UUID.randomUUID();
        UUID secondSyncId = UUID.randomUUID();
        UUID thirdSyncId = UUID.randomUUID();
        List<SyncEntry> entries = List.of(
            entry(validRegistration, firstSyncId),
            entry(invalidRegistration, secondSyncId),
            entry(validRegistration, thirdSyncId)
        );

        List<AttendanceOutcome> outcomes = policy.classify(
            entries,
            Set.of(validRegistration),
            Map.of(),
            Set.of(firstSyncId)
        );

        assertThat(outcomes)
            .extracting(AttendanceOutcome::syncId)
            .containsExactly(firstSyncId, secondSyncId, thirdSyncId);
        assertThat(outcomes)
            .extracting(AttendanceOutcome::classification)
            .containsExactly(
                AttendanceClassification.SUCCESS,
                AttendanceClassification.INVALID_TICKET,
                AttendanceClassification.ALREADY_USED
            );
    }

    @Test
    @DisplayName("classify returns an empty list for an empty batch")
    void classify_emptyBatch_returnsEmptyList() {
        assertThat(
            policy.classify(List.of(), Set.of(), Map.of(), Set.of())
        ).isEmpty();
    }

    @Test
    @DisplayName(
        "classify reports INVALID_TICKET for an inadmissible entry regardless of the attendance record it already has"
    )
    void classify_inadmissibleWithExistingRecord_reportsInvalidTicket() {
        UUID sameSyncRegistration = UUID.randomUUID();
        UUID invalidatedRegistration = UUID.randomUUID();
        UUID sameSync = UUID.randomUUID();
        UUID invalidatedSync = UUID.randomUUID();
        List<SyncEntry> entries = List.of(
            entry(sameSyncRegistration, sameSync),
            entry(invalidatedRegistration, invalidatedSync)
        );

        List<AttendanceOutcome> outcomes = policy.classify(
            entries,
            Set.of(),
            Map.of(
                sameSyncRegistration,
                existing(
                    sameSyncRegistration,
                    sameSync,
                    AttendanceStatus.VALID
                ),
                invalidatedRegistration,
                existing(
                    invalidatedRegistration,
                    invalidatedSync,
                    AttendanceStatus.INVALIDATED
                )
            ),
            Set.of()
        );

        assertThat(outcomes)
            .extracting(AttendanceOutcome::classification)
            .containsExactly(
                AttendanceClassification.INVALID_TICKET,
                AttendanceClassification.INVALID_TICKET
            );
    }

    @Test
    @DisplayName(
        "classify reports SUCCESS for a VALID record carrying the same syncId, without requiring a write"
    )
    void classify_validRecordWithSameSyncId_reportsSuccess() {
        UUID registrationId = UUID.randomUUID();
        UUID syncId = UUID.randomUUID();
        List<SyncEntry> entries = List.of(entry(registrationId, syncId));

        List<AttendanceOutcome> outcomes = policy.classify(
            entries,
            Set.of(registrationId),
            Map.of(
                registrationId,
                existing(registrationId, syncId, AttendanceStatus.VALID)
            ),
            Set.of()
        );

        assertThat(outcomes).containsExactly(
            new AttendanceOutcome(syncId, AttendanceClassification.SUCCESS)
        );
    }

    @Test
    @DisplayName(
        "classify reports ALREADY_USED for a VALID record carrying a different syncId"
    )
    void classify_validRecordWithDifferentSyncId_reportsAlreadyUsed() {
        UUID registrationId = UUID.randomUUID();
        UUID storedSyncId = UUID.randomUUID();
        UUID submittedSyncId = UUID.randomUUID();
        List<SyncEntry> entries = List.of(
            entry(registrationId, submittedSyncId)
        );

        List<AttendanceOutcome> outcomes = policy.classify(
            entries,
            Set.of(registrationId),
            Map.of(
                registrationId,
                existing(registrationId, storedSyncId, AttendanceStatus.VALID)
            ),
            Set.of()
        );

        assertThat(outcomes).containsExactly(
            new AttendanceOutcome(
                submittedSyncId,
                AttendanceClassification.ALREADY_USED
            )
        );
    }

    @Test
    @DisplayName(
        "classify reports ALREADY_USED for an INVALIDATED record even when the syncId matches exactly"
    )
    void classify_invalidatedRecordWithSameSyncId_reportsAlreadyUsed() {
        UUID registrationId = UUID.randomUUID();
        UUID syncId = UUID.randomUUID();
        List<SyncEntry> entries = List.of(entry(registrationId, syncId));

        List<AttendanceOutcome> outcomes = policy.classify(
            entries,
            Set.of(registrationId),
            Map.of(
                registrationId,
                existing(registrationId, syncId, AttendanceStatus.INVALIDATED)
            ),
            Set.of()
        );

        assertThat(outcomes).containsExactly(
            new AttendanceOutcome(syncId, AttendanceClassification.ALREADY_USED)
        );
    }

    @Test
    @DisplayName(
        "classify reports ALREADY_USED for an INVALIDATED record carrying a different syncId"
    )
    void classify_invalidatedRecordWithDifferentSyncId_reportsAlreadyUsed() {
        UUID registrationId = UUID.randomUUID();
        UUID storedSyncId = UUID.randomUUID();
        UUID submittedSyncId = UUID.randomUUID();
        List<SyncEntry> entries = List.of(
            entry(registrationId, submittedSyncId)
        );

        List<AttendanceOutcome> outcomes = policy.classify(
            entries,
            Set.of(registrationId),
            Map.of(
                registrationId,
                existing(
                    registrationId,
                    storedSyncId,
                    AttendanceStatus.INVALIDATED
                )
            ),
            Set.of(submittedSyncId)
        );

        assertThat(outcomes).containsExactly(
            new AttendanceOutcome(
                submittedSyncId,
                AttendanceClassification.ALREADY_USED
            )
        );
    }

    @Test
    @DisplayName(
        "resolveEntriesToInsert keeps only admissible entries without an existing record, in submitted order"
    )
    void resolveEntriesToInsert_keepsOnlyNewAdmissibleEntries() {
        UUID retriedRegistration = UUID.randomUUID();
        UUID newRegistration = UUID.randomUUID();
        UUID inadmissibleRegistration = UUID.randomUUID();
        UUID retriedSync = UUID.randomUUID();
        UUID newSync = UUID.randomUUID();
        UUID inadmissibleSync = UUID.randomUUID();
        List<SyncEntry> entries = List.of(
            entry(retriedRegistration, retriedSync),
            entry(newRegistration, newSync),
            entry(inadmissibleRegistration, inadmissibleSync)
        );

        List<SyncEntry> toInsert = policy.resolveEntriesToInsert(
            entries,
            Set.of(retriedRegistration, newRegistration),
            Map.of(
                retriedRegistration,
                existing(
                    retriedRegistration,
                    retriedSync,
                    AttendanceStatus.VALID
                )
            )
        );

        assertThat(toInsert).containsExactly(entry(newRegistration, newSync));
    }

    @Test
    @DisplayName(
        "resolveEntriesToInsert never reinserts an INVALIDATED record, even with a different syncId"
    )
    void resolveEntriesToInsert_invalidatedRecord_isNeverReinserted() {
        UUID registrationId = UUID.randomUUID();
        UUID storedSyncId = UUID.randomUUID();
        UUID submittedSyncId = UUID.randomUUID();

        List<SyncEntry> toInsert = policy.resolveEntriesToInsert(
            List.of(entry(registrationId, submittedSyncId)),
            Set.of(registrationId),
            Map.of(
                registrationId,
                existing(
                    registrationId,
                    storedSyncId,
                    AttendanceStatus.INVALIDATED
                )
            )
        );

        assertThat(toInsert).isEmpty();
    }

    @Test
    @DisplayName(
        "resolveEntriesToInsert returns an empty list for an empty batch or absent inputs"
    )
    void resolveEntriesToInsert_emptyBatch_returnsEmptyList() {
        assertThat(
            policy.resolveEntriesToInsert(List.of(), Set.of(), Map.of())
        ).isEmpty();
        assertThat(policy.resolveEntriesToInsert(null, null, null)).isEmpty();
    }
}
