package com.unihub.workshop.iam.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordResetTokenTest {

    @Test
    void createShouldInitializeUnconsumedToken() {
        UUID userId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);

        PasswordResetToken token = PasswordResetToken.create(userId, "hash123", expiresAt);

        assertThat(token.id()).isNotNull();
        assertThat(token.userId()).isEqualTo(userId);
        assertThat(token.tokenHash()).isEqualTo("hash123");
        assertThat(token.expiresAt()).isEqualTo(expiresAt);
        assertThat(token.usedAt()).isNull();
        assertThat(token.isConsumed()).isFalse();
        assertThat(token.isExpired()).isFalse();
    }

    @Test
    void consumeShouldSetUsedAt() {
        PasswordResetToken token = PasswordResetToken.create(UUID.randomUUID(), "hash123", Instant.now().plusSeconds(60));
        PasswordResetToken consumed = token.consume();

        assertThat(consumed.usedAt()).isNotNull();
        assertThat(consumed.isConsumed()).isTrue();
    }

    @Test
    void isExpiredShouldReturnTrueWhenExpiresAtInPast() {
        PasswordResetToken expiredToken = PasswordResetToken.create(UUID.randomUUID(), "hash123", Instant.now().minusSeconds(10));
        assertThat(expiredToken.isExpired()).isTrue();
    }
}
