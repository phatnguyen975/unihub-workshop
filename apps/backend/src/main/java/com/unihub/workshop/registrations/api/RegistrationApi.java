package com.unihub.workshop.registrations.api;

import com.unihub.workshop.registrations.api.dto.ConfirmPaidRegistrationCommand;
import com.unihub.workshop.registrations.api.dto.FailPaidRegistrationCommand;
import com.unihub.workshop.registrations.api.dto.RegistrationConfirmedDto;

/**
 * Public synchronous facade interface for Registrations management required by peer modules (e.g. payments).
 */
public interface RegistrationApi {
    /**
     * Confirms a PENDING paid registration:
     * - Transitions registration from PENDING to CONFIRMED
     * - Generates and sets signed QR ticket fields atomically
     * - Publishes WorkshopRegisteredEvent to Spring Modulith outbox within the transaction
     *
     * @param command confirmation command containing registrationId and providerPaymentId
     * @return details of the confirmed registration, or null if registration not found or not in PENDING state
     */
    RegistrationConfirmedDto confirmPaidRegistration(
        ConfirmPaidRegistrationCommand command
    );

    /**
     * Fails a PENDING paid registration:
     * - Transitions registration from PENDING to FAILED
     * - Releases reserved workshop capacity in Redis (INCR)
     * - Does NOT publish WorkshopRegisteredEvent
     *
     * @param command failure command containing registrationId and failureReason
     * @return true if successfully transitioned to FAILED, false if registration not found or not in PENDING state
     */
    boolean failPaidRegistration(FailPaidRegistrationCommand command);
}
