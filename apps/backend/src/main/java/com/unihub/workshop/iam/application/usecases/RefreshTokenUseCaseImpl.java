package com.unihub.workshop.iam.application.usecases;

import com.unihub.workshop.iam.application.ports.in.LoginUseCase.LoginResult;
import com.unihub.workshop.iam.application.ports.in.RefreshTokenUseCase;
import com.unihub.workshop.iam.application.ports.out.RefreshTokenRepository;
import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.domain.model.RefreshToken;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.shared.exception.UnauthorizedException;
import com.unihub.workshop.shared.security.JwtTokenIssuer;
import com.unihub.workshop.shared.security.SecurityProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

@Service
public class RefreshTokenUseCaseImpl implements RefreshTokenUseCase {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtTokenIssuer jwtTokenIssuer;
    private final SecurityProperties securityProperties;
    private final MeterRegistry meterRegistry;

    public RefreshTokenUseCaseImpl(
            RefreshTokenRepository refreshTokenRepository,
            UserRepository userRepository,
            JwtTokenIssuer jwtTokenIssuer,
            SecurityProperties securityProperties,
            MeterRegistry meterRegistry) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.jwtTokenIssuer = jwtTokenIssuer;
        this.securityProperties = securityProperties;
        this.meterRegistry = meterRegistry;
    }

    @Override
    @Transactional
    public LoginResult refresh(String refreshTokenString, String userAgent, String ipAddress) {
        String tokenHash = hashToken(refreshTokenString);

        RefreshToken oldToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> {
                    meterRegistry.counter("auth.token.refresh", "outcome", "failure", "reason", "not_found")
                            .increment();
                    return new UnauthorizedException("urn:unihub:error:authentication", "Invalid refresh token");
                });

        Instant now = Instant.now();

        if (oldToken.revokedAt() != null) {
            meterRegistry.counter("auth.token.refresh", "outcome", "failure", "reason", "revoked").increment();
            throw new UnauthorizedException("urn:unihub:error:authentication", "Invalid refresh token");
        }

        if (oldToken.expiresAt().isBefore(now)) {
            meterRegistry.counter("auth.token.refresh", "outcome", "failure", "reason", "expired").increment();
            throw new UnauthorizedException("urn:unihub:error:authentication", "Invalid refresh token");
        }

        RefreshToken revokedToken = new RefreshToken(
                oldToken.id(),
                oldToken.userId(),
                oldToken.tokenHash(),
                oldToken.deviceInfo(),
                oldToken.ipAddress(),
                oldToken.expiresAt(),
                now);
        refreshTokenRepository.save(revokedToken);

        User user = userRepository.findById(oldToken.userId())
                .orElseThrow(() -> {
                    meterRegistry.counter("auth.token.refresh", "outcome", "failure", "reason", "user_not_found")
                            .increment();
                    return new UnauthorizedException("urn:unihub:error:authentication", "Invalid refresh token");
                });

        if (!user.isActive()) {
            meterRegistry.counter("auth.token.refresh", "outcome", "failure", "reason", "account_disabled").increment();
            throw new UnauthorizedException("urn:unihub:error:authentication", "Invalid refresh token");
        }

        String accessToken = jwtTokenIssuer.issueAccessToken(
                user.id().toString(),
                user.role().name(),
                user.organizationId().toString());

        String rawNewRefreshToken = generateSecureToken();
        String newTokenHash = hashToken(rawNewRefreshToken);
        Instant newExpiresAt = now.plus(securityProperties.refreshTokenTtlDays(), ChronoUnit.DAYS);

        RefreshToken newRefreshToken = new RefreshToken(
                UUID.randomUUID(),
                user.id(),
                newTokenHash,
                userAgent,
                ipAddress,
                newExpiresAt,
                null);
        refreshTokenRepository.save(newRefreshToken);

        meterRegistry.counter("auth.token.refresh", "outcome", "success").increment();

        return new LoginResult(
                accessToken,
                rawNewRefreshToken,
                "Bearer",
                securityProperties.accessTokenTtlMinutes() * 60L);
    }

    private String generateSecureToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * encodedHash.length);
            for (byte b : encodedHash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }
}
