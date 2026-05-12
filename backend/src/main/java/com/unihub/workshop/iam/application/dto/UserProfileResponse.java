package com.unihub.workshop.iam.application.dto;

public record UserProfileResponse(
                String id,
                String email,
                String fullName,
                String phone,
                String role,
                String status,
                String organizationId,
                String studentId,
                boolean isTemporaryPassword) {
}
