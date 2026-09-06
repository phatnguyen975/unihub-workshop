package com.unihub.workshop.iam.application.usecases;

import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.application.ports.out.UserVerificationTokenRepository;
import com.unihub.workshop.iam.application.utils.OtpService;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserRole;
import com.unihub.workshop.iam.domain.model.UserVerificationToken;
import com.unihub.workshop.shared.exception.ConflictException;
import com.unihub.workshop.shared.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ActivateAccountUseCaseImplTest {

    private UserRepository userRepository;
    private UserVerificationTokenRepository userVerificationTokenRepository;
    private OtpService otpService;
    private ActivateAccountUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userVerificationTokenRepository = mock(UserVerificationTokenRepository.class);
        otpService = mock(OtpService.class);
        useCase = new ActivateAccountUseCaseImpl(userRepository, userVerificationTokenRepository, otpService);
    }

    @Test
    void shouldActivateAccountSuccessfully() {
        String email = "student@university.edu";
        String otp = "123456";
        UUID userId = UUID.randomUUID();
        User inactiveUser = new User(userId, UUID.randomUUID(), UserRole.STUDENT, email, "hash", false, false, null);
        UserVerificationToken token = UserVerificationToken.createActivationOtp(userId, "tokenHash",
                Instant.now().plus(15, ChronoUnit.MINUTES));

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(inactiveUser));
        when(userVerificationTokenRepository.findLatestByUserIdAndType(userId,
                UserVerificationToken.TYPE_ACTIVATION_OTP))
                .thenReturn(Optional.of(token));
        when(otpService.verifyOtp(otp, "tokenHash")).thenReturn(true);
        when(userVerificationTokenRepository.markConsumed(token.id())).thenReturn(true);

        useCase.activate(email, otp);

        verify(userVerificationTokenRepository).markConsumed(token.id());
        verify(userRepository).activate(userId);
    }

    @Test
    void shouldThrowValidationExceptionWhenMarkConsumedFailsDueToRaceCondition() {
        String email = "student@university.edu";
        String otp = "123456";
        UUID userId = UUID.randomUUID();
        User inactiveUser = new User(userId, UUID.randomUUID(), UserRole.STUDENT, email, "hash", false, false, null);
        UserVerificationToken token = UserVerificationToken.createActivationOtp(userId, "tokenHash",
                Instant.now().plus(15, ChronoUnit.MINUTES));

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(inactiveUser));
        when(userVerificationTokenRepository.findLatestByUserIdAndType(userId,
                UserVerificationToken.TYPE_ACTIVATION_OTP))
                .thenReturn(Optional.of(token));
        when(otpService.verifyOtp(otp, "tokenHash")).thenReturn(true);
        when(userVerificationTokenRepository.markConsumed(token.id())).thenReturn(false);

        assertThatThrownBy(() -> useCase.activate(email, otp))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Invalid or expired activation code.");

        verify(userRepository, never()).activate(any());
    }

    @Test
    void shouldThrowValidationExceptionWhenUserNotFound() {
        String email = "nonexistent@university.edu";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.activate(email, "123456"))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Invalid or expired activation code.");

        verify(userRepository, never()).activate(any());
        verify(userVerificationTokenRepository, never()).markConsumed(any());
    }

    @Test
    void shouldThrowConflictExceptionWhenAccountAlreadyActive() {
        String email = "active@university.edu";
        UUID userId = UUID.randomUUID();
        User activeUser = new User(userId, UUID.randomUUID(), UserRole.STUDENT, email, "hash", true, false, null);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> useCase.activate(email, "123456"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Account is already active");

        verify(userRepository, never()).activate(any());
        verify(userVerificationTokenRepository, never()).markConsumed(any());
    }

    @Test
    void shouldThrowValidationExceptionWhenNoActiveTokenFound() {
        String email = "student@university.edu";
        UUID userId = UUID.randomUUID();
        User inactiveUser = new User(userId, UUID.randomUUID(), UserRole.STUDENT, email, "hash", false, false, null);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(inactiveUser));
        when(userVerificationTokenRepository.findLatestByUserIdAndType(userId,
                UserVerificationToken.TYPE_ACTIVATION_OTP))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.activate(email, "123456"))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Invalid or expired activation code.");

        verify(userRepository, never()).activate(any());
    }

    @Test
    void shouldThrowValidationExceptionWhenTokenExpired() {
        String email = "student@university.edu";
        UUID userId = UUID.randomUUID();
        User inactiveUser = new User(userId, UUID.randomUUID(), UserRole.STUDENT, email, "hash", false, false, null);
        UserVerificationToken expiredToken = UserVerificationToken.createActivationOtp(userId, "tokenHash",
                Instant.now().minusSeconds(10));

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(inactiveUser));
        when(userVerificationTokenRepository.findLatestByUserIdAndType(userId,
                UserVerificationToken.TYPE_ACTIVATION_OTP))
                .thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> useCase.activate(email, "123456"))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Invalid or expired activation code.");

        verify(userRepository, never()).activate(any());
        verify(userVerificationTokenRepository, never()).markConsumed(any());
    }

    @Test
    void shouldThrowValidationExceptionWhenTokenAlreadyConsumed() {
        String email = "student@university.edu";
        UUID userId = UUID.randomUUID();
        User inactiveUser = new User(userId, UUID.randomUUID(), UserRole.STUDENT, email, "hash", false, false, null);
        UserVerificationToken token = new UserVerificationToken(
                UUID.randomUUID(),
                userId,
                UserVerificationToken.TYPE_ACTIVATION_OTP,
                "tokenHash",
                Instant.now().plusSeconds(600),
                Instant.now(),
                Instant.now(),
                Instant.now(),
                null,
                null);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(inactiveUser));
        when(userVerificationTokenRepository.findLatestByUserIdAndType(userId,
                UserVerificationToken.TYPE_ACTIVATION_OTP))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> useCase.activate(email, "123456"))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Invalid or expired activation code.");

        verify(userRepository, never()).activate(any());
        verify(userVerificationTokenRepository, never()).markConsumed(any());
    }

    @Test
    void shouldThrowValidationExceptionWhenOtpMismatch() {
        String email = "student@university.edu";
        UUID userId = UUID.randomUUID();
        User inactiveUser = new User(userId, UUID.randomUUID(), UserRole.STUDENT, email, "hash", false, false, null);
        UserVerificationToken token = UserVerificationToken.createActivationOtp(userId, "tokenHash",
                Instant.now().plusSeconds(600));

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(inactiveUser));
        when(userVerificationTokenRepository.findLatestByUserIdAndType(userId,
                UserVerificationToken.TYPE_ACTIVATION_OTP))
                .thenReturn(Optional.of(token));
        when(otpService.verifyOtp("000000", "tokenHash")).thenReturn(false);

        assertThatThrownBy(() -> useCase.activate(email, "000000"))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Invalid or expired activation code.");

        verify(userRepository, never()).activate(any());
        verify(userVerificationTokenRepository, never()).markConsumed(any());
    }
}
