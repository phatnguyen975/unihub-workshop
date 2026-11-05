package com.unihub.workshop.attendances.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Reconciliation verdict for a single submitted sync entry.
 *
 * @param syncId device-generated identifier echoed back so the client can match the outcome
 * @param classification verdict for this entry
 */
public record AttendanceOutcome(
    UUID syncId,
    AttendanceClassification classification
) {
    public AttendanceOutcome {
        Objects.requireNonNull(syncId, "syncId must not be null");
        Objects.requireNonNull(
            classification,
            "classification must not be null"
        );
    }
}
