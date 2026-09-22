package com.unihub.workshop.iam.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresIn,
    UserProfileResponse user
) {}
