package com.unihub.workshop.iam.application.usecases;

import com.unihub.workshop.iam.application.ports.in.LogoutUseCase;
import com.unihub.workshop.iam.application.ports.out.JtiBlacklistPort;
import com.unihub.workshop.iam.application.ports.out.RefreshTokenRepository;
import com.unihub.workshop.iam.domain.model.RefreshToken;
import com.unihub.workshop.iam.domain.utils.TokenHashUtils;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class LogoutUseCaseImpl implements LogoutUseCase {

    private final JtiBlacklistPort jtiBlacklistPort;
    private final RefreshTokenRepository refreshTokenRepository;
    private final MeterRegistry meterRegistry;

    public LogoutUseCaseImpl(
            JtiBlacklistPort jtiBlacklistPort,
            RefreshTokenRepository refreshTokenRepository,
            MeterRegistry meterRegistry) {
        this.jtiBlacklistPort = jtiBlacklistPort;
        this.refreshTokenRepository = refreshTokenRepository;
        this.meterRegistry = meterRegistry;
    }

    @Override
    @Transactional
    public void logout(String accessTokenString, String jti, Instant expiresAt, UUID userId,
            String refreshTokenString) {
        Instant now = Instant.now();
        Duration ttl = Duration.between(now, expiresAt);
        if (ttl.isNegative()) {
            ttl = Duration.ZERO;
        }

        // 1. Blacklist the access token's JTI
        jtiBlacklistPort.blacklistJti(jti, ttl);

        // 2. Revoke refresh token(s) in DB
        if (refreshTokenString != null && !refreshTokenString.isBlank()) {
            String tokenHash = TokenHashUtils.hash(refreshTokenString);
            refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
                if (token.userId().equals(userId)) {
                    RefreshToken revoked = new RefreshToken(
                            token.id(),
                            token.userId(),
                            token.tokenHash(),
                            token.deviceInfo(),
                            token.ipAddress(),
                            token.expiresAt(),
                            now);
                    refreshTokenRepository.save(revoked);
                }
            });
        } else {
            refreshTokenRepository.revokeAllActiveTokensForUser(userId, now);
        }

        meterRegistry.counter("auth.logout", "outcome", "success").increment();
    }
}
