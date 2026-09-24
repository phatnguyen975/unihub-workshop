package com.unihub.workshop.iam.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.unihub.workshop.iam.application.ports.out.JtiBlacklistPort;
import com.unihub.workshop.iam.application.ports.out.RefreshTokenRepository;
import com.unihub.workshop.iam.application.ports.out.SessionTrackerPort;
import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.application.ports.out.UserVerificationTokenRepository;
import com.unihub.workshop.iam.application.utils.OtpService;
import com.unihub.workshop.iam.domain.model.RefreshToken;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserRole;
import com.unihub.workshop.iam.domain.model.UserVerificationToken;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.exception.ValidationException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
    private SessionTrackerPort sessionTrackerPort;
    private JtiBlacklistPort jtiBlacklistPort;
    private OtpService otpService;
    private PasswordEncoder passwordEncoder;
    private ResetPasswordUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userVerificationTokenRepository = mock(
            UserVerificationTokenRepository.class
        );
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        sessionTrackerPort = mock(SessionTrackerPort.class);
        jtiBlacklistPort = mock(JtiBlacklistPort.class);
        otpService = mock(OtpService.class);
        passwordEncoder = mock(PasswordEncoder.class);

        useCase = new ResetPasswordUseCaseImpl(
            userRepository,
            userVerificationTokenRepository,
            refreshTokenRepository,
            sessionTrackerPort,
            jtiBlacklistPort,
            otpService,
            passwordEncoder
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

        UUID sessionId = UUID.randomUUID();
        RefreshToken activeSession = RefreshToken.builder()
            .id(sessionId)
            .userId(userId)
            .tokenHash("rtHash123")
            .expiresAt(Instant.now().plus(Duration.ofDays(7)))
            .createdAt(Instant.now())
            .build();
        when(
            refreshTokenRepository.findAllActiveSessionsForUser(
                eq(userId),
                any(Instant.class)
            )
        ).thenReturn(List.of(activeSession));
        when(sessionTrackerPort.getActiveJti(sessionId)).thenReturn(
            Optional.of("jti-abc")
        );
        when(sessionTrackerPort.getRemainingTtl(sessionId)).thenReturn(
            Duration.ofMinutes(45)
        );

        useCase.resetPassword(email, otp, newPassword);

        verify(userVerificationTokenRepository).markConsumed(token.id());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.passwordHash()).isEqualTo("argon2NewHash");
        assertThat(savedUser.isTemporaryPassword()).isFalse();

        verify(jtiBlacklistPort).blacklistJti(
            "jti-abc",
            Duration.ofMinutes(45)
        );
        verify(sessionTrackerPort).removeSession(sessionId);

        verify(refreshTokenRepository).revokeAllActiveTokensForUser(
            eq(userId),
            any(Instant.class)
        );
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
    }

    @Test
    void shouldThrowNotFoundExceptionWhenUserNotFound() {
        String email = "unknown@university.edu";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            useCase.resetPassword(email, "123456", "Pass1234!")
        )
            .isInstanceOf(NotFoundException.class)
            .hasMessage("No account found for this email.");

        verify(userVerificationTokenRepository, never()).markConsumed(any());
        verify(userRepository, never()).save(any());
        verify(refreshTokenRepository, never()).revokeAllActiveTokensForUser(
            any(),
            any()
        );
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

        verify(userVerificationTokenRepository, never()).markConsumed(any());
        verify(userRepository, never()).save(any());
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

    @Test
    void shouldNormalizeEmailBeforeLookup() {
        String rawEmail = "  Student.Bob@University.EDU  ";
        String normalizedEmail = "student.bob@university.edu";
        when(userRepository.findByEmail(normalizedEmail)).thenReturn(
            Optional.empty()
        );

        assertThatThrownBy(() ->
            useCase.resetPassword(rawEmail, "123456", "Pass1234!")
        ).isInstanceOf(NotFoundException.class);

        verify(userRepository).findByEmail(normalizedEmail);
    }
}
