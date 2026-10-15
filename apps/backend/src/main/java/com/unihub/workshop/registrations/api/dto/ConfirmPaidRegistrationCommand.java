package com.unihub.workshop.registrations.api.dto;

import java.util.UUID;

/**
 * Command carrying parameters required to confirm a paid workshop registration upon payment success.
 */
public record ConfirmPaidRegistrationCommand(
    UUID registrationId,
    String providerPaymentId
) {}
