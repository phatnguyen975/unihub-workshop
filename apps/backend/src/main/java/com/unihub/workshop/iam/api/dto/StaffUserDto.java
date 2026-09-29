package com.unihub.workshop.iam.api.dto;

import java.util.UUID;

/**
 * Immutable DTO representing a staff user for cross-module exchange.
 */
public record StaffUserDto(
    UUID userId,
    UUID organizationId,
    String role,
    boolean isActive,
    String fullName,
    String email,
    String avatarUrl,
    String phoneNumber
) {}
