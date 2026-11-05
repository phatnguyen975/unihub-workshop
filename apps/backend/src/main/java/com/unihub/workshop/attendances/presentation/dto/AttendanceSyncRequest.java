package com.unihub.workshop.attendances.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Request body of the attendance sync endpoint.
 *
 * @param deviceId optional identifier of the device that produced the batch
 * @param entries  queued scans, in the order outcomes must be returned
 */
public record AttendanceSyncRequest(
    @Size(max = 100) String deviceId,
    @NotEmpty
    @Size(max = 500)
    List<@NotNull @Valid AttendanceSyncEntryRequest> entries
) {}
