package com.unihub.workshop.iam.application.dto;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
                @NotBlank String refreshToken) {
}
