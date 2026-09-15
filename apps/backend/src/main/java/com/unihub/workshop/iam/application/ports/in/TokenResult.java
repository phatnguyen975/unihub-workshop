package com.unihub.workshop.iam.application.ports.in;

public interface TokenResult {
    String accessToken();
    String refreshToken();
    String tokenType();
    long expiresIn();

    default String getAccessToken() {
        return accessToken();
    }

    default String getRefreshToken() {
        return refreshToken();
    }

    default String getTokenType() {
        return tokenType();
    }

    default long getExpiresIn() {
        return expiresIn();
    }
}
