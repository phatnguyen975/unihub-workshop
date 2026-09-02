package com.unihub.workshop.iam.application.usecases;

import com.unihub.workshop.iam.application.ports.out.JtiBlacklistPort;
import com.unihub.workshop.iam.application.ports.out.RefreshTokenRepository;
import com.unihub.workshop.iam.domain.model.RefreshToken;
import com.unihub.workshop.shared.exception.ServiceUnavailableException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LogoutUseCaseImplTest {

    @Mock
    private JtiBlacklistPort jtiBlacklistPort;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private MeterRegistry meterRegistry;
    private LogoutUseCaseImpl useCase;

    private final UUID userId = UUID.randomUUID();
    private final String jti = UUID.randomUUID().toString();
    private final String accessToken = "access.token.string";
    private final Instant expiresAt = Instant.now().plusSeconds(3600);

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        useCase = new LogoutUseCaseImpl(jtiBlacklistPort, refreshTokenRepository, meterRegistry);
    }

    private String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * encodedHash.length);
            for (byte b : encodedHash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void shouldBlacklistJtiAndRevokeSpecificToken() {
        String rawToken = "raw-token";
        String tokenHash = hash(rawToken);
        RefreshToken token = new RefreshToken(UUID.randomUUID(), userId, tokenHash, "dev", "ip",
                Instant.now().plusSeconds(100), null);

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

        useCase.logout(accessToken, jti, expiresAt, userId, rawToken);

        ArgumentCaptor<Duration> durationCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(jtiBlacklistPort).blacklistJti(eq(jti), durationCaptor.capture());
        assertThat(durationCaptor.getValue()).isGreaterThan(Duration.ofMinutes(59));

        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().revokedAt()).isNotNull();

        verify(refreshTokenRepository, never()).revokeAllActiveTokensForUser(any(), any());

        assertThat(meterRegistry.counter("auth.logout", "outcome", "success").count()).isEqualTo(1);
    }

    @Test
    void shouldBlacklistJtiAndRevokeAllTokensWhenRefreshTokenIsNull() {
        useCase.logout(accessToken, jti, expiresAt, userId, null);

        verify(jtiBlacklistPort).blacklistJti(eq(jti), any(Duration.class));
        verify(refreshTokenRepository).revokeAllActiveTokensForUser(eq(userId), any(Instant.class));
        verify(refreshTokenRepository, never()).save(any());

        assertThat(meterRegistry.counter("auth.logout", "outcome", "success").count()).isEqualTo(1);
    }

    @Test
    void shouldFailIfRedisFails() {
        doThrow(new ServiceUnavailableException("urn:unihub:error:service-unavailable", "Redis down"))
                .when(jtiBlacklistPort).blacklistJti(any(), any());

        assertThatThrownBy(() -> useCase.logout(accessToken, jti, expiresAt, userId, "raw"))
                .isInstanceOf(ServiceUnavailableException.class);

        verify(refreshTokenRepository, never()).save(any());
        verify(refreshTokenRepository, never()).revokeAllActiveTokensForUser(any(), any());
    }
}
