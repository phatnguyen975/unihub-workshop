package com.unihub.workshop.iam.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.unihub.workshop.iam.application.ports.in.AuthSessionResult;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserProfileResponse user) {

    public static LoginResponse from(AuthSessionResult result) {
        if (result == null) {
            return null;
        }
        return new LoginResponse(
                result.accessToken(),
                result.refreshToken(),
                result.tokenType(),
                result.expiresIn(),
                UserProfileResponse.from(result.user()));
    }
}
