package com.unihub.workshop.attendances.domain.model;

/**
 * Per-entry verdict reported back to the syncing device.
 *
 * <p>These are exactly the values the mobile sync queue accepts, so the client can
 * persist the outcome without translation.
 */
public enum AttendanceClassification {
    SUCCESS,
    ALREADY_USED,
    INVALID_TICKET,
}
