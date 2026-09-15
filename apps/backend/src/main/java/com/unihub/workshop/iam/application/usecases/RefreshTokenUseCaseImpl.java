package com.unihub.workshop.iam.application.usecases;

import com.unihub.workshop.iam.application.ports.in.RefreshTokenUseCase;
import com.unihub.workshop.iam.application.ports.out.RefreshTokenRepository;
import com.unihub.workshop.iam.application.ports.out.SessionTrackerPort;
import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.application.utils.TokenUtils;
import com.unihub.workshop.iam.domain.model.RefreshToken;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.shared.exception.UnauthorizedException;
import com.unihub.workshop.shared.security.IssuedToken;
import com.unihub.workshop.shared.security.JwtTokenIssuer;
import com.unihub.workshop.shared.security.SecurityProperties;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefreshTokenUseCaseImpl implements RefreshTokenUseCase {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final SessionTrackerPort sessionTrackerPort;
    private final JwtTokenIssuer jwtTokenIssuer;
    private final SecurityProperties securityProperties;
    private final MeterRegistry meterRegistry;

    @Override
    @Transactional
    public RefreshResult refresh(
        String refreshTokenString,
        String userAgent,
        String ipAddress
    ) {
        String tokenHash = TokenUtils.hash(refreshTokenString);

        RefreshToken oldToken = refreshTokenRepository
            .findByTokenHash(tokenHash)
            .orElseThrow(() -> {
                meterRegistry
                    .counter(
                        "auth.token.refresh",
                        "outcome",
                        "failure",
                        "reason",
                        "not_found"
                    )
                    .increment();
                return new UnauthorizedException("Invalid refresh token");
            });

        Instant now = Instant.now();

        if (oldToken.revokedAt() != null) {
            meterRegistry
                .counter(
                    "auth.token.refresh",
                    "outcome",
                    "failure",
                    "reason",
                    "revoked"
                )
                .increment();
            throw new UnauthorizedException("Invalid refresh token");
        }

        if (oldToken.expiresAt().isBefore(now)) {
            meterRegistry
                .counter(
                    "auth.token.refresh",
                    "outcome",
                    "failure",
                    "reason",
                    "expired"
                )
                .increment();
            throw new UnauthorizedException("Invalid refresh token");
        }

        User user = userRepository
            .findById(oldToken.userId())
            .orElseThrow(() -> {
                meterRegistry
                    .counter(
                        "auth.token.refresh",
                        "outcome",
                        "failure",
                        "reason",
                        "user_not_found"
                    )
                    .increment();
                return new UnauthorizedException("Invalid refresh token");
            });

        if (!user.isActive()) {
            meterRegistry
                .counter(
                    "auth.token.refresh",
                    "outcome",
                    "failure",
                    "reason",
                    "account_disabled"
                )
                .increment();
            throw new UnauthorizedException("Invalid refresh token");
        }

        RefreshToken revokedToken = oldToken.toBuilder().revokedAt(now).build();
        refreshTokenRepository.save(revokedToken);
        sessionTrackerPort.removeSession(oldToken.id());

        String orgId =
            user.organizationId() != null
                ? user.organizationId().toString()
                : null;

        String rawNewRefreshToken = TokenUtils.generateSecureToken();
        String newTokenHash = TokenUtils.hash(rawNewRefreshToken);
        Instant newExpiresAt = now.plus(
            securityProperties.refreshTokenTtlDays(),
            ChronoUnit.DAYS
        );

        UUID newSessionId = UUID.randomUUID();
        RefreshToken newRefreshToken = RefreshToken.builder()
            .id(newSessionId)
            .userId(user.id())
            .tokenHash(newTokenHash)
            .deviceInfo(userAgent)
            .ipAddress(ipAddress)
            .expiresAt(newExpiresAt)
            .createdAt(now)
            .updatedAt(now)
            .build();
        refreshTokenRepository.save(newRefreshToken);

        IssuedToken issuedToken = jwtTokenIssuer.issueToken(
            user.id().toString(),
            user.role().name(),
            orgId,
            newSessionId.toString()
        );

        sessionTrackerPort.trackSession(
            newSessionId,
            issuedToken.jti(),
            Duration.ofMinutes(securityProperties.accessTokenTtlMinutes())
        );

        meterRegistry
            .counter("auth.token.refresh", "outcome", "success")
            .increment();

        return new RefreshResult(
            issuedToken.token(),
            rawNewRefreshToken,
            "Bearer",
            securityProperties.accessTokenTtlMinutes() * 60L
        );
    }
}
