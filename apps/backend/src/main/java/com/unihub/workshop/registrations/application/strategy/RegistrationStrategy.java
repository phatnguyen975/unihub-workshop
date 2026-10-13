package com.unihub.workshop.registrations.application.strategy;

import com.unihub.workshop.iam.api.dto.StudentProfileDto;
import com.unihub.workshop.registrations.domain.model.Registration;
import com.unihub.workshop.workshops.api.dto.WorkshopRegistrationDetailsDto;
import java.util.UUID;

/**
 * Strategy interface for workshop registration workflows (free vs paid).
 */
public interface RegistrationStrategy {
    /**
     * Determines if this strategy supports the given workshop.
     *
     * @param workshop workshop details
     * @return true if supported, false otherwise
     */
    boolean supports(WorkshopRegistrationDetailsDto workshop);

    /**
     * Executes the registration pipeline for the supported workshop type.
     *
     * @param student verified student profile
     * @param workshop eligible workshop details
     * @param idempotencyKeyId idempotency key reference
     * @return created registration aggregate
     */
    Registration register(
        StudentProfileDto student,
        WorkshopRegistrationDetailsDto workshop,
        UUID idempotencyKeyId
    );
}
