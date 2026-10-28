package com.unihub.workshop.registrations.application.ports.out;

import com.unihub.workshop.registrations.domain.model.Registration;
import com.unihub.workshop.registrations.domain.model.RegistrationStatus;
import com.unihub.workshop.registrations.domain.model.WorkshopRegistrationRawCounts;
import com.unihub.workshop.shared.pagination.CursorPage;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RegistrationRepository {
    /**
     * Finds expired registrations in PENDING status ordered by oldest expiration first.
     *
     * @param now current cutoff instant
     * @param batchSize maximum number of items to retrieve
     * @return list of expired PENDING registrations
     */
    List<Registration> findExpiredPendingRegistrations(
        Instant now,
        int batchSize
    );

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

    /**
     * Finds registrations by their identifiers in a single read.
     *
     * @param ids registration identifiers to resolve
     * @return found registrations, in unspecified order; unknown identifiers are absent
     */
    List<Registration> findAllByIds(Collection<UUID> ids);

    /**
     * Atomically transitions a registration from CONFIRMED to CANCELED status.
     *
     * @param id registration ID
     * @param updatedBy actor ID performing the cancellation
     * @param now timestamp of cancellation
     * @return true if updated, false if registration was not found or was not in CONFIRMED status
     */
    boolean cancelRegistration(UUID id, UUID updatedBy, Instant now);

    /**
     * Retrieves aggregated raw seat counts (CONFIRMED, PENDING) and latest registration update timestamp.
     *
     * @param workshopId workshop ID
     * @return raw counts snapshot
     */
    WorkshopRegistrationRawCounts getRegistrationRawCounts(UUID workshopId);
}
