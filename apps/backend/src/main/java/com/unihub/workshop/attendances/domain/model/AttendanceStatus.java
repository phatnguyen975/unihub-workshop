package com.unihub.workshop.attendances.domain.model;

/**
 * Lifecycle state of a persisted attendance record.
 *
 * <p>Only {@link #VALID} is produced by the offline sync flow. {@link #INVALIDATED}
 * is owned by organizer correction capabilities and is terminal for this flow.
 */
public enum AttendanceStatus {
    VALID,
    INVALIDATED,
}
