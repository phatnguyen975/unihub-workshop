package com.unihub.workshop.attendances.application.ports.out;

import com.unihub.workshop.attendances.domain.model.Attendance;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Outbound port for attendance persistence.
 */
public interface AttendanceRepository {
    /**
     * Persists every supplied attendance record with a single bulk write.
     *
     * <p>The database's uniqueness constraints are the final arbiter: a record that
     * conflicts with an existing one is silently skipped rather than raising.
     *
     * @param attendances records to persist
     * @return the {@code syncId} of every record that was actually persisted
     */
    Set<UUID> insertAll(Collection<Attendance> attendances);
}
