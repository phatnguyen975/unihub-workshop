package com.unihub.workshop.iam.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.application.ports.out.UserVerificationTokenRepository;
import com.unihub.workshop.iam.application.utils.OtpService;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserRole;
import com.unihub.workshop.iam.domain.model.UserVerificationToken;
import com.unihub.workshop.shared.events.OtpRequestedEvent;
import com.unihub.workshop.shared.exception.NotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class ResendOtpUseCaseImplTest {

    private UserRepository userRepository;
    private UserVerificationTokenRepository userVerificationTokenRepository;
    private OtpService otpService;
    private ApplicationEventPublisher eventPublisher;
    private ResendOtpUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userVerificationTokenRepository = mock(
            UserVerificationTokenRepository.class
        );
        otpService = mock(OtpService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        useCase = new ResendOtpUseCaseImpl(
            userRepository,
            userVerificationTokenRepository,
            otpService,
            eventPublisher
        );
    }

    @Test
    void shouldResendOtpSuccessfullyWhenAccountInactive() {
        String email = "student@university.edu";
        UUID userId = UUID.randomUUID();
        User inactiveUser = User.builder()
            .id(userId)
            .organizationId(UUID.randomUUID())
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
        when(otpService.generateOtp()).thenReturn("987654");
        when(otpService.hashOtp("987654")).thenReturn("newOtpHash");
        when(otpService.calculateExpiryInstant()).thenReturn(
            java.time.Instant.now().plus(java.time.Duration.ofMinutes(15))
        );
        when(
            userVerificationTokenRepository.save(
                any(UserVerificationToken.class)
            )
        ).thenAnswer(i -> i.getArgument(0));

        useCase.resendOtp(email);

        verify(userVerificationTokenRepository).invalidateActiveTokens(
            userId,
            UserVerificationToken.TYPE_ACTIVATION_OTP
        );

        ArgumentCaptor<UserVerificationToken> tokenCaptor =
            ArgumentCaptor.forClass(UserVerificationToken.class);
        verify(userVerificationTokenRepository).save(tokenCaptor.capture());
        UserVerificationToken savedToken = tokenCaptor.getValue();
        assertThat(savedToken.userId()).isEqualTo(userId);
        assertThat(savedToken.tokenType()).isEqualTo(
            UserVerificationToken.TYPE_ACTIVATION_OTP
        );
        assertThat(savedToken.tokenHash()).isEqualTo("newOtpHash");
        assertThat(savedToken.usedAt()).isNull();
        assertThat(savedToken.expiresAt()).isNotNull();

        ArgumentCaptor<OtpRequestedEvent> eventCaptor = ArgumentCaptor.forClass(
            OtpRequestedEvent.class
        );
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        OtpRequestedEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.userId()).isEqualTo(userId);
        assertThat(publishedEvent.email()).isEqualTo(email);
        assertThat(publishedEvent.otp()).isEqualTo("987654");
        assertThat(publishedEvent.purpose()).isEqualTo(
            OtpRequestedEvent.PURPOSE_ACTIVATION
        );
    }

    @Test
    void shouldThrowNotFoundExceptionWhenUserNotFound() {
        String email = "unknown@university.edu";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.resendOtp(email))
            .isInstanceOf(NotFoundException.class)
            .hasMessage("No account found for this email.");

        verify(userVerificationTokenRepository, never()).invalidateActiveTokens(
            any(),
            any()
        );
        verify(otpService, never()).generateOtp();
        verify(userVerificationTokenRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void shouldResendPasswordResetOtpSuccessfullyWhenAccountActive() {
        String email = "active@university.edu";
        UUID userId = UUID.randomUUID();
        User activeUser = User.builder()
            .id(userId)
            .organizationId(UUID.randomUUID())
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
        when(otpService.generateOtp()).thenReturn("123456");
        when(otpService.hashOtp("123456")).thenReturn("resetOtpHash");
        when(otpService.calculateExpiryInstant()).thenReturn(
            java.time.Instant.now().plus(java.time.Duration.ofMinutes(15))
        );
        when(
            userVerificationTokenRepository.save(
                any(UserVerificationToken.class)
            )
        ).thenAnswer(i -> i.getArgument(0));

        useCase.resendOtp(email);

        verify(userVerificationTokenRepository).invalidateActiveTokens(
            userId,
            UserVerificationToken.TYPE_PASSWORD_RESET_OTP
        );

        ArgumentCaptor<UserVerificationToken> tokenCaptor =
            ArgumentCaptor.forClass(UserVerificationToken.class);
        verify(userVerificationTokenRepository).save(tokenCaptor.capture());
        UserVerificationToken savedToken = tokenCaptor.getValue();
        assertThat(savedToken.userId()).isEqualTo(userId);
        assertThat(savedToken.tokenType()).isEqualTo(
            UserVerificationToken.TYPE_PASSWORD_RESET_OTP
        );
        assertThat(savedToken.tokenHash()).isEqualTo("resetOtpHash");
        assertThat(savedToken.usedAt()).isNull();
        assertThat(savedToken.expiresAt()).isNotNull();

        ArgumentCaptor<OtpRequestedEvent> eventCaptor = ArgumentCaptor.forClass(
            OtpRequestedEvent.class
        );
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        OtpRequestedEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.userId()).isEqualTo(userId);
        assertThat(publishedEvent.email()).isEqualTo(email);
        assertThat(publishedEvent.otp()).isEqualTo("123456");
        assertThat(publishedEvent.purpose()).isEqualTo(
            OtpRequestedEvent.PURPOSE_PASSWORD_RESET
        );
    }
}
