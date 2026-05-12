package com.unihub.workshop.iam.application.dto;

public record VerifyOtpResponse(
        String email,
        String status,
        String resetToken,
        Integer resetTokenExpiresInSeconds) {
}
