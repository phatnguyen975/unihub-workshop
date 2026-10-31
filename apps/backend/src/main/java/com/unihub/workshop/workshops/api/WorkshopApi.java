package com.unihub.workshop.workshops.api;

import com.unihub.workshop.workshops.api.dto.WorkshopRegistrationDetailsDto;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Public synchronous facade interface for Workshop queries required by peer modules.
 */
public interface WorkshopApi {
    /**
     * Retrieves registration details for a single workshop by ID.
     *
     * @param workshopId target workshop ID
     * @return optional containing WorkshopRegistrationDetailsDto if workshop exists, empty otherwise
     */
    Optional<WorkshopRegistrationDetailsDto> getWorkshopForRegistration(
        UUID workshopId
    );

    /**
     * Resolves multiple workshop registration details in batch by their IDs.
     *
     * @param workshopIds collection of workshop IDs to resolve
     * @return map of workshopId to WorkshopRegistrationDetailsDto for all matching workshops found
     */
    Map<UUID, WorkshopRegistrationDetailsDto> getWorkshopsForRegistrations(
        Collection<UUID> workshopIds
    );

    /**
     * Checks whether a staff user is explicitly assigned to a workshop.
     *
     * <p>Assignment is the only authority for staff check-in; organization membership
     * is deliberately not considered.
     *
     * @param workshopId target workshop ID
     * @param staffUserId staff user ID
     * @return true when an assignment record exists for the pair, false otherwise
     */
    boolean isStaffAssignedToWorkshop(UUID workshopId, UUID staffUserId);
}
