package com.unihub.workshop.registrations.api.dto;

import java.util.UUID;

/**
 * Command carrying parameters required to fail a paid workshop registration upon payment failure.
 */
public record FailPaidRegistrationCommand(
    UUID registrationId,
    String failureReason
) {}
