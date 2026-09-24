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
            .orElseThrow(() ->
                new UnauthorizedException("Invalid refresh token")
            );

        Instant now = Instant.now();

        if (oldToken.revokedAt() != null) {
            throw new UnauthorizedException("Invalid refresh token");
        }

        if (oldToken.expiresAt().isBefore(now)) {
            throw new UnauthorizedException("Invalid refresh token");
        }

        User user = userRepository
            .findById(oldToken.userId())
            .orElseThrow(() ->
                new UnauthorizedException("Invalid refresh token")
            );

        if (!user.isActive()) {
            throw new UnauthorizedException("Invalid refresh token");
        }

        if (user.isTemporaryPassword()) {
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

        String resolvedDevice =
            userAgent != null && !userAgent.isBlank()
                ? userAgent
                : oldToken.deviceInfo();
        String resolvedIp =
            ipAddress != null && !ipAddress.isBlank()
                ? ipAddress
                : oldToken.ipAddress();

        UUID newSessionId = UUID.randomUUID();
        RefreshToken newRefreshToken = RefreshToken.builder()
            .id(newSessionId)
            .userId(user.id())
            .tokenHash(newTokenHash)
            .deviceInfo(resolvedDevice)
            .ipAddress(resolvedIp)
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

        return new RefreshResult(
            issuedToken.token(),
            rawNewRefreshToken,
            "Bearer",
            securityProperties.accessTokenTtlMinutes() * 60L
        );
    }
}
