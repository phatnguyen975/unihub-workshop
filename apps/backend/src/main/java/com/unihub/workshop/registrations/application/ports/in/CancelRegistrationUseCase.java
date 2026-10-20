package com.unihub.workshop.registrations.application.ports.in;

import com.unihub.workshop.registrations.domain.model.CancelledRegistrationResult;

public interface CancelRegistrationUseCase {
    /**
     * Cancels an existing CONFIRMED registration, restoring capacity and returning refund info if applicable.
     *
     * @param command cancellation parameters
     * @return result containing the updated registration and refund details
     */
    CancelledRegistrationResult cancel(CancelRegistrationCommand command);
}
