package com.unihub.workshop.iam.application.dto;

public record RegisterResponse(
        String email,
        String status,
        boolean verificationRequired) {
}
