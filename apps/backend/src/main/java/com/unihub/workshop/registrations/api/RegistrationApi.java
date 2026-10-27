package com.unihub.workshop.registrations.api;

import com.unihub.workshop.registrations.api.dto.ConfirmPaidRegistrationCommand;
import com.unihub.workshop.registrations.api.dto.FailPaidRegistrationCommand;
import com.unihub.workshop.registrations.api.dto.RegistrationConfirmedDto;
import com.unihub.workshop.registrations.api.dto.RegistrationPaymentContextDto;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

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

    /**
     * Resolves the payment-facing context of several registrations in a single read.
     *
     * <p>Callers use this to enrich a page of payment records without performing
     * per-record lookups. Unknown identifiers are simply absent from the result.
     *
     * @param registrationIds registration identifiers to resolve
     * @return registration context keyed by registration identifier; empty when no ids are supplied
     */
    Map<UUID, RegistrationPaymentContextDto> getPaymentContexts(
        Collection<UUID> registrationIds
    );
}
