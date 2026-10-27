package com.unihub.workshop.payments.application.ports.in;

import java.util.UUID;

/**
 * Query describing one organization-scoped page of payment operations.
 *
 * <p>Filter values stay raw so this layer owns their validation and can report the
 * contract's error responses instead of relying on framework-level parsing.
 *
 * @param organizationId caller's organization scope (mandatory)
 * @param workshopId optional workshop filter, as supplied by the caller
 * @param status optional payment status filter, matched case-insensitively
 * @param cursor optional opaque keyset cursor issued for the same filter set
 * @param size optional requested page size, as supplied by the caller
 */
public record GetOrganizerPaymentHistoryQuery(
    UUID organizationId,
    String workshopId,
    String status,
    String cursor,
    String size
) {
    public GetOrganizerPaymentHistoryQuery {
        if (organizationId == null) {
            throw new IllegalArgumentException(
                "organizationId must not be null"
            );
        }
    }
}
