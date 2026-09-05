package com.unihub.workshop.iam.application.ports.in;

public interface TokenResult {
    String accessToken();
    String refreshToken();
    String tokenType();
    long expiresIn();
}
