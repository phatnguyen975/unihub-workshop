package com.unihub.workshop.registrations.application.ports.in;

import com.unihub.workshop.registrations.domain.model.Registration;
import java.util.UUID;

public interface RegisterWorkshopUseCase {
    /**
     * Executes the registration pipeline for an authenticated student.
     *
     * @param studentUserId authenticated student user ID
     * @param workshopId target workshop ID
     * @param idempotencyKey client-supplied UUID string idempotency key
     * @return confirmed registration aggregate
     */
    Registration register(
        UUID studentUserId,
        UUID workshopId,
        String idempotencyKey
    );
}
