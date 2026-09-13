package com.unihub.workshop.iam.application.usecases;

import com.unihub.workshop.iam.application.ports.in.LoginUseCase;
import com.unihub.workshop.iam.application.ports.out.RefreshTokenRepository;
import com.unihub.workshop.iam.application.ports.out.SessionTrackerPort;
import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.application.utils.TokenUtils;
import com.unihub.workshop.iam.domain.model.RefreshToken;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.UnauthorizedException;
import com.unihub.workshop.shared.security.IssuedToken;
import com.unihub.workshop.shared.security.JwtTokenIssuer;
import com.unihub.workshop.shared.security.SecurityProperties;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginUseCaseImpl implements LoginUseCase {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SessionTrackerPort sessionTrackerPort;
    private final JwtTokenIssuer jwtTokenIssuer;
    private final PasswordEncoder passwordEncoder;
    private final SecurityProperties securityProperties;
    private final MeterRegistry meterRegistry;
    private final String dummyPasswordHash;

    public LoginUseCaseImpl(
        UserRepository userRepository,
        RefreshTokenRepository refreshTokenRepository,
        SessionTrackerPort sessionTrackerPort,
        JwtTokenIssuer jwtTokenIssuer,
        PasswordEncoder passwordEncoder,
        SecurityProperties securityProperties,
        MeterRegistry meterRegistry
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.sessionTrackerPort = sessionTrackerPort;
        this.jwtTokenIssuer = jwtTokenIssuer;
        this.passwordEncoder = passwordEncoder;
        this.securityProperties = securityProperties;
        this.meterRegistry = meterRegistry;
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-hash");
    }

    @Override
    @Transactional
    public LoginResult login(
        String email,
        String password,
        String deviceInfo,
        String ipAddress
    ) {
        Optional<User> optionalUser = userRepository.findByEmail(email);

        if (optionalUser.isEmpty()) {
            // Anti-enumeration: perform dummy hash check
            passwordEncoder.matches(password, dummyPasswordHash);
            meterRegistry
                .counter(
                    "auth.login.attempts",
                    "outcome",
                    "failure",
                    "reason",
                    "invalid_credentials"
                )
                .increment();
            throw new UnauthorizedException("Invalid email or password");
        }

        User user = optionalUser.get();

        if (!passwordEncoder.matches(password, user.passwordHash())) {
            meterRegistry
                .counter(
                    "auth.login.attempts",
                    "outcome",
                    "failure",
                    "reason",
                    "invalid_credentials"
                )
                .increment();
            throw new UnauthorizedException("Invalid email or password");
        }

        if (!user.isActive()) {
            meterRegistry
                .counter(
                    "auth.login.attempts",
                    "outcome",
                    "failure",
                    "reason",
                    "account_disabled"
                )
                .increment();
            throw new ForbiddenException(
                "urn:unihub:error:account-disabled",
                "Account is disabled"
            );
        }

        if (user.isTemporaryPassword()) {
            meterRegistry
                .counter(
                    "auth.login.attempts",
                    "outcome",
                    "failure",
                    "reason",
                    "temporary_password"
                )
                .increment();
            throw new ForbiddenException(
                "urn:unihub:error:temporary-password",
                "Temporary password change required"
            );
        }

        Instant now = Instant.now();
        userRepository.updateLastLoginAt(user.id(), now);

        String rawRefreshToken = TokenUtils.generateSecureToken();
        String tokenHash = TokenUtils.hash(rawRefreshToken);
        Instant expiresAt = now.plus(
            securityProperties.refreshTokenTtlDays(),
            ChronoUnit.DAYS
        );

        UUID sessionId = UUID.randomUUID();
        RefreshToken refreshToken = new RefreshToken(
            sessionId,
            user.id(),
            tokenHash,
            deviceInfo,
            ipAddress,
            expiresAt,
            null
        );
        refreshTokenRepository.save(refreshToken);

        String orgId =
            user.organizationId() != null
                ? user.organizationId().toString()
                : null;
        IssuedToken issuedToken = jwtTokenIssuer.issueToken(
            user.id().toString(),
            user.role().name(),
            orgId,
            sessionId.toString()
        );

        sessionTrackerPort.trackSession(
            sessionId,
            issuedToken.jti(),
            Duration.ofMinutes(securityProperties.accessTokenTtlMinutes())
        );

        meterRegistry
            .counter("auth.login.attempts", "outcome", "success")
            .increment();

        return new LoginResult(
            issuedToken.token(),
            rawRefreshToken,
            "Bearer",
            securityProperties.accessTokenTtlMinutes() * 60L,
            user
        );
    }
}
