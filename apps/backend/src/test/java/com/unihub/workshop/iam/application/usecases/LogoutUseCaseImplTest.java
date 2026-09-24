package com.unihub.workshop.iam.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.unihub.workshop.iam.application.ports.out.JtiBlacklistPort;
import com.unihub.workshop.iam.application.ports.out.RefreshTokenRepository;
import com.unihub.workshop.iam.application.utils.TokenUtils;
import com.unihub.workshop.iam.domain.model.RefreshToken;
import com.unihub.workshop.shared.exception.ServiceUnavailableException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LogoutUseCaseImplTest {

    @Mock
    private JtiBlacklistPort jtiBlacklistPort;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private LogoutUseCaseImpl useCase;

    private final UUID userId = UUID.randomUUID();
    private final String jti = UUID.randomUUID().toString();
    private final Instant expiresAt = Instant.now().plusSeconds(3600);

    @BeforeEach
    void setUp() {
        useCase = new LogoutUseCaseImpl(
            jtiBlacklistPort,
            refreshTokenRepository
        );
    }

    @Test
    void shouldBlacklistJtiAndRevokeSpecificToken() {
        String rawToken = "raw-token";
        String tokenHash = TokenUtils.hash(rawToken);
        RefreshToken token = new RefreshToken(
            UUID.randomUUID(),
            userId,
            tokenHash,
            "dev",
            "ip",
            Instant.now().plusSeconds(100),
            null
        );

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(
            Optional.of(token)
        );

        useCase.logout(jti, expiresAt, userId, rawToken);

        ArgumentCaptor<Duration> durationCaptor = ArgumentCaptor.forClass(
            Duration.class
        );
        verify(jtiBlacklistPort).blacklistJti(
            eq(jti),
            durationCaptor.capture()
        );
        assertThat(durationCaptor.getValue()).isGreaterThan(
            Duration.ofMinutes(59)
        );

        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(
            RefreshToken.class
        );
        verify(refreshTokenRepository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().revokedAt()).isNotNull();

        verify(refreshTokenRepository, never()).revokeAllActiveTokensForUser(
            any(),
            any()
        );
    }

    @Test
    void shouldBlacklistJtiAndRevokeAllTokensWhenRefreshTokenIsNull() {
        useCase.logout(jti, expiresAt, userId, null);

        verify(jtiBlacklistPort).blacklistJti(eq(jti), any(Duration.class));
        verify(refreshTokenRepository).revokeAllActiveTokensForUser(
            eq(userId),
            any(Instant.class)
        );
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void shouldBlacklistJtiAndRevokeAllTokensWhenRefreshTokenIsBlank() {
        useCase.logout(jti, expiresAt, userId, "   ");

        verify(jtiBlacklistPort).blacklistJti(eq(jti), any(Duration.class));
        verify(refreshTokenRepository).revokeAllActiveTokensForUser(
            eq(userId),
            any(Instant.class)
        );
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void shouldBlacklistJtiAndGracefullySucceedWhenRefreshTokenNotFound() {
        String rawToken = "non-existent-token";
        String tokenHash = TokenUtils.hash(rawToken);

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(
            Optional.empty()
        );

        useCase.logout(jti, expiresAt, userId, rawToken);

        verify(jtiBlacklistPort).blacklistJti(eq(jti), any(Duration.class));
        verify(refreshTokenRepository, never()).save(any());
        verify(refreshTokenRepository, never()).revokeAllActiveTokensForUser(
            any(),
            any()
        );
    }

    @Test
    void shouldBlacklistJtiAndNotRevokeTokenWhenBelongingToDifferentUser() {
        String rawToken = "other-user-token";
        String tokenHash = TokenUtils.hash(rawToken);
        UUID differentUserId = UUID.randomUUID();

        RefreshToken otherUserToken = new RefreshToken(
            UUID.randomUUID(),
            differentUserId,
            tokenHash,
            "dev",
            "ip",
            Instant.now().plusSeconds(100),
            null
        );

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(
            Optional.of(otherUserToken)
        );

        useCase.logout(jti, expiresAt, userId, rawToken);

        verify(jtiBlacklistPort).blacklistJti(eq(jti), any(Duration.class));
        verify(refreshTokenRepository, never()).save(any());
        verify(refreshTokenRepository, never()).revokeAllActiveTokensForUser(
            any(),
            any()
        );
    }

    @Test
    void shouldClampTtlToZeroWhenTokenAlreadyExpired() {
        Instant pastExpiration = Instant.now().minusSeconds(10);

        useCase.logout(jti, pastExpiration, userId, null);

        verify(jtiBlacklistPort).blacklistJti(eq(jti), eq(Duration.ZERO));
    }

    @Test
    void shouldFailIfRedisFailsAndNeverTouchDatabase() {
        doThrow(
            new ServiceUnavailableException(
                "urn:unihub:error:service-unavailable",
                "Redis down"
            )
        )
            .when(jtiBlacklistPort)
            .blacklistJti(any(), any());

        assertThatThrownBy(() ->
            useCase.logout(jti, expiresAt, userId, "raw")
        ).isInstanceOf(ServiceUnavailableException.class);

        verify(refreshTokenRepository, never()).save(any());
        verify(refreshTokenRepository, never()).revokeAllActiveTokensForUser(
            any(),
            any()
        );
    }
}
