package com.unihub.workshop.workshops.application.ports.out;

import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.workshops.domain.model.WorkshopAssignment;
import java.util.UUID;

public interface WorkshopAssignmentRepository {
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
     * Saves a workshop staff assignment.
     */
    WorkshopAssignment save(WorkshopAssignment assignment);
}
