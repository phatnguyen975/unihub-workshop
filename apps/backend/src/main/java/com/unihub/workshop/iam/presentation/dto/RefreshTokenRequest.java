package com.unihub.workshop.iam.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record RefreshTokenRequest(
    @NotBlank(message = "refreshToken must not be blank") String refreshToken
) {}
