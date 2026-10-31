package com.unihub.workshop.registrations.api.dto;

import java.util.UUID;

/**
 * Immutable registration context exposed to peer modules that reconcile check-ins.
 *
 * <p>Contains only the identifiers and the current status a ticket-validation decision
 * needs; every other registration field stays internal to the registrations module.
 *
 * @param registrationId registration identifier
 * @param workshopId workshop the registration targets
 * @param status current registration status name (e.g. {@code CONFIRMED})
 */
public record RegistrationTicketContextDto(
    UUID registrationId,
    UUID workshopId,
    String status
) {}
