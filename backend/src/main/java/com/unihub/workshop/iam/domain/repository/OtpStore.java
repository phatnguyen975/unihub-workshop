package com.unihub.workshop.iam.domain.repository;

import java.time.Duration;

public interface OtpStore {

    void saveOtp(String email, String otp, Duration ttl);

    String getOtp(String email);

    void deleteOtp(String email);

    boolean isCooldownActive(String email);

    void markCooldown(String email, Duration ttl);

    int incrementFailedAttempts(String email, Duration ttl);

    void resetFailedAttempts(String email);
}
