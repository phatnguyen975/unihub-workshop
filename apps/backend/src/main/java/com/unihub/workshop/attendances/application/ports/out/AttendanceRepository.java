package com.unihub.workshop.attendances.application.ports.out;

import com.unihub.workshop.attendances.domain.model.Attendance;
import java.util.Collection;
import java.util.Map;
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

    /**
     * Loads the existing attendance records of one workshop for a set of
     * registrations with a single bulk read.
     *
     * <p>The workshop identifier is part of the predicate, never a filter applied
     * afterwards, so a record of another workshop can never be observed here.
     *
     * @param workshopId workshop the records must belong to
     * @param registrationIds registrations to look up
     * @return every matching record keyed by its registration identifier; empty when
     *     the workshop or the registration set is absent
     */
    Map<UUID, Attendance> findByWorkshopIdAndRegistrationIds(
        UUID workshopId,
        Collection<UUID> registrationIds
    );

    /**
     * Counts the attendance records of one workshop that are still valid.
     *
     * <p>Used to build the live attendance counter snapshot after a committed
     * check-in. The count is scoped by workshop and restricted to {@code VALID}
     * rows, so records invalidated by an organizer are never reported as
     * present.
     *
     * @param workshopId workshop whose valid attendance records are counted
     * @return number of {@code VALID} records for the workshop; {@code 0} when the
     *     workshop identifier is absent
     */
    long countValidByWorkshopId(UUID workshopId);
}
