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
}
