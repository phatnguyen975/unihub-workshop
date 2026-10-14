package com.unihub.workshop.registrations.application.ports.in;

import com.unihub.workshop.registrations.domain.model.RegistrationDetail;
import java.util.UUID;

public interface GetRegistrationDetailUseCase {
    /**
     * Retrieves the registration detail for an authenticated student, verifying ownership and enriching workshop details.
     *
     * @param studentUserId authenticated student user ID
     * @param registrationId target registration ID
     * @return enriched RegistrationDetail domain projection
     * @throws com.unihub.workshop.shared.exception.NotFoundException if registration does not exist
     * @throws com.unihub.workshop.shared.exception.ForbiddenException if caller does not own the registration
     */
    RegistrationDetail getRegistrationDetail(
        UUID studentUserId,
        UUID registrationId
    );
}
