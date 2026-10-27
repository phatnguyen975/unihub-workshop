package com.unihub.workshop.payments.domain.model;

import java.util.UUID;

/**
 * Read model combining one payment with the registration context a caller may see.
 *
 * <p>The registration context is supplied by the registrations module; this record never
 * carries registration or workshop internals beyond what the response contract exposes.
 *
 * @param payment the payment record owned by this module
 * @param workshopId workshop targeted by the linked registration, or null when unavailable
 * @param registrationStatus current registration status name, or null when unavailable
 */
public record PaymentHistoryItem(
    Payment payment,
    UUID workshopId,
    String registrationStatus
) {}
