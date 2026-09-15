package com.unihub.workshop.iam.application.usecases;

import com.unihub.workshop.iam.application.ports.in.ChangeTemporaryPasswordUseCase;
import com.unihub.workshop.iam.application.ports.out.RefreshTokenRepository;
import com.unihub.workshop.iam.application.ports.out.SessionTrackerPort;
import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.application.utils.TokenUtils;
import com.unihub.workshop.iam.domain.model.RefreshToken;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.exception.UnauthorizedException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.shared.security.IssuedToken;
import com.unihub.workshop.shared.security.JwtTokenIssuer;
import com.unihub.workshop.shared.security.SecurityProperties;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChangeTemporaryPasswordUseCaseImpl
    implements ChangeTemporaryPasswordUseCase
{

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SessionTrackerPort sessionTrackerPort;
    private final JwtTokenIssuer jwtTokenIssuer;
    private final PasswordEncoder passwordEncoder;
    private final SecurityProperties securityProperties;
    private final MeterRegistry meterRegistry;

    private volatile String dummyPasswordHash;

    @PostConstruct
    void init() {
        if (dummyPasswordHash == null) {
            dummyPasswordHash = passwordEncoder.encode("dummy-password-hash");
        }
    }

    private String getDummyPasswordHash() {
        if (dummyPasswordHash == null) {
            dummyPasswordHash = passwordEncoder.encode("dummy-password-hash");
        }
        return dummyPasswordHash;
    }

    @Override
    @Transactional
    public ChangeTemporaryPasswordResult changeTemporaryPassword(
        String email,
        String currentPassword,
        String newPassword,
        String deviceInfo,
        String ipAddress
    ) {
        String normalizedEmail =
            email != null ? email.toLowerCase().trim() : "";
        Optional<User> optionalUser = userRepository.findByEmail(
            normalizedEmail
        );

        if (optionalUser.isEmpty()) {
            passwordEncoder.matches(currentPassword, getDummyPasswordHash());
            meterRegistry
                .counter(
                    "auth.password.temporary.attempts",
                    "outcome",
                    "failure",
                    "reason",
                    "invalid_credentials"
                )
                .increment();
            throw new UnauthorizedException(
                "Invalid email or current password"
            );
        }

        User user = optionalUser.get();

        if (!user.isActive()) {
            meterRegistry
                .counter(
                    "auth.password.temporary.attempts",
                    "outcome",
                    "failure",
                    "reason",
                    "invalid_credentials"
                )
                .increment();
            throw new UnauthorizedException(
                "Invalid email or current password"
            );
        }

        if (!passwordEncoder.matches(currentPassword, user.passwordHash())) {
            meterRegistry
                .counter(
                    "auth.password.temporary.attempts",
                    "outcome",
                    "failure",
                    "reason",
                    "invalid_credentials"
                )
                .increment();
            throw new UnauthorizedException(
                "Invalid email or current password"
            );
        }

        if (!user.isTemporaryPassword()) {
            meterRegistry
                .counter(
                    "auth.password.temporary.attempts",
                    "outcome",
                    "failure",
                    "reason",
                    "not_temporary_password"
                )
                .increment();
            throw new BadRequestException(
                "Account does not have a temporary password."
            );
        }

        if (newPassword != null && newPassword.equals(currentPassword)) {
            throw new ValidationException(
                "newPassword",
                "New password must be different from the temporary password."
            );
        }

        Instant now = Instant.now();
        String newPasswordHash = passwordEncoder.encode(newPassword);

        boolean updated = userRepository.updateTemporaryPassword(
            user.id(),
            newPasswordHash,
            now
        );
        if (!updated) {
            meterRegistry
                .counter(
                    "auth.password.temporary.attempts",
                    "outcome",
                    "failure",
                    "reason",
                    "not_temporary_password"
                )
                .increment();
            throw new BadRequestException(
                "Account does not have a temporary password."
            );
        }

        User updatedUser = user.changeTemporaryPassword(newPasswordHash, now);

        String rawRefreshToken = TokenUtils.generateSecureToken();
        String tokenHash = TokenUtils.hash(rawRefreshToken);
        Instant expiresAt = now.plus(
            securityProperties.refreshTokenTtlDays(),
            ChronoUnit.DAYS
        );

        UUID sessionId = UUID.randomUUID();
        RefreshToken refreshToken = RefreshToken.builder()
            .id(sessionId)
            .userId(updatedUser.id())
            .tokenHash(tokenHash)
            .deviceInfo(deviceInfo)
            .ipAddress(ipAddress)
            .expiresAt(expiresAt)
            .createdAt(now)
            .updatedAt(now)
            .build();
        refreshTokenRepository.save(refreshToken);

        String orgId =
            updatedUser.organizationId() != null
                ? updatedUser.organizationId().toString()
                : null;
        IssuedToken issuedToken = jwtTokenIssuer.issueToken(
            updatedUser.id().toString(),
            updatedUser.role().name(),
            orgId,
            sessionId.toString()
        );

        sessionTrackerPort.trackSession(
            sessionId,
            issuedToken.jti(),
            Duration.ofMinutes(securityProperties.accessTokenTtlMinutes())
        );

        meterRegistry
            .counter("auth.password.temporary.attempts", "outcome", "success")
            .increment();

        return new ChangeTemporaryPasswordResult(
            issuedToken.token(),
            rawRefreshToken,
            "Bearer",
            securityProperties.accessTokenTtlMinutes() * 60L,
            updatedUser
        );
    }
}
