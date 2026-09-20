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
public class LoginUseCaseImpl implements LoginUseCase {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SessionTrackerPort sessionTrackerPort;
    private final JwtTokenIssuer jwtTokenIssuer;
    private final PasswordEncoder passwordEncoder;
    private final SecurityProperties securityProperties;

    @Override
    @Transactional
    public LoginResult login(
        String email,
        String password,
        String deviceInfo,
        String ipAddress
    ) {
        String normalizedEmail =
            email != null ? email.toLowerCase().trim() : "";

        Optional<User> optionalUser = userRepository.findByEmail(
            normalizedEmail
        );

        if (optionalUser.isEmpty()) {
            throw new UnauthorizedException("Invalid email or password");
        }

        User user = optionalUser.get();

        if (!passwordEncoder.matches(password, user.passwordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        if (!user.isActive()) {
            throw new ForbiddenException(
                "urn:unihub:error:account-disabled",
                "Account is disabled"
            );
        }

        if (user.isTemporaryPassword()) {
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
        RefreshToken refreshToken = RefreshToken.builder()
            .id(sessionId)
            .userId(user.id())
            .tokenHash(tokenHash)
            .deviceInfo(deviceInfo)
            .ipAddress(ipAddress)
            .expiresAt(expiresAt)
            .createdAt(now)
            .updatedAt(now)
            .build();
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

        return new LoginResult(
            issuedToken.token(),
            rawRefreshToken,
            "Bearer",
            securityProperties.accessTokenTtlMinutes() * 60L,
            user
        );
    }
}
