package com.unihub.workshop.iam.application.usecases;

import com.unihub.workshop.iam.application.ports.in.ChangeTemporaryPasswordUseCase;
import com.unihub.workshop.iam.application.ports.out.RefreshTokenRepository;
import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.application.utils.TokenUtils;
import com.unihub.workshop.iam.domain.model.RefreshToken;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.exception.UnauthorizedException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.shared.security.JwtTokenIssuer;
import com.unihub.workshop.shared.security.SecurityProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
public class ChangeTemporaryPasswordUseCaseImpl implements ChangeTemporaryPasswordUseCase {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenIssuer jwtTokenIssuer;
    private final PasswordEncoder passwordEncoder;
    private final SecurityProperties securityProperties;
    private final MeterRegistry meterRegistry;
    private final String dummyPasswordHash;

    public ChangeTemporaryPasswordUseCaseImpl(
            UserRepository userRepository,
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
    public ChangeTemporaryPasswordResult changeTemporaryPassword(
            String email,
            String currentPassword,
            String newPassword,
            String deviceInfo,
            String ipAddress) {
        String normalizedEmail = email != null ? email.toLowerCase().trim() : "";
        Optional<User> optionalUser = userRepository.findByEmail(normalizedEmail);

        if (optionalUser.isEmpty()) {
            passwordEncoder.matches(currentPassword, dummyPasswordHash);
            meterRegistry.counter("auth.password.temporary.attempts", "outcome", "failure", "reason", "invalid_credentials")
                    .increment();
            throw new UnauthorizedException("Invalid email or current password");
        }

        User user = optionalUser.get();

        if (!user.isActive()) {
            meterRegistry.counter("auth.password.temporary.attempts", "outcome", "failure", "reason", "invalid_credentials")
                    .increment();
            throw new UnauthorizedException("Invalid email or current password");
        }

        if (!passwordEncoder.matches(currentPassword, user.passwordHash())) {
            meterRegistry.counter("auth.password.temporary.attempts", "outcome", "failure", "reason", "invalid_credentials")
                    .increment();
            throw new UnauthorizedException("Invalid email or current password");
        }

        if (!user.isTemporaryPassword()) {
            meterRegistry.counter("auth.password.temporary.attempts", "outcome", "failure", "reason", "not_temporary_password")
                    .increment();
            throw new BadRequestException("Account does not have a temporary password.");
        }

        if (newPassword != null && newPassword.equals(currentPassword)) {
            throw new ValidationException("newPassword", "New password must be different from the temporary password.");
        }

        Instant now = Instant.now();
        String newPasswordHash = passwordEncoder.encode(newPassword);
        User updatedUser = user.changeTemporaryPassword(newPasswordHash, now);
        User savedUser = userRepository.save(updatedUser);
        if (savedUser == null) {
            savedUser = updatedUser;
        }

        String orgId = savedUser.organizationId() != null ? savedUser.organizationId().toString() : null;
        String accessToken = jwtTokenIssuer.issueAccessToken(
                savedUser.id().toString(),
                savedUser.role().name(),
                orgId);

        String rawRefreshToken = TokenUtils.generateSecureToken();
        String tokenHash = TokenUtils.hash(rawRefreshToken);
        Instant expiresAt = now.plus(securityProperties.refreshTokenTtlDays(), ChronoUnit.DAYS);

        RefreshToken refreshToken = new RefreshToken(
                UUID.randomUUID(),
                savedUser.id(),
                tokenHash,
                deviceInfo,
                ipAddress,
                expiresAt,
                null);
        refreshTokenRepository.save(refreshToken);

        meterRegistry.counter("auth.password.temporary.attempts", "outcome", "success").increment();

        return new ChangeTemporaryPasswordResult(
                accessToken,
                rawRefreshToken,
                "Bearer",
                securityProperties.accessTokenTtlMinutes() * 60L,
                savedUser);
    }
}
