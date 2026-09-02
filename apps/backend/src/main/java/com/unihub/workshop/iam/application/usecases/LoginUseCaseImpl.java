package com.unihub.workshop.iam.application.usecases;

import com.unihub.workshop.iam.application.ports.in.LoginUseCase;
import com.unihub.workshop.iam.application.ports.out.RefreshTokenRepository;
import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.shared.exception.UnauthorizedException;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.iam.domain.model.RefreshToken;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.utils.TokenHashUtils;
import com.unihub.workshop.shared.security.JwtTokenIssuer;
import com.unihub.workshop.shared.security.SecurityProperties;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.security.SecureRandom;

@Service
public class LoginUseCaseImpl implements LoginUseCase {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenIssuer jwtTokenIssuer;
    private final PasswordEncoder passwordEncoder;
    private final SecurityProperties securityProperties;
    private final MeterRegistry meterRegistry;
    private final String dummyPasswordHash;

    public LoginUseCaseImpl(UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            JwtTokenIssuer jwtTokenIssuer,
            PasswordEncoder passwordEncoder,
            SecurityProperties securityProperties,
            MeterRegistry meterRegistry) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtTokenIssuer = jwtTokenIssuer;
        this.passwordEncoder = passwordEncoder;
        this.securityProperties = securityProperties;
        this.meterRegistry = meterRegistry;
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-hash");
    }

    @Override
    @Transactional
    public LoginResult login(String email, String password, String deviceInfo, String ipAddress) {
        Optional<User> optionalUser = userRepository.findByEmail(email);

        if (optionalUser.isEmpty()) {
            // Anti-enumeration: perform dummy hash check
            passwordEncoder.matches(password, dummyPasswordHash);
            meterRegistry.counter("auth.login.attempts", "outcome", "failure", "reason", "invalid_credentials")
                    .increment();
            throw new UnauthorizedException("urn:unihub:error:authentication", "Invalid email or password");
        }

        User user = optionalUser.get();

        if (!passwordEncoder.matches(password, user.passwordHash())) {
            meterRegistry.counter("auth.login.attempts", "outcome", "failure", "reason", "invalid_credentials")
                    .increment();
            throw new UnauthorizedException("urn:unihub:error:authentication", "Invalid email or password");
        }

        if (!user.isActive()) {
            meterRegistry.counter("auth.login.attempts", "outcome", "failure", "reason", "account_disabled")
                    .increment();
            throw new ForbiddenException("urn:unihub:error:account-disabled", "Account is disabled");
        }

        if (user.isTemporaryPassword()) {
            meterRegistry.counter("auth.login.attempts", "outcome", "failure", "reason", "temporary_password")
                    .increment();
            throw new ForbiddenException("urn:unihub:error:temporary-password", "Temporary password change required");
        }

        Instant now = Instant.now();
        userRepository.updateLastLoginAt(user.id(), now);

        String accessToken = jwtTokenIssuer.issueAccessToken(
                user.id().toString(),
                user.role().name(),
                user.organizationId().toString());

        String rawRefreshToken = generateSecureToken();
        String tokenHash = TokenHashUtils.hash(rawRefreshToken);
        Instant expiresAt = now.plus(securityProperties.refreshTokenTtlDays(), ChronoUnit.DAYS);

        RefreshToken refreshToken = new RefreshToken(
                UUID.randomUUID(),
                user.id(),
                tokenHash,
                deviceInfo,
                ipAddress,
                expiresAt,
                null);
        refreshTokenRepository.save(refreshToken);

        meterRegistry.counter("auth.login.attempts", "outcome", "success").increment();

        return new LoginResult(
                accessToken,
                rawRefreshToken,
                "Bearer",
                securityProperties.accessTokenTtlMinutes() * 60L);
    }

    private String generateSecureToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
