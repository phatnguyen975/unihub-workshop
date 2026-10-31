package com.unihub.workshop.attendances.presentation.dto;

import java.util.List;

/**
 * Request body of the attendance sync endpoint.
 *
 * @param deviceId optional identifier of the device that produced the batch
 * @param entries  queued scans, in the order outcomes must be returned
 */
public record AttendanceSyncRequest(
    String deviceId,
    List<AttendanceSyncEntryRequest> entries
) {}
