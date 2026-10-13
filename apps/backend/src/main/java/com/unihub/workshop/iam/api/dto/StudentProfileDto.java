package com.unihub.workshop.iam.api.dto;

import java.util.UUID;

/**
 * Immutable DTO representing a student user profile for cross-module exchange.
 */
public record StudentProfileDto(
    UUID userId,
    UUID studentRecordId,
    String fullName,
    String email
) {}
