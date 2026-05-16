package com.unihub.workshop.iam.application.dto;

public record ProvisionStaffResponse(
        String id,
        String email,
        String fullName,
        String role,
        String organizationId,
        String lastLoginAt,
        boolean isTemporaryPassword) {
}
