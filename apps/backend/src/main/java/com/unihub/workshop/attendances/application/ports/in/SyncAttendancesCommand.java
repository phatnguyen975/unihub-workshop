package com.unihub.workshop.attendances.application.ports.in;

import com.unihub.workshop.attendances.domain.model.SyncEntry;
import java.util.List;
import java.util.UUID;

/**
 * Input command for reconciling a batch of offline scans.
 *
 * @param workshopId workshop targeted by the request
 * @param staffUserId caller identified by {@code jwt.sub}
 * @param deviceId optional identifier of the device that produced the batch
 * @param entries submitted scans, in the order outcomes must be returned
 */
public record SyncAttendancesCommand(
    UUID workshopId,
    UUID staffUserId,
    String deviceId,
    List<SyncEntry> entries
) {
    public SyncAttendancesCommand {
        entries = entries != null ? List.copyOf(entries) : null;
    }
}
