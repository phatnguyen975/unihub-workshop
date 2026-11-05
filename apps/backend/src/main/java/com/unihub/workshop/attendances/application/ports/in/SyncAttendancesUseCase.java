package com.unihub.workshop.attendances.application.ports.in;

import com.unihub.workshop.attendances.domain.model.AttendanceOutcome;
import java.util.List;

/**
 * Inbound port for reconciling a batch of offline check-in scans.
 */
public interface SyncAttendancesUseCase {
    /**
     * Reconciles every entry of a batch and persists the admissible ones.
     *
     * @param command the batch to reconcile
     * @return one outcome per submitted entry, in the submitted order
     */
    List<AttendanceOutcome> execute(SyncAttendancesCommand command);
}
