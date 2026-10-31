package com.unihub.workshop.attendances.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Builder;

/**
 * Attendance aggregate: one check-in of one registration for one workshop.
 *
 * <p>The record is written once by the offline sync flow and never transitioned out
 * of {@link AttendanceStatus#VALID} here; invalidation is owned by organizer
 * correction capabilities.
 *
 * @param id attendance record identifier
 * @param workshopId workshop the attendance belongs to
 * @param registrationId registration that was checked in
 * @param checkedInByUserId staff user who performed the check-in
 * @param syncId device-generated identifier of the scan
 * @param deviceId optional identifier of the scanning device
 * @param checkInMethod channel that produced the record (always {@code OFFLINE_SYNC} here)
 * @param status lifecycle state of the record
 * @param checkedInAt clamped device scan time
 * @param createdAt server receipt time
 * @param updatedAt server time of the last update
 * @param createdBy actor that created the record
 * @param updatedBy actor that last updated the record
 */
@Builder(toBuilder = true)
public record Attendance(
    UUID id,
    UUID workshopId,
    UUID registrationId,
    UUID checkedInByUserId,
    UUID syncId,
    String deviceId,
    String checkInMethod,
    AttendanceStatus status,
    Instant checkedInAt,
    Instant createdAt,
    Instant updatedAt,
    UUID createdBy,
    UUID updatedBy
) {
    /** Check-in channel recorded for every scan reconciled from an offline batch. */
    public static final String CHECK_IN_METHOD_OFFLINE_SYNC = "OFFLINE_SYNC";

    /**
     * Creates a new {@code VALID} attendance record from a reconciled offline scan.
     *
     * <p>{@code checkedInAt} keeps the device's scan time, clamped so that it is never
     * earlier than the workshop start and never later than the server receipt time.
     * When the workshop start is later than the server clock, the server clock wins:
     * the flow never fabricates future attendance.
     *
     * @param workshopId workshop the attendance belongs to
     * @param registrationId registration that was checked in
     * @param checkedInByUserId staff user who performed the check-in
     * @param syncId device-generated identifier of the scan
     * @param deviceId optional scanning device identifier
     * @param scannedAt device clock time of the scan, may be {@code null}
     * @param workshopStartsAt workshop start time, may be {@code null}
     * @param receivedAt server receipt time
     * @return the attendance record to persist
     */
    public static Attendance createOfflineSync(
        UUID workshopId,
        UUID registrationId,
        UUID checkedInByUserId,
        UUID syncId,
        String deviceId,
        Instant scannedAt,
        Instant workshopStartsAt,
        Instant receivedAt
    ) {
        Objects.requireNonNull(workshopId, "workshopId must not be null");
        Objects.requireNonNull(
            registrationId,
            "registrationId must not be null"
        );
        Objects.requireNonNull(
            checkedInByUserId,
            "checkedInByUserId must not be null"
        );
        Objects.requireNonNull(syncId, "syncId must not be null");

        Instant now = receivedAt != null ? receivedAt : Instant.now();
        return Attendance.builder()
            .id(UUID.randomUUID())
            .workshopId(workshopId)
            .registrationId(registrationId)
            .checkedInByUserId(checkedInByUserId)
            .syncId(syncId)
            .deviceId(deviceId)
            .checkInMethod(CHECK_IN_METHOD_OFFLINE_SYNC)
            .status(AttendanceStatus.VALID)
            .checkedInAt(clampScannedAt(scannedAt, workshopStartsAt, now))
            .createdAt(now)
            .updatedAt(now)
            .createdBy(checkedInByUserId)
            .updatedBy(checkedInByUserId)
            .build();
    }

    private static Instant clampScannedAt(
        Instant scannedAt,
        Instant workshopStartsAt,
        Instant now
    ) {
        Instant effective = scannedAt != null ? scannedAt : now;
        if (workshopStartsAt != null && effective.isBefore(workshopStartsAt)) {
            effective = workshopStartsAt;
        }
        if (effective.isAfter(now)) {
            effective = now;
        }
        return effective;
    }
}
