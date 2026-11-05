package com.unihub.workshop.attendances.presentation.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * One queued scan inside an attendance sync batch.
 *
 * <p>Identifiers and the timestamp are received as strings so that a
 * syntactically valid JSON body carrying a semantically invalid value is
 * rejected with {@code 422 Unprocessable Content} instead of being masked as a
 * {@code 400 Bad Request} deserialization failure. Format checking happens in
 * {@link com.unihub.workshop.attendances.presentation.validation.AttendanceSyncBatchValidator}.
 *
 * @param syncId         device-generated identifier of this single scan (UUID)
 * @param registrationId registration the scanned QR code resolves to (UUID)
 * @param scannedAt      device clock time at which the scan occurred (ISO-8601 UTC)
 */
public record AttendanceSyncEntryRequest(
    @NotBlank String syncId,
    @NotBlank String registrationId,
    @NotBlank String scannedAt
) {}
