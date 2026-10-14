package com.unihub.workshop.registrations.application.ports.out;

import com.unihub.workshop.registrations.domain.model.Registration;
import com.unihub.workshop.registrations.domain.model.RegistrationStatus;
import com.unihub.workshop.shared.pagination.CursorPage;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RegistrationRepository {
    /**
     * Persists a registration aggregate.
     *
     * @param registration aggregate to save
     * @return persisted registration aggregate
     */
    Registration save(Registration registration);

    /**
     * Finds a registration by its unique ID.
     *
     * @param id registration ID
     * @return optional containing Registration if found, empty otherwise
     */
    Optional<Registration> findById(UUID id);

    /**
     * Checks if an active (PENDING or CONFIRMED) registration exists for the student on the target workshop.
     *
     * @param studentUserId student user ID
     * @param workshopId workshop ID
     * @return true if an active registration already exists, false otherwise
     */
    boolean existsActiveRegistration(UUID studentUserId, UUID workshopId);

    /**
     * Checks if the student has any active registration for a workshop whose schedule overlaps with the given time window.
     *
     * @param studentUserId student user ID
     * @param startsAt new workshop start time
     * @param endsAt new workshop end time
     * @return true if an overlapping active registration exists, false otherwise
     */
    boolean hasOverlappingActiveRegistration(
        UUID studentUserId,
        Instant startsAt,
        Instant endsAt
    );

    /**
     * Counts active (PENDING or CONFIRMED) registrations for the given workshop.
     *
     * @param workshopId workshop ID
     * @return active registration count
     */
    long countActiveRegistrations(UUID workshopId);

    /**
     * Finds keyset cursor-paginated registrations belonging to the student.
     *
     * @param studentUserId student user ID
     * @param status optional status filter
     * @param cursor optional keyset cursor
     * @param size page window size
     * @return CursorPage of Registration items
     */
    CursorPage<Registration> findByStudentUserId(
        UUID studentUserId,
        RegistrationStatus status,
        String cursor,
        int size
    );
}
