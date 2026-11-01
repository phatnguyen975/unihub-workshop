package com.unihub.workshop.attendances.presentation.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.unihub.workshop.attendances.application.ports.in.SyncAttendancesCommand;
import com.unihub.workshop.attendances.domain.model.AttendanceClassification;
import com.unihub.workshop.attendances.domain.model.AttendanceOutcome;
import com.unihub.workshop.attendances.domain.model.SyncEntry;
import com.unihub.workshop.attendances.presentation.dto.AttendanceSyncEntryRequest;
import com.unihub.workshop.attendances.presentation.dto.AttendanceSyncOutcomeResponse;
import com.unihub.workshop.attendances.presentation.dto.AttendanceSyncRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class AttendancePresentationMapperTest {

    private static final UUID WORKSHOP_ID = UUID.randomUUID();
    private static final UUID STAFF_ID = UUID.randomUUID();
    private static final String IDEMPOTENCY_KEY = UUID.randomUUID().toString();
    private static final String DEVICE_ID = "device-abc-123";
    private static final String SCANNED_AT = "2026-11-15T09:30:00Z";

    private AttendancePresentationMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = Mappers.getMapper(AttendancePresentationMapper.class);
    }

    @Test
    @DisplayName("toSyncEntry parses the string-typed identifiers and instant")
    void toSyncEntry_parsesStringFields() {
        UUID syncId = UUID.randomUUID();
        UUID registrationId = UUID.randomUUID();

        SyncEntry entry = mapper.toSyncEntry(
            new AttendanceSyncEntryRequest(
                syncId.toString(),
                registrationId.toString(),
                SCANNED_AT
            )
        );

        assertThat(entry.syncId()).isEqualTo(syncId);
        assertThat(entry.registrationId()).isEqualTo(registrationId);
        assertThat(entry.scannedAt()).isEqualTo(
            Instant.parse("2026-11-15T09:30:00Z")
        );
    }

    @Test
    @DisplayName(
        "toCommand maps the envelope, the normalized key and every batch entry in order"
    )
    void toCommand_mapsEnvelopeAndEntries() {
        UUID firstSync = UUID.randomUUID();
        UUID secondSync = UUID.randomUUID();
        AttendanceSyncRequest request = new AttendanceSyncRequest(
            DEVICE_ID,
            List.of(
                new AttendanceSyncEntryRequest(
                    firstSync.toString(),
                    UUID.randomUUID().toString(),
                    SCANNED_AT
                ),
                new AttendanceSyncEntryRequest(
                    secondSync.toString(),
                    UUID.randomUUID().toString(),
                    SCANNED_AT
                )
            )
        );

        SyncAttendancesCommand command = mapper.toCommand(
            request,
            WORKSHOP_ID,
            STAFF_ID,
            IDEMPOTENCY_KEY
        );

        assertThat(command.workshopId()).isEqualTo(WORKSHOP_ID);
        assertThat(command.staffUserId()).isEqualTo(STAFF_ID);
        assertThat(command.idempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
        assertThat(command.deviceId()).isEqualTo(DEVICE_ID);
        assertThat(command.entries())
            .extracting(SyncEntry::syncId)
            .containsExactly(firstSync, secondSync);
    }

    @Test
    @DisplayName("toCommand keeps an omitted device id as null")
    void toCommand_omittedDeviceId_mapsNull() {
        AttendanceSyncRequest request = new AttendanceSyncRequest(
            null,
            List.of(
                new AttendanceSyncEntryRequest(
                    UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(),
                    SCANNED_AT
                )
            )
        );

        SyncAttendancesCommand command = mapper.toCommand(
            request,
            WORKSHOP_ID,
            STAFF_ID,
            IDEMPOTENCY_KEY
        );

        assertThat(command.deviceId()).isNull();
    }

    @Test
    @DisplayName("toResponse maps the classification to its wire status name")
    void toResponse_mapsClassificationName() {
        UUID syncId = UUID.randomUUID();

        AttendanceSyncOutcomeResponse response = mapper.toResponse(
            new AttendanceOutcome(syncId, AttendanceClassification.ALREADY_USED)
        );

        assertThat(response.syncId()).isEqualTo(syncId);
        assertThat(response.status()).isEqualTo("ALREADY_USED");
    }

    @Test
    @DisplayName("toResponses preserves the submitted order")
    void toResponses_preservesOrder() {
        UUID firstSync = UUID.randomUUID();
        UUID secondSync = UUID.randomUUID();

        List<AttendanceSyncOutcomeResponse> responses = mapper.toResponses(
            List.of(
                new AttendanceOutcome(
                    firstSync,
                    AttendanceClassification.SUCCESS
                ),
                new AttendanceOutcome(
                    secondSync,
                    AttendanceClassification.INVALID_TICKET
                )
            )
        );

        assertThat(responses)
            .extracting(AttendanceSyncOutcomeResponse::syncId)
            .containsExactly(firstSync, secondSync);
        assertThat(responses)
            .extracting(AttendanceSyncOutcomeResponse::status)
            .containsExactly("SUCCESS", "INVALID_TICKET");
    }
}
