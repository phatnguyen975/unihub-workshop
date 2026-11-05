package com.unihub.workshop.attendances.presentation.dto;

import java.util.UUID;

/**
 * Reconciliation verdict for a single submitted scan.
 *
 * @param syncId identifier echoed from the submitted entry
 * @param status one of {@code SUCCESS}, {@code ALREADY_USED}, {@code INVALID_TICKET}
 */
public record AttendanceSyncOutcomeResponse(UUID syncId, String status) {}
