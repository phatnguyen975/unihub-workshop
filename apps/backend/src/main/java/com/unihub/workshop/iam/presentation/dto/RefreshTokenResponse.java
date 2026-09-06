package com.unihub.workshop.iam.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.unihub.workshop.iam.application.ports.in.TokenResult;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RefreshTokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn) {

    public static RefreshTokenResponse from(TokenResult result) {
        if (result == null) {
            return null;
        }
        return new RefreshTokenResponse(
                result.accessToken(),
                result.refreshToken(),
                result.tokenType(),
                result.expiresIn());
    }
}
