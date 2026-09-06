package com.unihub.workshop.iam.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserVerificationTokenTest {

    @Test
    @DisplayName("createActivationOtp initializes token with ACTIVATION_OTP type and correct defaults")
    void createActivationOtpInitializesCorrectly() {
        UUID userId = UUID.randomUUID();
        String tokenHash = "hmac_hash_123456";
        Instant expiresAt = Instant.now().plusSeconds(900);

        UserVerificationToken token = UserVerificationToken.createActivationOtp(userId, tokenHash, expiresAt);

        assertThat(token.id()).isNotNull();
        assertThat(token.userId()).isEqualTo(userId);
        assertThat(token.tokenType()).isEqualTo(UserVerificationToken.TYPE_ACTIVATION_OTP);
        assertThat(token.tokenHash()).isEqualTo(tokenHash);
        assertThat(token.expiresAt()).isEqualTo(expiresAt);
        assertThat(token.usedAt()).isNull();
        assertThat(token.isConsumed()).isFalse();
        assertThat(token.isExpired()).isFalse();
    }

    @Test
    @DisplayName("isConsumed returns true when usedAt is present and false when null")
    void isConsumedReturnsTrueWhenUsedAtPresent() {
        UserVerificationToken unconsumed = UserVerificationToken.createActivationOtp(
                UUID.randomUUID(), "hash", Instant.now().plusSeconds(60));
        assertThat(unconsumed.isConsumed()).isFalse();

        UserVerificationToken consumed = new UserVerificationToken(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UserVerificationToken.TYPE_ACTIVATION_OTP,
                "hash",
                Instant.now().plusSeconds(60),
                Instant.now(),
                Instant.now(),
                Instant.now(),
                null,
                null);
        assertThat(consumed.isConsumed()).isTrue();
    }

    @Test
    @DisplayName("isExpired returns true when expiresAt is in the past")
    void isExpiredReturnsTrueWhenPast() {
        UserVerificationToken expiredToken = UserVerificationToken.createActivationOtp(
                UUID.randomUUID(), "hash", Instant.now().minusSeconds(10));

        assertThat(expiredToken.isExpired()).isTrue();
    }
}
