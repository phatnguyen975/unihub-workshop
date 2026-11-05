package com.unihub.workshop.attendances.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A single scan queued on an offline device and submitted for reconciliation.
 *
 * @param syncId device-generated identifier, stable across retries of the same scan
 * @param registrationId registration the scanned QR code resolves to
 * @param scannedAt device clock time at which the scan occurred
 */
public record SyncEntry(UUID syncId, UUID registrationId, Instant scannedAt) {
    public SyncEntry {
        Objects.requireNonNull(syncId, "syncId must not be null");
        Objects.requireNonNull(
            registrationId,
            "registrationId must not be null"
        );
        Objects.requireNonNull(scannedAt, "scannedAt must not be null");
    }
}
