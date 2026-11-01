package com.unihub.workshop.attendances.presentation.validation;

import com.unihub.workshop.attendances.presentation.dto.AttendanceSyncEntryRequest;
import com.unihub.workshop.attendances.presentation.dto.AttendanceSyncRequest;
import com.unihub.workshop.shared.exception.ValidationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Validates an attendance sync batch before any business work is attempted.
 *
 * <p>The validation runs programmatically, after the protocol-level header and
 * path checks, so a request missing its {@code Idempotency-Key} is always
 * answered with {@code 400 Bad Request} and never consumes an idempotency
 * claim. Every failure is reported as a {@link ValidationException}, which the
 * global exception handler renders as {@code 422 Unprocessable Content} with a
 * field-level {@code validationErrors} list.
 *
 * <p>Three passes run in order: the declarative bean constraints on the DTO,
 * duplicate detection for {@code syncId} and {@code registrationId}, and format
 * parsing of the string-typed identifiers and timestamp.
 */
@Component
@RequiredArgsConstructor
public class AttendanceSyncBatchValidator {

    private static final String ENTRIES_FIELD = "entries";
    private static final String SYNC_ID_FIELD = "syncId";
    private static final String REGISTRATION_ID_FIELD = "registrationId";
    private static final String SCANNED_AT_FIELD = "scannedAt";
    private static final String UNIQUE_MESSAGE =
        "must be unique within the batch";
    private static final String UUID_MESSAGE = "must be a valid UUID";
    private static final String INSTANT_MESSAGE =
        "must be an ISO-8601 UTC instant";
    private static final String ABSENT_BATCH_MESSAGE =
        "Attendance sync batch must contain between 1 and 500 entries";

    private final Validator validator;

    /**
     * Rejects the batch when any boundary rule is broken.
     *
     * @param request parsed request body
     * @throws ValidationException when at least one rule is broken
     */
    public void validate(AttendanceSyncRequest request) {
        if (request == null) {
            throw new ValidationException(ENTRIES_FIELD, ABSENT_BATCH_MESSAGE);
        }
        requireBeanConstraints(request);

        List<AttendanceSyncEntryRequest> entries = request.entries();
        requireDistinctIdentifiers(entries);
        requireParsableValues(entries);
    }

    private void requireBeanConstraints(AttendanceSyncRequest request) {
        Set<ConstraintViolation<AttendanceSyncRequest>> violations =
            validator.validate(request);
        if (violations.isEmpty()) {
            return;
        }
        List<String> errors = violations
            .stream()
            .map(AttendanceSyncBatchValidator::formatViolation)
            .sorted()
            .toList();
        throw new ValidationException(errors.get(0), errors);
    }

    private static String formatViolation(
        ConstraintViolation<AttendanceSyncRequest> violation
    ) {
        return violation.getPropertyPath() + ": " + violation.getMessage();
    }

    private static void requireDistinctIdentifiers(
        List<AttendanceSyncEntryRequest> entries
    ) {
        Map<String, Integer> firstSyncIdIndexes = new HashMap<>();
        Map<String, Integer> firstRegistrationIdIndexes = new HashMap<>();
        List<String> errors = new ArrayList<>();

        for (int index = 0; index < entries.size(); index++) {
            AttendanceSyncEntryRequest entry = entries.get(index);
            requireDistinct(
                SYNC_ID_FIELD,
                entry.syncId(),
                index,
                firstSyncIdIndexes,
                errors
            );
            requireDistinct(
                REGISTRATION_ID_FIELD,
                entry.registrationId(),
                index,
                firstRegistrationIdIndexes,
                errors
            );
        }

        requireNoErrors(errors);
    }

    private static void requireDistinct(
        String field,
        String value,
        int index,
        Map<String, Integer> firstIndexes,
        List<String> errors
    ) {
        if (value == null) {
            return;
        }
        Integer firstIndex = firstIndexes.putIfAbsent(value, index);
        if (firstIndex != null) {
            errors.add(
                entryField(index, field) +
                    ": " +
                    UNIQUE_MESSAGE +
                    " (first seen at " +
                    entryField(firstIndex, field) +
                    ")"
            );
        }
    }

    private static void requireParsableValues(
        List<AttendanceSyncEntryRequest> entries
    ) {
        List<String> errors = new ArrayList<>();

        for (int index = 0; index < entries.size(); index++) {
            AttendanceSyncEntryRequest entry = entries.get(index);
            requireUuid(entry.syncId(), index, SYNC_ID_FIELD, errors);
            requireUuid(
                entry.registrationId(),
                index,
                REGISTRATION_ID_FIELD,
                errors
            );
            requireInstant(entry.scannedAt(), index, errors);
        }

        requireNoErrors(errors);
    }

    private static void requireUuid(
        String value,
        int index,
        String field,
        List<String> errors
    ) {
        if (value == null) {
            return;
        }
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            errors.add(entryField(index, field) + ": " + UUID_MESSAGE);
        }
    }

    private static void requireInstant(
        String value,
        int index,
        List<String> errors
    ) {
        if (value == null) {
            return;
        }
        try {
            Instant.parse(value);
        } catch (DateTimeParseException e) {
            errors.add(
                entryField(index, SCANNED_AT_FIELD) + ": " + INSTANT_MESSAGE
            );
        }
    }

    private static void requireNoErrors(List<String> errors) {
        if (!errors.isEmpty()) {
            throw new ValidationException(errors.get(0), errors);
        }
    }

    private static String entryField(int index, String field) {
        return ENTRIES_FIELD + "[" + index + "]." + field;
    }
}
