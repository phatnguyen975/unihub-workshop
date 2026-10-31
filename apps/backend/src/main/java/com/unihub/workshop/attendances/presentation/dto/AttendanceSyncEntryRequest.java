package com.unihub.workshop.attendances.presentation.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One queued scan inside an attendance sync batch.
 *
 * @param syncId         device-generated identifier of this single scan
 * @param registrationId registration the scanned QR code resolves to
 * @param scannedAt      device clock time at which the scan occurred
 */
public record AttendanceSyncEntryRequest(
    UUID syncId,
    UUID registrationId,
    Instant scannedAt
) {}
