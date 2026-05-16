package com.unihub.workshop.iam.application.dto;

public record OrganizationStaffResponse(
        String id,
        String email,
        String fullName,
        String role,
        String status,
        String lastLoginAt,
        boolean isTemporaryPassword) {
}
