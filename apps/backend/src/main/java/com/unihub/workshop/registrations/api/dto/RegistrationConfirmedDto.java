package com.unihub.workshop.registrations.api.dto;

import java.time.Instant;
import java.util.UUID;

public record RegistrationConfirmedDto(
    UUID registrationId,
    UUID workshopId,
    UUID studentUserId,
    String qrPayload,
    Instant qrIssuedAt
) {}
