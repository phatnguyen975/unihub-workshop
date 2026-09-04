package com.unihub.workshop.iam.application.usecases;

import com.unihub.workshop.iam.application.ports.out.PasswordResetTokenRepository;
import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.application.utils.OtpService;
import com.unihub.workshop.iam.domain.events.OtpRequestedEvent;
import com.unihub.workshop.iam.domain.model.PasswordResetToken;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ResendOtpUseCaseImplTest {

    private UserRepository userRepository;
    private PasswordResetTokenRepository passwordResetTokenRepository;
    private OtpService otpService;
    private ApplicationEventPublisher eventPublisher;
    private ResendOtpUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordResetTokenRepository = mock(PasswordResetTokenRepository.class);
        otpService = mock(OtpService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        useCase = new ResendOtpUseCaseImpl(
                userRepository,
                passwordResetTokenRepository,
                otpService,
                eventPublisher);
    }

    @Test
    void shouldResendOtpSuccessfullyWhenAccountInactive() {
        String email = "student@university.edu";
        UUID userId = UUID.randomUUID();
        User inactiveUser = new User(userId, UUID.randomUUID(), UserRole.STUDENT, email, "hash", false, false, null);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(inactiveUser));
        when(otpService.generateOtp()).thenReturn("987654");
        when(otpService.hashOtp("987654")).thenReturn("newOtpHash");
        when(otpService.calculateExpiryInstant())
                .thenReturn(java.time.Instant.now().plus(java.time.Duration.ofMinutes(15)));
        when(passwordResetTokenRepository.save(any(PasswordResetToken.class))).thenAnswer(i -> i.getArgument(0));

        useCase.resendOtp(email);

        verify(passwordResetTokenRepository).invalidateAllActiveTokensForUser(userId);

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(tokenCaptor.capture());
        PasswordResetToken savedToken = tokenCaptor.getValue();
        assertThat(savedToken.userId()).isEqualTo(userId);
        assertThat(savedToken.tokenHash()).isEqualTo("newOtpHash");
        assertThat(savedToken.usedAt()).isNull();
        assertThat(savedToken.expiresAt()).isNotNull();

        ArgumentCaptor<OtpRequestedEvent> eventCaptor = ArgumentCaptor.forClass(OtpRequestedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        OtpRequestedEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.userId()).isEqualTo(userId);
        assertThat(publishedEvent.email()).isEqualTo(email);
        assertThat(publishedEvent.otp()).isEqualTo("987654");
        assertThat(publishedEvent.purpose()).isEqualTo(OtpRequestedEvent.PURPOSE_ACTIVATION);
    }

    @Test
    void shouldThrowIllegalStateExceptionWhenUserNotFound() {
        String email = "unknown@university.edu";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.resendOtp(email))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No inactive account found for this email.");

        verify(passwordResetTokenRepository, never()).invalidateAllActiveTokensForUser(any());
        verify(otpService, never()).generateOtp();
        verify(passwordResetTokenRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void shouldThrowIllegalStateExceptionWhenUserAlreadyActive() {
        String email = "active@university.edu";
        UUID userId = UUID.randomUUID();
        User activeUser = new User(userId, UUID.randomUUID(), UserRole.STUDENT, email, "hash", true, false, null);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> useCase.resendOtp(email))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No inactive account found for this email.");

        verify(passwordResetTokenRepository, never()).invalidateAllActiveTokensForUser(any());
        verify(otpService, never()).generateOtp();
        verify(passwordResetTokenRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }
}
