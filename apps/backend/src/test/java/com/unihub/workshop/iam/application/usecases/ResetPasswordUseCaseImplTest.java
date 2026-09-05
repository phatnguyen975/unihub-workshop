package com.unihub.workshop.iam.application.usecases;

import com.unihub.workshop.iam.application.ports.out.RefreshTokenRepository;
import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.application.ports.out.UserVerificationTokenRepository;
import com.unihub.workshop.iam.application.utils.OtpService;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserRole;
import com.unihub.workshop.iam.domain.model.UserVerificationToken;
import com.unihub.workshop.shared.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ResetPasswordUseCaseImplTest {

    private UserRepository userRepository;
    private UserVerificationTokenRepository userVerificationTokenRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private OtpService otpService;
    private PasswordEncoder passwordEncoder;
    private ResetPasswordUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userVerificationTokenRepository = mock(UserVerificationTokenRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        otpService = mock(OtpService.class);
        passwordEncoder = mock(PasswordEncoder.class);

        useCase = new ResetPasswordUseCaseImpl(
                userRepository,
                userVerificationTokenRepository,
                refreshTokenRepository,
                otpService,
                passwordEncoder);
    }

    @Test
    void shouldResetPasswordSuccessfullyWhenOtpValid() {
        String email = "student@university.edu";
        String otp = "849201";
        String newPassword = "BrandNewSecurePassword2026!";
        UUID userId = UUID.randomUUID();
        User activeUser = new User(userId, UUID.randomUUID(), UserRole.STUDENT, email, "oldHash", true, true, null);

        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(15));
        UserVerificationToken token = new UserVerificationToken(
                UUID.randomUUID(), userId, UserVerificationToken.TYPE_PASSWORD_RESET_OTP,
                "tokenHash123", expiresAt, null, Instant.now(), Instant.now(), null, null);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(activeUser));
        when(userVerificationTokenRepository.findLatestByUserIdAndType(userId,
                UserVerificationToken.TYPE_PASSWORD_RESET_OTP))
                .thenReturn(Optional.of(token));
        when(otpService.verifyOtp(otp, "tokenHash123")).thenReturn(true);
        when(passwordEncoder.encode(newPassword)).thenReturn("argon2NewHash");

        useCase.resetPassword(email, otp, newPassword);

        verify(userVerificationTokenRepository).markConsumed(token.id());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.passwordHash()).isEqualTo("argon2NewHash");
        assertThat(savedUser.isTemporaryPassword()).isFalse();

        verify(refreshTokenRepository).revokeAllActiveTokensForUser(eq(userId), any(Instant.class));
    }

    @Test
    void shouldThrowValidationExceptionWhenUserNotFound() {
        String email = "unknown@university.edu";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.resetPassword(email, "123456", "Pass1234!"))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Invalid or expired verification code.");

        verify(userVerificationTokenRepository, never()).markConsumed(any());
        verify(userRepository, never()).save(any());
        verify(refreshTokenRepository, never()).revokeAllActiveTokensForUser(any(), any());
    }

    @Test
    void shouldThrowValidationExceptionWhenUserInactive() {
        String email = "inactive@university.edu";
        UUID userId = UUID.randomUUID();
        User inactiveUser = new User(userId, null, UserRole.STUDENT, email, "hash", false, false, null);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(inactiveUser));

        assertThatThrownBy(() -> useCase.resetPassword(email, "123456", "Pass1234!"))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Invalid or expired verification code.");

        verify(userVerificationTokenRepository, never()).markConsumed(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldThrowValidationExceptionWhenTokenNotFound() {
        String email = "active@university.edu";
        UUID userId = UUID.randomUUID();
        User activeUser = new User(userId, null, UserRole.STUDENT, email, "hash", true, false, null);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(activeUser));
        when(userVerificationTokenRepository.findLatestByUserIdAndType(userId,
                UserVerificationToken.TYPE_PASSWORD_RESET_OTP))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.resetPassword(email, "123456", "Pass1234!"))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Invalid or expired verification code.");

        verify(userVerificationTokenRepository, never()).markConsumed(any());
    }

    @Test
    void shouldThrowValidationExceptionWhenTokenAlreadyConsumed() {
        String email = "active@university.edu";
        UUID userId = UUID.randomUUID();
        User activeUser = new User(userId, null, UserRole.STUDENT, email, "hash", true, false, null);

        UserVerificationToken consumedToken = new UserVerificationToken(
                UUID.randomUUID(), userId, UserVerificationToken.TYPE_PASSWORD_RESET_OTP,
                "tokenHash123", Instant.now().plus(Duration.ofMinutes(15)), Instant.now(), Instant.now(), Instant.now(),
                null, null);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(activeUser));
        when(userVerificationTokenRepository.findLatestByUserIdAndType(userId,
                UserVerificationToken.TYPE_PASSWORD_RESET_OTP))
                .thenReturn(Optional.of(consumedToken));

        assertThatThrownBy(() -> useCase.resetPassword(email, "123456", "Pass1234!"))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Invalid or expired verification code.");

        verify(userVerificationTokenRepository, never()).markConsumed(any());
    }

    @Test
    void shouldThrowValidationExceptionWhenTokenExpired() {
        String email = "active@university.edu";
        UUID userId = UUID.randomUUID();
        User activeUser = new User(userId, null, UserRole.STUDENT, email, "hash", true, false, null);

        UserVerificationToken expiredToken = new UserVerificationToken(
                UUID.randomUUID(), userId, UserVerificationToken.TYPE_PASSWORD_RESET_OTP,
                "tokenHash123", Instant.now().minus(Duration.ofMinutes(1)), null, Instant.now(), Instant.now(), null,
                null);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(activeUser));
        when(userVerificationTokenRepository.findLatestByUserIdAndType(userId,
                UserVerificationToken.TYPE_PASSWORD_RESET_OTP))
                .thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> useCase.resetPassword(email, "123456", "Pass1234!"))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Invalid or expired verification code.");

        verify(userVerificationTokenRepository, never()).markConsumed(any());
    }

    @Test
    void shouldThrowValidationExceptionWhenOtpMismatch() {
        String email = "active@university.edu";
        UUID userId = UUID.randomUUID();
        User activeUser = new User(userId, null, UserRole.STUDENT, email, "hash", true, false, null);

        UserVerificationToken token = new UserVerificationToken(
                UUID.randomUUID(), userId, UserVerificationToken.TYPE_PASSWORD_RESET_OTP,
                "tokenHash123", Instant.now().plus(Duration.ofMinutes(15)), null, Instant.now(), Instant.now(), null,
                null);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(activeUser));
        when(userVerificationTokenRepository.findLatestByUserIdAndType(userId,
                UserVerificationToken.TYPE_PASSWORD_RESET_OTP))
                .thenReturn(Optional.of(token));
        when(otpService.verifyOtp("999999", "tokenHash123")).thenReturn(false);

        assertThatThrownBy(() -> useCase.resetPassword(email, "999999", "Pass1234!"))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Invalid or expired verification code.");

        verify(userVerificationTokenRepository, never()).markConsumed(any());
    }
}
