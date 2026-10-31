package com.unihub.workshop.attendances.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AttendanceTest {

    private static final UUID WORKSHOP_ID = UUID.randomUUID();
    private static final UUID REGISTRATION_ID = UUID.randomUUID();
    private static final UUID STAFF_ID = UUID.randomUUID();
    private static final UUID SYNC_ID = UUID.randomUUID();
    private static final String DEVICE_ID = "device-abc-123";
    private static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");
    private static final Instant STARTS_AT = Instant.parse(
        "2026-11-15T09:00:00Z"
    );

    @Test
    @DisplayName(
        "createOfflineSync persists the server-authoritative audit fields and OFFLINE_SYNC method"
    )
    void createOfflineSync_populatesServerAuthoritativeFields() {
        Instant scannedAt = Instant.parse("2026-11-15T09:30:00Z");

        Attendance attendance = Attendance.createOfflineSync(
            WORKSHOP_ID,
            REGISTRATION_ID,
            STAFF_ID,
            SYNC_ID,
            DEVICE_ID,
            scannedAt,
            STARTS_AT,
            NOW
        );

        assertThat(attendance.id()).isNotNull();
        assertThat(attendance.workshopId()).isEqualTo(WORKSHOP_ID);
        assertThat(attendance.registrationId()).isEqualTo(REGISTRATION_ID);
        assertThat(attendance.checkedInByUserId()).isEqualTo(STAFF_ID);
        assertThat(attendance.syncId()).isEqualTo(SYNC_ID);
        assertThat(attendance.deviceId()).isEqualTo(DEVICE_ID);
        assertThat(attendance.checkInMethod()).isEqualTo(
            Attendance.CHECK_IN_METHOD_OFFLINE_SYNC
        );
        assertThat(attendance.status()).isEqualTo(AttendanceStatus.VALID);
        assertThat(attendance.createdAt()).isEqualTo(NOW);
        assertThat(attendance.updatedAt()).isEqualTo(NOW);
        assertThat(attendance.createdBy()).isEqualTo(STAFF_ID);
        assertThat(attendance.updatedBy()).isEqualTo(STAFF_ID);
    }

    @Test
    @DisplayName(
        "createOfflineSync keeps the submitted scannedAt when it falls inside the workshop window"
    )
    void createOfflineSync_keepsScannedAt_whenInsideWindow() {
        Instant scannedAt = Instant.parse("2026-11-15T09:30:00Z");

        Attendance attendance = Attendance.createOfflineSync(
            WORKSHOP_ID,
            REGISTRATION_ID,
            STAFF_ID,
            SYNC_ID,
            DEVICE_ID,
            scannedAt,
            STARTS_AT,
            NOW
        );

        assertThat(attendance.checkedInAt()).isEqualTo(scannedAt);
    }

    @Test
    @DisplayName(
        "createOfflineSync clamps a scan earlier than the workshop start to the workshop start"
    )
    void createOfflineSync_clampsScannedAtToWorkshopStart() {
        Instant scannedAt = Instant.parse("2026-11-15T08:00:00Z");

        Attendance attendance = Attendance.createOfflineSync(
            WORKSHOP_ID,
            REGISTRATION_ID,
            STAFF_ID,
            SYNC_ID,
            DEVICE_ID,
            scannedAt,
            STARTS_AT,
            NOW
        );

        assertThat(attendance.checkedInAt()).isEqualTo(STARTS_AT);
    }

    @Test
    @DisplayName(
        "createOfflineSync clamps a scan later than the server clock to the server receipt time"
    )
    void createOfflineSync_clampsScannedAtToServerNow() {
        Instant scannedAt = Instant.parse("2026-11-15T23:00:00Z");

        Attendance attendance = Attendance.createOfflineSync(
            WORKSHOP_ID,
            REGISTRATION_ID,
            STAFF_ID,
            SYNC_ID,
            DEVICE_ID,
            scannedAt,
            STARTS_AT,
            NOW
        );

        assertThat(attendance.checkedInAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName(
        "createOfflineSync applies no lower bound when the workshop has no start time"
    )
    void createOfflineSync_withoutWorkshopStart_keepsScannedAt() {
        Instant scannedAt = Instant.parse("2026-11-15T08:00:00Z");

        Attendance attendance = Attendance.createOfflineSync(
            WORKSHOP_ID,
            REGISTRATION_ID,
            STAFF_ID,
            SYNC_ID,
            DEVICE_ID,
            scannedAt,
            null,
            NOW
        );

        assertThat(attendance.checkedInAt()).isEqualTo(scannedAt);
    }

    @Test
    @DisplayName(
        "createOfflineSync never fabricates future attendance when the workshop starts after the server clock"
    )
    void createOfflineSync_workshopStartAfterNow_fallsBackToNow() {
        Instant futureStart = NOW.plusSeconds(3600);

        Attendance attendance = Attendance.createOfflineSync(
            WORKSHOP_ID,
            REGISTRATION_ID,
            STAFF_ID,
            SYNC_ID,
            DEVICE_ID,
            null,
            futureStart,
            NOW
        );

        assertThat(attendance.checkedInAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName(
        "createOfflineSync falls back to the server receipt time when scannedAt is missing"
    )
    void createOfflineSync_withoutScannedAt_usesServerNow() {
        Attendance attendance = Attendance.createOfflineSync(
            WORKSHOP_ID,
            REGISTRATION_ID,
            STAFF_ID,
            SYNC_ID,
            DEVICE_ID,
            null,
            STARTS_AT,
            NOW
        );

        assertThat(attendance.checkedInAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName(
        "createOfflineSync rejects a missing workshop, registration, staff user or sync identifier"
    )
    void createOfflineSync_rejectsMissingIdentifiers() {
        Instant scannedAt = Instant.parse("2026-11-15T09:30:00Z");

        assertThatThrownBy(() ->
            Attendance.createOfflineSync(
                null,
                REGISTRATION_ID,
                STAFF_ID,
                SYNC_ID,
                DEVICE_ID,
                scannedAt,
                STARTS_AT,
                NOW
            )
        ).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() ->
            Attendance.createOfflineSync(
                WORKSHOP_ID,
                null,
                STAFF_ID,
                SYNC_ID,
                DEVICE_ID,
                scannedAt,
                STARTS_AT,
                NOW
            )
        ).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() ->
            Attendance.createOfflineSync(
                WORKSHOP_ID,
                REGISTRATION_ID,
                null,
                SYNC_ID,
                DEVICE_ID,
                scannedAt,
                STARTS_AT,
                NOW
            )
        ).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() ->
            Attendance.createOfflineSync(
                WORKSHOP_ID,
                REGISTRATION_ID,
                STAFF_ID,
                null,
                DEVICE_ID,
                scannedAt,
                STARTS_AT,
                NOW
            )
        ).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("createOfflineSync keeps a null deviceId as null")
    void createOfflineSync_withoutDeviceId_keepsNull() {
        Attendance attendance = Attendance.createOfflineSync(
            WORKSHOP_ID,
            REGISTRATION_ID,
            STAFF_ID,
            SYNC_ID,
            null,
            NOW,
            STARTS_AT,
            NOW
        );

        assertThat(attendance.deviceId()).isNull();
    }
}
