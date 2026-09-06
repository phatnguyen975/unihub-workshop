package com.unihub.workshop.iam.application.usecases;

import com.unihub.workshop.iam.application.ports.out.StudentDirectoryRepository;
import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.application.ports.out.UserVerificationTokenRepository;
import com.unihub.workshop.iam.application.utils.OtpService;
import com.unihub.workshop.iam.domain.events.OtpRequestedEvent;
import com.unihub.workshop.iam.domain.model.StudentDirectory;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserRole;
import com.unihub.workshop.iam.domain.model.UserVerificationToken;
import com.unihub.workshop.shared.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RegisterUserUseCaseImplTest {

    private UserRepository userRepository;
    private StudentDirectoryRepository studentDirectoryRepository;
    private UserVerificationTokenRepository userVerificationTokenRepository;
    private OtpService otpService;
    private PasswordEncoder passwordEncoder;
    private ApplicationEventPublisher eventPublisher;
    private RegisterUserUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        studentDirectoryRepository = mock(StudentDirectoryRepository.class);
        userVerificationTokenRepository = mock(UserVerificationTokenRepository.class);
        otpService = mock(OtpService.class);
        passwordEncoder = mock(PasswordEncoder.class);
        eventPublisher = mock(ApplicationEventPublisher.class);

        useCase = new RegisterUserUseCaseImpl(
                userRepository,
                studentDirectoryRepository,
                userVerificationTokenRepository,
                otpService,
                passwordEncoder,
                eventPublisher);
    }

    @Test
    void shouldRegisterStudentWithStudentDirectoryMatch() {
        String email = "alice@university.edu";
        String password = "Str0ng!Pass";
        String fullName = "Alice Smith";
        UUID dirId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();

        when(userRepository.existsByEmail(email)).thenReturn(false);
        StudentDirectory directory = new StudentDirectory(
                dirId, orgId, "23123456", email, fullName, "Computer Science", "ACTIVE", null, Instant.now(),
                Instant.now());
        when(studentDirectoryRepository.findActiveByEmail(email)).thenReturn(Optional.of(directory));
        when(passwordEncoder.encode(password)).thenReturn("hashedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(otpService.generateOtp()).thenReturn("123456");
        when(otpService.hashOtp("123456")).thenReturn("otpHash");
        when(otpService.calculateExpiryInstant()).thenReturn(Instant.now().plus(Duration.ofMinutes(15)));

        UUID userId = useCase.register(email, password, fullName);

        assertThat(userId).isNotNull();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.id()).isEqualTo(userId);
        assertThat(savedUser.email()).isEqualTo(email);
        assertThat(savedUser.role()).isEqualTo(UserRole.STUDENT);
        assertThat(savedUser.isActive()).isFalse();
        assertThat(savedUser.studentRecordId()).isEqualTo(dirId);
        assertThat(savedUser.organizationId()).isEqualTo(orgId);
        assertThat(savedUser.passwordHash()).isEqualTo("hashedPassword");

        ArgumentCaptor<UserVerificationToken> tokenCaptor = ArgumentCaptor.forClass(UserVerificationToken.class);
        verify(userVerificationTokenRepository).save(tokenCaptor.capture());
        UserVerificationToken savedToken = tokenCaptor.getValue();
        assertThat(savedToken.userId()).isEqualTo(userId);
        assertThat(savedToken.tokenType()).isEqualTo(UserVerificationToken.TYPE_ACTIVATION_OTP);
        assertThat(savedToken.tokenHash()).isEqualTo("otpHash");
        assertThat(savedToken.usedAt()).isNull();
        assertThat(savedToken.expiresAt()).isAfter(Instant.now());

        ArgumentCaptor<OtpRequestedEvent> eventCaptor = ArgumentCaptor.forClass(OtpRequestedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        OtpRequestedEvent event = eventCaptor.getValue();
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.email()).isEqualTo(email);
        assertThat(event.otp()).isEqualTo("123456");
        assertThat(event.purpose()).isEqualTo(OtpRequestedEvent.PURPOSE_ACTIVATION);
    }

    @Test
    void shouldRegisterStudentWithoutStudentDirectoryMatch() {
        String email = "bob@external.com";
        when(userRepository.existsByEmail(email)).thenReturn(false);
        when(studentDirectoryRepository.findActiveByEmail(email)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("hashedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(otpService.generateOtp()).thenReturn("654321");
        when(otpService.hashOtp("654321")).thenReturn("otpHash2");
        when(otpService.calculateExpiryInstant()).thenReturn(Instant.now().plus(Duration.ofMinutes(15)));

        UUID userId = useCase.register(email, "Str0ng!Pass", "Bob Jones");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.id()).isEqualTo(userId);
        assertThat(savedUser.studentRecordId()).isNull();
        assertThat(savedUser.organizationId()).isNull();
        assertThat(savedUser.isActive()).isFalse();

        ArgumentCaptor<UserVerificationToken> tokenCaptor = ArgumentCaptor.forClass(UserVerificationToken.class);
        verify(userVerificationTokenRepository).save(tokenCaptor.capture());
        UserVerificationToken savedToken = tokenCaptor.getValue();
        assertThat(savedToken.tokenType()).isEqualTo(UserVerificationToken.TYPE_ACTIVATION_OTP);
    }

    @Test
    void shouldThrowConflictExceptionWhenEmailAlreadyExists() {
        String email = "existing@university.edu";
        when(userRepository.existsByEmail(email)).thenReturn(true);

        assertThatThrownBy(() -> useCase.register(email, "Str0ng!Pass", "Alice Smith"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("An account with this email already exists");

        verify(userRepository, never()).save(any());
        verify(userVerificationTokenRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }
}
