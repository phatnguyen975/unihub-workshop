package com.unihub.workshop.workshops.application.ports.out;

import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.workshops.domain.model.StaffAssignedWorkshop;
import com.unihub.workshop.workshops.domain.model.WorkshopAssignment;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface WorkshopAssignmentRepository {
    /**
     * Finds paginated workshops assigned to a staff user, sorted by starts_at ASC, id ASC.
     * Excludes CANCELED workshops.
     *
     * @param staffUserId staff user identifier
     * @param cursor keyset cursor string
     * @param size page size
     * @return cursor page of assigned workshops
     */
    CursorPage<StaffAssignedWorkshop> findAssignedWorkshopsByStaffUserId(
        UUID staffUserId,
        String cursor,
        int size
    );

    /**
     * Finds paginated staff assignments for a workshop, sorted by assigned_at DESC, id DESC.
     *
     * @param workshopId workshop identifier
     * @param cursor keyset cursor string
     * @param size page size
     * @return cursor page of assignments
     */
    CursorPage<WorkshopAssignment> findByWorkshopId(
        UUID workshopId,
        String cursor,
        int size
    );

    /**
     * Checks if a staff member is already assigned to a workshop.
     */
    boolean existsByWorkshopIdAndStaffUserId(UUID workshopId, UUID staffUserId);

    /**
     * Deletes a staff assignment by workshop ID and staff user ID.
     */
    void deleteByWorkshopIdAndStaffUserId(UUID workshopId, UUID staffUserId);

    /**
     * Saves a workshop staff assignment.
     */
    WorkshopAssignment save(WorkshopAssignment assignment);

    /**
     * Saves a list of workshop staff assignments atomically.
     */
    List<WorkshopAssignment> saveAll(List<WorkshopAssignment> assignments);

    /**
     * Finds which of the given staff user IDs are already assigned to the given workshop.
     */
    Set<UUID> findAlreadyAssignedUserIds(
        UUID workshopId,
        Collection<UUID> staffUserIds
    );

    /**
     * Finds which of the given staff user IDs in the organization have an active assignment
     * to another non-CANCELED workshop overlapping the target time window.
     */
    Set<UUID> findScheduleConflictingUserIds(
        UUID organizationId,
        UUID targetWorkshopId,
        Instant startsAt,
        Instant endsAt,
        Collection<UUID> staffUserIds
    );

    /**
     * Finds the set of staff user IDs already assigned to the given workshop.
     */
    Set<UUID> findAssignedStaffUserIds(UUID workshopId);

    /**
     * Finds staff user IDs in the organization that have an active assignment to another
     * non-CANCELED workshop overlapping the target time window.
     */
    Set<UUID> findScheduleConflictingStaffUserIds(
        UUID organizationId,
        UUID targetWorkshopId,
        Instant startsAt,
        Instant endsAt
    );

    /**
     * Counts active assignments (in non-CANCELED workshops) for the given staff members.
     */
    Map<UUID, Integer> countActiveAssignmentsForUsers(
        UUID organizationId,
        Collection<UUID> staffUserIds
    );
}
