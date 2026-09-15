package com.unihub.workshop.iam.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.unihub.workshop.iam.application.ports.out.RefreshTokenRepository;
import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.application.ports.out.UserVerificationTokenRepository;
import com.unihub.workshop.iam.application.utils.OtpService;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserRole;
import com.unihub.workshop.iam.domain.model.UserVerificationToken;
import com.unihub.workshop.shared.exception.ValidationException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

class ResetPasswordUseCaseImplTest {

    private UserRepository userRepository;
    private UserVerificationTokenRepository userVerificationTokenRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private OtpService otpService;
    private PasswordEncoder passwordEncoder;
    private MeterRegistry meterRegistry;
    private ResetPasswordUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userVerificationTokenRepository = mock(
            UserVerificationTokenRepository.class
        );
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        otpService = mock(OtpService.class);
        passwordEncoder = mock(PasswordEncoder.class);
        meterRegistry = new SimpleMeterRegistry();

        when(otpService.hashOtp("000000")).thenReturn("dummyHash");

        useCase = new ResetPasswordUseCaseImpl(
            userRepository,
            userVerificationTokenRepository,
            refreshTokenRepository,
            otpService,
            passwordEncoder,
            meterRegistry
        );
    }

    @Test
    void shouldResetPasswordSuccessfullyWhenOtpValid() {
        String email = "student@university.edu";
        String otp = "849201";
        String newPassword = "BrandNewSecurePassword2026!";
        UUID userId = UUID.randomUUID();
        User activeUser = User.builder()
            .id(userId)
            .organizationId(UUID.randomUUID())
            .role(UserRole.STUDENT)
            .email(email)
            .passwordHash("oldHash")
            .isActive(true)
            .isTemporaryPassword(true)
            .lastLoginAt(null)
            .build();

        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(15));
        UserVerificationToken token = new UserVerificationToken(
            UUID.randomUUID(),
            userId,
            UserVerificationToken.TYPE_PASSWORD_RESET_OTP,
            "tokenHash123",
            expiresAt,
            null,
            Instant.now(),
            Instant.now(),
            null,
            null
        );

        when(userRepository.findByEmail(email)).thenReturn(
            Optional.of(activeUser)
        );
        when(
            userVerificationTokenRepository.findLatestByUserIdAndType(
                userId,
                UserVerificationToken.TYPE_PASSWORD_RESET_OTP
            )
        ).thenReturn(Optional.of(token));
        when(otpService.verifyOtp(otp, "tokenHash123")).thenReturn(true);
        when(
            userVerificationTokenRepository.markConsumed(token.id())
        ).thenReturn(true);
        when(passwordEncoder.encode(newPassword)).thenReturn("argon2NewHash");

        useCase.resetPassword(email, otp, newPassword);

        verify(userVerificationTokenRepository).markConsumed(token.id());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.passwordHash()).isEqualTo("argon2NewHash");
        assertThat(savedUser.isTemporaryPassword()).isFalse();

        verify(refreshTokenRepository).revokeAllActiveTokensForUser(
            eq(userId),
            any(Instant.class)
        );
        assertThat(
            meterRegistry
                .counter("auth.password.reset.attempts", "outcome", "success")
                .count()
        ).isEqualTo(1.0);
    }

    @Test
    void shouldThrowValidationExceptionWhenMarkConsumedFailsDueToRaceCondition() {
        String email = "student@university.edu";
        String otp = "849201";
        String newPassword = "BrandNewSecurePassword2026!";
        UUID userId = UUID.randomUUID();
        User activeUser = User.builder()
            .id(userId)
            .organizationId(UUID.randomUUID())
            .role(UserRole.STUDENT)
            .email(email)
            .passwordHash("oldHash")
            .isActive(true)
            .isTemporaryPassword(true)
            .lastLoginAt(null)
            .build();

        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(15));
        UserVerificationToken token = new UserVerificationToken(
            UUID.randomUUID(),
            userId,
            UserVerificationToken.TYPE_PASSWORD_RESET_OTP,
            "tokenHash123",
            expiresAt,
            null,
            Instant.now(),
            Instant.now(),
            null,
            null
        );

        when(userRepository.findByEmail(email)).thenReturn(
            Optional.of(activeUser)
        );
        when(
            userVerificationTokenRepository.findLatestByUserIdAndType(
                userId,
                UserVerificationToken.TYPE_PASSWORD_RESET_OTP
            )
        ).thenReturn(Optional.of(token));
        when(otpService.verifyOtp(otp, "tokenHash123")).thenReturn(true);
        when(
            userVerificationTokenRepository.markConsumed(token.id())
        ).thenReturn(false);

        assertThatThrownBy(() -> useCase.resetPassword(email, otp, newPassword))
            .isInstanceOf(ValidationException.class)
            .hasMessage("Invalid or expired verification code.");

        verify(userRepository, never()).save(any());
        verify(refreshTokenRepository, never()).revokeAllActiveTokensForUser(
            any(),
            any()
        );
        assertThat(
            meterRegistry
                .counter(
                    "auth.password.reset.attempts",
                    "outcome",
                    "failure",
                    "reason",
                    "already_consumed"
                )
                .count()
        ).isEqualTo(1.0);
    }

    @Test
    void shouldThrowValidationExceptionWhenUserNotFound() {
        String email = "unknown@university.edu";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            useCase.resetPassword(email, "123456", "Pass1234!")
        )
            .isInstanceOf(ValidationException.class)
            .hasMessage("Invalid or expired verification code.");

        verify(otpService).verifyOtp("123456", "dummyHash");
        verify(userVerificationTokenRepository, never()).markConsumed(any());
        verify(userRepository, never()).save(any());
        verify(refreshTokenRepository, never()).revokeAllActiveTokensForUser(
            any(),
            any()
        );
        assertThat(
            meterRegistry
                .counter(
                    "auth.password.reset.attempts",
                    "outcome",
                    "failure",
                    "reason",
                    "user_not_found"
                )
                .count()
        ).isEqualTo(1.0);
    }

    @Test
    void shouldThrowValidationExceptionWhenUserInactive() {
        String email = "inactive@university.edu";
        UUID userId = UUID.randomUUID();
        User inactiveUser = User.builder()
            .id(userId)
            .organizationId(null)
            .role(UserRole.STUDENT)
            .email(email)
            .passwordHash("hash")
            .isActive(false)
            .isTemporaryPassword(false)
            .lastLoginAt(null)
            .build();
        when(userRepository.findByEmail(email)).thenReturn(
            Optional.of(inactiveUser)
        );

        assertThatThrownBy(() ->
            useCase.resetPassword(email, "123456", "Pass1234!")
        )
            .isInstanceOf(ValidationException.class)
            .hasMessage("Invalid or expired verification code.");

        verify(otpService).verifyOtp("123456", "dummyHash");
        verify(userVerificationTokenRepository, never()).markConsumed(any());
        verify(userRepository, never()).save(any());
        assertThat(
            meterRegistry
                .counter(
                    "auth.password.reset.attempts",
                    "outcome",
                    "failure",
                    "reason",
                    "inactive_user"
                )
                .count()
        ).isEqualTo(1.0);
    }

    @Test
    void shouldThrowValidationExceptionWhenTokenNotFound() {
        String email = "active@university.edu";
        UUID userId = UUID.randomUUID();
        User activeUser = User.builder()
            .id(userId)
            .organizationId(null)
            .role(UserRole.STUDENT)
            .email(email)
            .passwordHash("hash")
            .isActive(true)
            .isTemporaryPassword(false)
            .lastLoginAt(null)
            .build();

        when(userRepository.findByEmail(email)).thenReturn(
            Optional.of(activeUser)
        );
        when(
            userVerificationTokenRepository.findLatestByUserIdAndType(
                userId,
                UserVerificationToken.TYPE_PASSWORD_RESET_OTP
            )
        ).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            useCase.resetPassword(email, "123456", "Pass1234!")
        )
            .isInstanceOf(ValidationException.class)
            .hasMessage("Invalid or expired verification code.");

        verify(userVerificationTokenRepository, never()).markConsumed(any());
    }

    @Test
    void shouldThrowValidationExceptionWhenTokenAlreadyConsumed() {
        String email = "active@university.edu";
        UUID userId = UUID.randomUUID();
        User activeUser = User.builder()
            .id(userId)
            .organizationId(null)
            .role(UserRole.STUDENT)
            .email(email)
            .passwordHash("hash")
            .isActive(true)
            .isTemporaryPassword(false)
            .lastLoginAt(null)
            .build();

        UserVerificationToken consumedToken = new UserVerificationToken(
            UUID.randomUUID(),
            userId,
            UserVerificationToken.TYPE_PASSWORD_RESET_OTP,
            "tokenHash123",
            Instant.now().plus(Duration.ofMinutes(15)),
            Instant.now(),
            Instant.now(),
            Instant.now(),
            null,
            null
        );

        when(userRepository.findByEmail(email)).thenReturn(
            Optional.of(activeUser)
        );
        when(
            userVerificationTokenRepository.findLatestByUserIdAndType(
                userId,
                UserVerificationToken.TYPE_PASSWORD_RESET_OTP
            )
        ).thenReturn(Optional.of(consumedToken));

        assertThatThrownBy(() ->
            useCase.resetPassword(email, "123456", "Pass1234!")
        )
            .isInstanceOf(ValidationException.class)
            .hasMessage("Invalid or expired verification code.");

        verify(userVerificationTokenRepository, never()).markConsumed(any());
    }

    @Test
    void shouldThrowValidationExceptionWhenTokenExpired() {
        String email = "active@university.edu";
        UUID userId = UUID.randomUUID();
        User activeUser = User.builder()
            .id(userId)
            .organizationId(null)
            .role(UserRole.STUDENT)
            .email(email)
            .passwordHash("hash")
            .isActive(true)
            .isTemporaryPassword(false)
            .lastLoginAt(null)
            .build();

        UserVerificationToken expiredToken = new UserVerificationToken(
            UUID.randomUUID(),
            userId,
            UserVerificationToken.TYPE_PASSWORD_RESET_OTP,
            "tokenHash123",
            Instant.now().minus(Duration.ofMinutes(1)),
            null,
            Instant.now(),
            Instant.now(),
            null,
            null
        );

        when(userRepository.findByEmail(email)).thenReturn(
            Optional.of(activeUser)
        );
        when(
            userVerificationTokenRepository.findLatestByUserIdAndType(
                userId,
                UserVerificationToken.TYPE_PASSWORD_RESET_OTP
            )
        ).thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() ->
            useCase.resetPassword(email, "123456", "Pass1234!")
        )
            .isInstanceOf(ValidationException.class)
            .hasMessage("Invalid or expired verification code.");

        verify(userVerificationTokenRepository, never()).markConsumed(any());
    }

    @Test
    void shouldThrowValidationExceptionWhenOtpMismatch() {
        String email = "active@university.edu";
        UUID userId = UUID.randomUUID();
        User activeUser = User.builder()
            .id(userId)
            .organizationId(null)
            .role(UserRole.STUDENT)
            .email(email)
            .passwordHash("hash")
            .isActive(true)
            .isTemporaryPassword(false)
            .lastLoginAt(null)
            .build();

        UserVerificationToken token = new UserVerificationToken(
            UUID.randomUUID(),
            userId,
            UserVerificationToken.TYPE_PASSWORD_RESET_OTP,
            "tokenHash123",
            Instant.now().plus(Duration.ofMinutes(15)),
            null,
            Instant.now(),
            Instant.now(),
            null,
            null
        );

        when(userRepository.findByEmail(email)).thenReturn(
            Optional.of(activeUser)
        );
        when(
            userVerificationTokenRepository.findLatestByUserIdAndType(
                userId,
                UserVerificationToken.TYPE_PASSWORD_RESET_OTP
            )
        ).thenReturn(Optional.of(token));
        when(otpService.verifyOtp("999999", "tokenHash123")).thenReturn(false);

        assertThatThrownBy(() ->
            useCase.resetPassword(email, "999999", "Pass1234!")
        )
            .isInstanceOf(ValidationException.class)
            .hasMessage("Invalid or expired verification code.");

        verify(userVerificationTokenRepository, never()).markConsumed(any());
    }
}
