package com.unihub.workshop.iam.application.usecases;

import com.unihub.workshop.iam.application.dto.ChangePasswordRequest;
import com.unihub.workshop.iam.application.dto.ChangePasswordResponse;
import com.unihub.workshop.iam.application.dto.ForgotPasswordRequest;
import com.unihub.workshop.iam.application.dto.ForgotPasswordResponse;
import com.unihub.workshop.iam.application.dto.LoginRequest;
import com.unihub.workshop.iam.application.dto.LoginResponse;
import com.unihub.workshop.iam.application.dto.LoginUserResponse;
import com.unihub.workshop.iam.application.dto.RefreshRequest;
import com.unihub.workshop.iam.application.dto.RefreshResponse;
import com.unihub.workshop.iam.application.dto.RegisterRequest;
import com.unihub.workshop.iam.application.dto.RegisterResponse;
import com.unihub.workshop.iam.application.dto.ResendOtpRequest;
import com.unihub.workshop.iam.application.dto.ResetPasswordRequest;
import com.unihub.workshop.iam.application.dto.VerifyOtpRequest;
import com.unihub.workshop.iam.application.dto.VerifyOtpResponse;
import com.unihub.workshop.iam.domain.events.PasswordResetRequestedEvent;
import com.unihub.workshop.iam.domain.events.UserRegisteredEvent;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserRole;
import com.unihub.workshop.iam.domain.model.UserStatus;
import com.unihub.workshop.iam.domain.repository.OtpGenerator;
import com.unihub.workshop.iam.domain.repository.OtpStore;
import com.unihub.workshop.iam.domain.repository.TokenStore;
import com.unihub.workshop.iam.domain.repository.UserRepository;
import com.unihub.workshop.iam.infrastructure.security.JwtTokenProvider;
import com.unihub.workshop.shared.exception.AppException;
import io.jsonwebtoken.JwtException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthService implements AuthUseCase {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final Duration OTP_TTL = Duration.ofMinutes(5);
    private static final Duration OTP_COOLDOWN = Duration.ofSeconds(60);
    private static final Duration OTP_ATTEMPT_WINDOW = Duration.ofMinutes(5);
    private static final int MAX_OTP_ATTEMPTS = 5;
    private static final int ACCESS_TOKEN_TTL_SECONDS = 900;
    private static final int REFRESH_TOKEN_TTL_SECONDS = 2_592_000;
    private static final int RESET_TOKEN_TTL_SECONDS = 900;
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final Duration LOGIN_LOCKOUT = Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final OtpStore otpStore;
    private final OtpGenerator otpGenerator;
    private final TokenStore tokenStore;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    public AuthService(
            UserRepository userRepository,
            OtpStore otpStore,
            OtpGenerator otpGenerator,
            TokenStore tokenStore,
            JwtTokenProvider jwtTokenProvider,
            PasswordEncoder passwordEncoder,
            ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.otpStore = otpStore;
        this.otpGenerator = otpGenerator;
        this.tokenStore = tokenStore;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public UseCaseResult<RegisterResponse> register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        User existing = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (existing != null && existing.getStatus() == UserStatus.ACTIVE) {
            throw new AppException(HttpStatus.CONFLICT, "Email already active", "email_active");
        }

        if (otpStore.isCooldownActive(email)) {
            throw new AppException(HttpStatus.TOO_MANY_REQUESTS, "OTP cooldown active", "otp_cooldown");
        }

        User user = existing == null ? new User() : existing;
        if (existing == null) {
            user.setId(UUID.randomUUID());
            user.setRole(UserRole.STUDENT);
            user.setFailedLoginAttempts(0);
            user.setLockoutUntil(null);
        }
        user.setEmail(email);
        user.setFullName(request.fullName());
        user.setPhone(request.phone());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(UserStatus.UNVERIFIED);
        user.setTemporaryPassword(false);
        userRepository.save(user);

        String otp = otpGenerator.generateOtp();
        // TEMP TEST ONLY: remove after QA because this exposes the plaintext OTP in logs.
        log.info("TEMP TEST ONLY - Generated registration OTP for {}: {}", email, otp);
        saveOtpAndCooldown(email, otp);
        publishEvent(new UserRegisteredEvent(email, request.fullName()));

        RegisterResponse response = new RegisterResponse(email, UserStatus.UNVERIFIED.name(), true);
        return new UseCaseResult<>(HttpStatus.CREATED, "Registration initiated", response);
    }

    @Override
    @Transactional
    public UseCaseResult<VerifyOtpResponse> verifyOtp(VerifyOtpRequest request) {
        String email = normalizeEmail(request.email());
        String expectedOtp = otpStore.getOtp(email);
        if (expectedOtp == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "OTP expired or missing", "otp_expired");
        }

        if (!expectedOtp.equals(request.otp())) {
            int attempts = otpStore.incrementFailedAttempts(email, OTP_ATTEMPT_WINDOW);
            if (attempts >= MAX_OTP_ATTEMPTS) {
                otpStore.deleteOtp(email);
                otpStore.resetFailedAttempts(email);
                throw new AppException(HttpStatus.TOO_MANY_REQUESTS, "Too many OTP attempts", "otp_attempts");
            }
            throw new AppException(HttpStatus.UNAUTHORIZED, "OTP mismatch", "otp_mismatch");
        }

        otpStore.deleteOtp(email);
        otpStore.resetFailedAttempts(email);

        if ("REGISTER".equals(request.intent())) {
            User user = userRepository.findByEmailIgnoreCase(email)
                    .orElseThrow(() -> new AppException(HttpStatus.CONFLICT, "User state incompatible with intent",
                            "otp_state"));
            if (user.getStatus() != UserStatus.UNVERIFIED) {
                throw new AppException(HttpStatus.CONFLICT, "User state incompatible with intent", "otp_state");
            }
            user.setStatus(UserStatus.ACTIVE);
            userRepository.save(user);

            VerifyOtpResponse response = new VerifyOtpResponse(email, UserStatus.ACTIVE.name(), null, null);
            return new UseCaseResult<>(HttpStatus.OK, "Account verified", response);
        }

        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null || (user.getStatus() != UserStatus.ACTIVE && user.getStatus() != UserStatus.LOCKED)) {
            throw new AppException(HttpStatus.CONFLICT, "User state incompatible with intent", "otp_state");
        }
        String resetToken = UUID.randomUUID().toString();
        tokenStore.storeResetToken(resetToken, user.getId().toString(), Duration.ofSeconds(RESET_TOKEN_TTL_SECONDS));
        VerifyOtpResponse response = new VerifyOtpResponse(null, null, resetToken, RESET_TOKEN_TTL_SECONDS);
        return new UseCaseResult<>(HttpStatus.OK, "Reset token issued", response);
    }

    @Override
    @Transactional
    public UseCaseResult<Void> resendOtp(ResendOtpRequest request) {
        String email = normalizeEmail(request.email());
        if (otpStore.isCooldownActive(email)) {
            throw new AppException(HttpStatus.TOO_MANY_REQUESTS, "OTP cooldown active", "otp_cooldown");
        }

        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) {
            throw new AppException(HttpStatus.NOT_FOUND, "Email not found", "not_found");
        }

        String intent = request.intent();
        if ("REGISTER".equals(intent)) {
            if (user.getStatus() != UserStatus.UNVERIFIED) {
                throw new AppException(HttpStatus.NOT_FOUND, "Email not found", "not_found");
            }
            String otp = otpGenerator.generateOtp();
            // TEMP TEST ONLY: remove after QA because this exposes the plaintext OTP in logs.
            log.info("TEMP TEST ONLY - Generated registration OTP resend for {}: {}", email, otp);
            saveOtpAndCooldown(email, otp);
            publishEvent(new UserRegisteredEvent(email, user.getFullName()));
            return new UseCaseResult<>(HttpStatus.OK, "A new OTP has been sent", null);
        }

        if (user.getStatus() != UserStatus.ACTIVE && user.getStatus() != UserStatus.LOCKED) {
            throw new AppException(HttpStatus.NOT_FOUND, "Email not found", "not_found");
        }
        String otp = otpGenerator.generateOtp();
        // TEMP TEST ONLY: remove after QA because this exposes the plaintext OTP in logs.
        log.info("TEMP TEST ONLY - Generated forgot-password OTP for {}: {}", email, otp);
        saveOtpAndCooldown(email, otp);
        publishEvent(new PasswordResetRequestedEvent(email));
        return new UseCaseResult<>(HttpStatus.OK, "A new OTP has been sent", null);
    }

    @Override
    public UseCaseResult<LoginResponse> login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(
                        () -> new AppException(HttpStatus.UNAUTHORIZED, "Invalid credentials", "invalid_credentials"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AppException(HttpStatus.FORBIDDEN, "User status is not active", "user_status");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (user.getLockoutUntil() != null && user.getLockoutUntil().isAfter(now)) {
            throw new AppException(HttpStatus.TOO_MANY_REQUESTS, "Temporary lockout active", "lockout");
        }

        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            int failures = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(failures);
            if (failures >= MAX_LOGIN_ATTEMPTS) {
                user.setLockoutUntil(now.plus(LOGIN_LOCKOUT));
                userRepository.save(user);
                throw new AppException(HttpStatus.TOO_MANY_REQUESTS, "Temporary lockout active", "lockout");
            }
            userRepository.save(user);
            throw new AppException(HttpStatus.UNAUTHORIZED, "Invalid credentials", "invalid_credentials");
        }

        if (user.isTemporaryPassword()) {
            String accessToken = jwtTokenProvider.generateRestrictedAccessToken(user);
            LoginResponse response = new LoginResponse(
                    accessToken,
                    null,
                    "Bearer",
                    ACCESS_TOKEN_TTL_SECONDS,
                    null,
                    toLoginUserResponse(user));
            return new UseCaseResult<>(HttpStatus.OK, "Password change required", response);
        }

        user.setFailedLoginAttempts(0);
        user.setLockoutUntil(null);
        user.setLastLoginAt(now);
        userRepository.save(user);

        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);
        LoginResponse response = new LoginResponse(
                accessToken,
                refreshToken,
                "Bearer",
                ACCESS_TOKEN_TTL_SECONDS,
                REFRESH_TOKEN_TTL_SECONDS,
                toLoginUserResponse(user));
        return new UseCaseResult<>(HttpStatus.OK, "Login successful", response);
    }

    @Override
    @Transactional
    public UseCaseResult<RefreshResponse> refresh(RefreshRequest request) {
        JwtTokenProvider.TokenClaims claims = parseRefreshToken(
                request.refreshToken(),
                HttpStatus.UNAUTHORIZED,
                "Invalid refresh token",
                "invalid_refresh_token");

        if (tokenStore.isRefreshTokenBlacklisted(claims.jti())) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Invalid refresh token", "invalid_refresh_token");
        }

        Instant revokedAfter = tokenStore.getRefreshTokensRevokedAfter(claims.subject());
        if (revokedAfter != null && (claims.issuedAt() == null || claims.issuedAt().isBefore(revokedAfter))) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Invalid refresh token", "invalid_refresh_token");
        }

        User user = loadUserById(claims.subject(), HttpStatus.FORBIDDEN);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AppException(HttpStatus.FORBIDDEN, "User status is not active", "user_status");
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);
        tokenStore.blacklistRefreshToken(claims.jti(), remainingTtl(claims.expiresAt()));

        RefreshResponse response = new RefreshResponse(
                accessToken,
                refreshToken,
                "Bearer",
                ACCESS_TOKEN_TTL_SECONDS,
                REFRESH_TOKEN_TTL_SECONDS,
                true);
        return new UseCaseResult<>(HttpStatus.OK, "Token refreshed", response);
    }

    @Override
    @Transactional
    public UseCaseResult<Void> logout(String userId, com.unihub.workshop.iam.application.dto.LogoutRequest request,
            String accessToken) {
        // Blacklist the access token if provided
        if (accessToken != null && !accessToken.isBlank()) {
            try {
                JwtTokenProvider.TokenClaims accessTokenClaims = jwtTokenProvider.parseToken(accessToken);
                if (accessTokenClaims.expiresAt() != null) {
                    Duration ttl = remainingTtl(accessTokenClaims.expiresAt());
                    tokenStore.blacklistAccessToken(accessTokenClaims.jti(), ttl);
                }
            } catch (JwtException | IllegalArgumentException ex) {
                // Log but don't fail logout if access token is invalid
                System.err.println("Failed to parse access token for blacklisting: " + ex.getMessage());
            }
        }

        // Blacklist the refresh token if provided
        String refreshToken = request == null ? null : request.refreshToken();
        if (refreshToken == null || refreshToken.isBlank()) {
            return new UseCaseResult<>(HttpStatus.OK, "Logged out", null);
        }

        JwtTokenProvider.TokenClaims claims = parseRefreshToken(
                refreshToken,
                HttpStatus.BAD_REQUEST,
                "Invalid refresh token",
                "invalid_refresh_token");
        if (userId != null && !userId.isBlank() && !userId.equals(claims.subject())) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Invalid refresh token", "invalid_refresh_token");
        }
        tokenStore.blacklistRefreshToken(claims.jti(), remainingTtl(claims.expiresAt()));
        return new UseCaseResult<>(HttpStatus.OK, "Logged out", null);
    }

    @Override
    @Transactional
    public UseCaseResult<ForgotPasswordResponse> forgotPassword(ForgotPasswordRequest request) {
        String email = normalizeEmail(request.email());
        if (otpStore.isCooldownActive(email)) {
            throw new AppException(HttpStatus.TOO_MANY_REQUESTS, "OTP cooldown active", "otp_cooldown");
        }

        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null || (user.getStatus() != UserStatus.ACTIVE && user.getStatus() != UserStatus.LOCKED)) {
            return new UseCaseResult<>(
                    HttpStatus.OK,
                    "If the account exists, a reset code was sent",
                    new ForgotPasswordResponse(true));
        }

        String otp = otpGenerator.generateOtp();
        // TEMP TEST ONLY: remove after QA because this exposes the plaintext OTP in logs.
        log.info("TEMP TEST ONLY - Generated forgot-password OTP for {}: {}", email, otp);
        saveOtpAndCooldown(email, otp);
        publishEvent(new PasswordResetRequestedEvent(email));

        return new UseCaseResult<>(
                HttpStatus.OK,
                "If the account exists, a reset code was sent",
                new ForgotPasswordResponse(true));
    }

    @Override
    @Transactional
    public UseCaseResult<Void> resetPassword(ResetPasswordRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Passwords do not match", "password_mismatch");
        }

        String tokenUserId = tokenStore.consumeResetToken(request.resetToken());
        if (tokenUserId == null || tokenUserId.isBlank()) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Invalid or expired reset token", "reset_token");
        }

        User user = loadUserById(tokenUserId, HttpStatus.UNAUTHORIZED);

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setTemporaryPassword(false);
        user.setFailedLoginAttempts(0);
        user.setLockoutUntil(null);
        if (user.getStatus() == UserStatus.LOCKED) {
            user.setStatus(UserStatus.ACTIVE);
        }
        userRepository.save(user);
        revokeRefreshTokens(user);

        return new UseCaseResult<>(HttpStatus.OK, "Password reset successful", null);
    }

    @Override
    @Transactional
    public UseCaseResult<ChangePasswordResponse> changePassword(String userId, ChangePasswordRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Passwords do not match", "password_mismatch");
        }

        User user = loadUserById(userId, HttpStatus.UNAUTHORIZED);
        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Current password mismatch", "invalid_credentials");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setTemporaryPassword(false);
        userRepository.save(user);
        revokeRefreshTokens(user);

        return new UseCaseResult<>(HttpStatus.OK, "Password changed", new ChangePasswordResponse(false));
    }

    private LoginUserResponse toLoginUserResponse(User user) {
        return new LoginUserResponse(
                user.getId() == null ? null : user.getId().toString(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getRole() == null ? null : user.getRole().name(),
                user.getStatus() == null ? null : user.getStatus().name(),
                user.getOrganizationId() == null ? null : user.getOrganizationId().toString(),
                user.getStudentId(),
                user.isTemporaryPassword());
    }

    private void saveOtpAndCooldown(String email, String otp) {
        try {
            otpStore.saveOtp(email, otp, OTP_TTL);
            otpStore.markCooldown(email, OTP_COOLDOWN);
        } catch (AppException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "OTP infrastructure unavailable", "otp_infra");
        }
    }

    private void publishEvent(Object event) {
        try {
            eventPublisher.publishEvent(event);
        } catch (RuntimeException ex) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "Event publication failed", "event_infra");
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private JwtTokenProvider.TokenClaims parseRefreshToken(
            String refreshToken,
            HttpStatus status,
            String message,
            String errorCode) {
        JwtTokenProvider.TokenClaims claims = parseToken(refreshToken, status, message, errorCode);
        if (!"refresh".equals(claims.type())) {
            throw new AppException(status, message, errorCode);
        }
        return claims;
    }

    private JwtTokenProvider.TokenClaims parseToken(
            String token,
            HttpStatus status,
            String message,
            String errorCode) {
        if (token == null || token.isBlank()) {
            throw new AppException(status, message, errorCode);
        }
        try {
            JwtTokenProvider.TokenClaims claims = jwtTokenProvider.parseToken(token);
            if (claims.subject() == null || claims.subject().isBlank()
                    || claims.jti() == null || claims.jti().isBlank()) {
                throw new AppException(status, message, errorCode);
            }
            return claims;
        } catch (JwtException | IllegalArgumentException ex) {
            throw new AppException(status, message, errorCode);
        }
    }

    private User loadUserById(String rawId, HttpStatus missingStatus) {
        if (rawId == null || rawId.isBlank()) {
            throw new AppException(missingStatus, "Invalid token subject", "invalid_token");
        }
        try {
            UUID userId = UUID.fromString(rawId);
            return userRepository.findById(userId)
                    .orElseThrow(() -> new AppException(missingStatus, "User not found", "user_not_found"));
        } catch (IllegalArgumentException ex) {
            throw new AppException(missingStatus, "Invalid token subject", "invalid_token");
        }
    }

    private Duration remainingTtl(Instant expiresAt) {
        if (expiresAt == null) {
            return Duration.ZERO;
        }
        Instant now = Instant.now();
        if (expiresAt.isBefore(now)) {
            return Duration.ZERO;
        }
        return Duration.between(now, expiresAt);
    }

    private void revokeRefreshTokens(User user) {
        if (user == null || user.getId() == null) {
            return;
        }
        tokenStore.revokeRefreshTokens(
                user.getId().toString(),
                Instant.now(),
                Duration.ofSeconds(REFRESH_TOKEN_TTL_SECONDS));
    }
}
