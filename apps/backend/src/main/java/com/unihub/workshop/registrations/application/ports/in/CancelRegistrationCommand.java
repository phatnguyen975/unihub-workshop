package com.unihub.workshop.registrations.application.ports.in;

import java.util.UUID;

/**
 * Command representing a request to cancel a workshop registration.
 *
 * @param registrationId the registration to cancel
 * @param callerUserId the ID of the authenticated caller (jwt.sub)
 * @param callerRole the role of the authenticated caller ("STUDENT" or "ORGANIZER")
 * @param callerOrganizationId the organization ID from the JWT org claim (populated if caller is ORGANIZER)
 * @param idempotencyKey validated Idempotency-Key header value
 * @param reason optional free-text cancellation reason (max 1000 characters)
 */
public record CancelRegistrationCommand(
    UUID registrationId,
    UUID callerUserId,
    String callerRole,
    UUID callerOrganizationId,
    String idempotencyKey,
    String reason
) {}
