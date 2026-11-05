package com.unihub.workshop.attendances.presentation.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.unihub.workshop.attendances.presentation.dto.AttendanceSyncEntryRequest;
import com.unihub.workshop.attendances.presentation.dto.AttendanceSyncRequest;
import com.unihub.workshop.shared.exception.ValidationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AttendanceSyncBatchValidatorTest {

    private static final String DEVICE_ID = "device-abc-123";
    private static final String SCANNED_AT = "2026-11-15T09:30:00Z";

    @Test
    void validate_equivalentUuidSpellings_rejectsDuplicates() {
        String id = "abcdefab-cdef-4abc-8def-abcdefabcdef";
        ValidationException syncFailure = expectValidationFailure(
            request(
                entry(id, UUID.randomUUID().toString(), SCANNED_AT),
                entry(
                    id.toUpperCase(Locale.ROOT),
                    UUID.randomUUID().toString(),
                    SCANNED_AT
                )
            )
        );
        assertThat(syncFailure.getValidationErrors()).anyMatch(error ->
            error.contains("syncId")
        );

        ValidationException registrationFailure = expectValidationFailure(
            request(
                entry(UUID.randomUUID().toString(), id, SCANNED_AT),
                entry(
                    UUID.randomUUID().toString(),
                    id.toUpperCase(Locale.ROOT),
                    SCANNED_AT
                )
            )
        );
        assertThat(registrationFailure.getValidationErrors()).anyMatch(error ->
            error.contains("registrationId")
        );
    }

    private AttendanceSyncBatchValidator batchValidator;

    @BeforeEach
    void setUp() {
        Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();
        batchValidator = new AttendanceSyncBatchValidator(validator);
    }

    private static AttendanceSyncEntryRequest entry(
        String syncId,
        String registrationId,
        String scannedAt
    ) {
        return new AttendanceSyncEntryRequest(
            syncId,
            registrationId,
            scannedAt
        );
    }

    private static AttendanceSyncEntryRequest validEntry() {
        return entry(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            SCANNED_AT
        );
    }

    private static AttendanceSyncRequest request(
        AttendanceSyncEntryRequest... entries
    ) {
        return new AttendanceSyncRequest(DEVICE_ID, List.of(entries));
    }

    private ValidationException expectValidationFailure(
        AttendanceSyncRequest request
    ) {
        Throwable thrown = catchThrowable(() ->
            batchValidator.validate(request)
        );
        assertThat(thrown).isInstanceOf(ValidationException.class);
        return (ValidationException) thrown;
    }

    @Test
    @DisplayName("a well-formed batch passes without any violation")
    void validate_wellFormedBatch_passes() {
        batchValidator.validate(
            request(validEntry(), validEntry(), validEntry())
        );
    }

    @Test
    @DisplayName("an omitted device id is accepted")
    void validate_omittedDeviceId_passes() {
        batchValidator.validate(
            new AttendanceSyncRequest(null, List.of(validEntry(), validEntry()))
        );
    }

    @Test
    @DisplayName("an empty batch is rejected with a field-level entries error")
    void validate_emptyBatch_reportsEntries() {
        ValidationException failure = expectValidationFailure(request());

        assertThat(failure.getValidationErrors())
            .singleElement()
            .asString()
            .startsWith("entries:");
    }

    @Test
    @DisplayName("a batch without an entries array is rejected on entries")
    void validate_absentEntries_reportsEntries() {
        ValidationException failure = expectValidationFailure(
            new AttendanceSyncRequest(DEVICE_ID, null)
        );

        assertThat(failure.getValidationErrors())
            .singleElement()
            .asString()
            .startsWith("entries:");
    }

    @Test
    @DisplayName("a null entry is rejected on its index")
    void validate_nullEntry_reportsIndex() {
        ValidationException failure = expectValidationFailure(
            new AttendanceSyncRequest(
                DEVICE_ID,
                Arrays.asList((AttendanceSyncEntryRequest) null)
            )
        );

        assertThat(failure.getValidationErrors())
            .singleElement()
            .asString()
            .startsWith("entries[0]");
    }

    @Test
    @DisplayName(
        "more than 500 entries is rejected with a field-level entries error"
    )
    void validate_oversizedBatch_reportsEntries() {
        List<AttendanceSyncEntryRequest> entries = new ArrayList<>();
        for (int index = 0; index < 501; index++) {
            entries.add(validEntry());
        }

        ValidationException failure = expectValidationFailure(
            new AttendanceSyncRequest(DEVICE_ID, entries)
        );

        assertThat(failure.getValidationErrors())
            .singleElement()
            .asString()
            .startsWith("entries:");
    }

    @Test
    @DisplayName(
        "two entries sharing a sync id are rejected naming the duplicate index"
    )
    void validate_duplicateSyncId_reportsDuplicateIndex() {
        String sharedSyncId = UUID.randomUUID().toString();

        ValidationException failure = expectValidationFailure(
            request(
                entry(sharedSyncId, UUID.randomUUID().toString(), SCANNED_AT),
                entry(
                    UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(),
                    SCANNED_AT
                ),
                entry(sharedSyncId, UUID.randomUUID().toString(), SCANNED_AT)
            )
        );

        assertThat(failure.getValidationErrors())
            .singleElement()
            .asString()
            .startsWith("entries[2].syncId:");
    }

    @Test
    @DisplayName(
        "two entries sharing a registration id are rejected naming the duplicate index"
    )
    void validate_duplicateRegistrationId_reportsDuplicateIndex() {
        String sharedRegistrationId = UUID.randomUUID().toString();

        ValidationException failure = expectValidationFailure(
            request(
                entry(
                    UUID.randomUUID().toString(),
                    sharedRegistrationId,
                    SCANNED_AT
                ),
                entry(
                    UUID.randomUUID().toString(),
                    sharedRegistrationId,
                    SCANNED_AT
                )
            )
        );

        assertThat(failure.getValidationErrors())
            .singleElement()
            .asString()
            .startsWith("entries[1].registrationId:");
    }

    @Test
    @DisplayName("a blank field is rejected before any format parsing")
    void validate_blankField_reportsField() {
        ValidationException failure = expectValidationFailure(
            request(entry("   ", UUID.randomUUID().toString(), SCANNED_AT))
        );

        assertThat(failure.getValidationErrors())
            .singleElement()
            .asString()
            .startsWith("entries[0].syncId:");
    }

    @Test
    @DisplayName("a present-but-invalid UUID is rejected on its field")
    void validate_invalidUuid_reportsField() {
        ValidationException failure = expectValidationFailure(
            request(
                entry("not-a-uuid", UUID.randomUUID().toString(), SCANNED_AT)
            )
        );

        assertThat(failure.getValidationErrors())
            .singleElement()
            .asString()
            .startsWith("entries[0].syncId:")
            .contains("UUID");
    }

    @Test
    @DisplayName(
        "a scanned-at value that is not an ISO-8601 instant is rejected on its field"
    )
    void validate_invalidScannedAt_reportsField() {
        ValidationException failure = expectValidationFailure(
            request(
                entry(
                    UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(),
                    "2026-11-15T09:30:00"
                )
            )
        );

        assertThat(failure.getValidationErrors())
            .singleElement()
            .asString()
            .startsWith("entries[0].scannedAt:");
    }

    @Test
    @DisplayName(
        "a device id longer than 100 characters is rejected on deviceId"
    )
    void validate_oversizedDeviceId_reportsDeviceId() {
        ValidationException failure = expectValidationFailure(
            new AttendanceSyncRequest("d".repeat(101), List.of(validEntry()))
        );

        assertThat(failure.getValidationErrors())
            .singleElement()
            .asString()
            .startsWith("deviceId:");
    }

    @Test
    @DisplayName("every field-level failure is reported together")
    void validate_multipleFormatFailures_reportsAllOfThem() {
        ValidationException failure = expectValidationFailure(
            request(entry("not-a-uuid", "also-not-a-uuid", "not-an-instant"))
        );

        assertThat(failure.getValidationErrors()).hasSize(3);
        assertThat(failure.getValidationErrors())
            .anySatisfy(error ->
                assertThat(error).startsWith("entries[0].syncId:")
            )
            .anySatisfy(error ->
                assertThat(error).startsWith("entries[0].registrationId:")
            )
            .anySatisfy(error ->
                assertThat(error).startsWith("entries[0].scannedAt:")
            );
    }
}
