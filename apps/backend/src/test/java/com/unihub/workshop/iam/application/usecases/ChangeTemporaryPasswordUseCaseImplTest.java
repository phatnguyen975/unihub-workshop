package com.unihub.workshop.iam.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.unihub.workshop.iam.application.ports.in.ChangeTemporaryPasswordUseCase;
import com.unihub.workshop.iam.application.ports.out.RefreshTokenRepository;
import com.unihub.workshop.iam.application.ports.out.SessionTrackerPort;
import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserRole;
import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.exception.UnauthorizedException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.shared.security.IssuedToken;
import com.unihub.workshop.shared.security.JwtTokenIssuer;
import com.unihub.workshop.shared.security.SecurityProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class ChangeTemporaryPasswordUseCaseImplTest {

    private UserRepository userRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private SessionTrackerPort sessionTrackerPort;
    private JwtTokenIssuer jwtTokenIssuer;
    private SecurityProperties securityProperties;
    private PasswordEncoder passwordEncoder;
    private MeterRegistry meterRegistry;
    private ChangeTemporaryPasswordUseCaseImpl changeTemporaryPasswordUseCase;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        sessionTrackerPort = mock(SessionTrackerPort.class);
        jwtTokenIssuer = mock(JwtTokenIssuer.class);
        securityProperties = mock(SecurityProperties.class);
        passwordEncoder = new Argon2PasswordEncoder(16, 32, 1, 4096, 1);
        meterRegistry = new SimpleMeterRegistry();

        when(securityProperties.accessTokenTtlMinutes()).thenReturn(60);
        when(securityProperties.refreshTokenTtlDays()).thenReturn(7);
        when(userRepository.save(any(User.class))).thenAnswer(invocation ->
            invocation.getArgument(0)
        );

        changeTemporaryPasswordUseCase = new ChangeTemporaryPasswordUseCaseImpl(
            userRepository,
            refreshTokenRepository,
            sessionTrackerPort,
            jwtTokenIssuer,
            passwordEncoder,
            securityProperties,
            meterRegistry
        );
    }

    @Test
    void shouldSuccessfullyChangeTemporaryPasswordAndIssueSession() {
        String email = "staff.bob@unihub.edu";
        String currentPassword = "TempPassword123!";
        String newPassword = "MyOfficialSecurePassword2026!";
        String encodedTempPassword = passwordEncoder.encode(currentPassword);

        User user = User.builder()
            .id(UUID.randomUUID())
            .organizationId(UUID.randomUUID())
            .role(UserRole.STAFF)
            .email(email)
            .passwordHash(encodedTempPassword)
            .isActive(true)
            .isTemporaryPassword(true)
            .lastLoginAt(null)
            .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(
            jwtTokenIssuer.issueToken(
                eq(user.id().toString()),
                eq("STAFF"),
                eq(user.organizationId().toString()),
                any()
            )
        ).thenReturn(
            new IssuedToken(
                "mock-access-token",
                "mock-jti",
                Instant.now().plusSeconds(3600)
            )
        );

        when(
            userRepository.updateTemporaryPassword(eq(user.id()), any(), any())
        ).thenReturn(true);

        ChangeTemporaryPasswordUseCase.ChangeTemporaryPasswordResult result =
            changeTemporaryPasswordUseCase.changeTemporaryPassword(
                email,
                currentPassword,
                newPassword,
                "Mozilla/5.0",
                "127.0.0.1"
            );

        assertThat(result.accessToken()).isEqualTo("mock-access-token");
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(result.expiresIn()).isEqualTo(3600);
        assertThat(result.user().email()).isEqualTo(email);
        assertThat(result.user().isTemporaryPassword()).isFalse();
        assertThat(result.user().lastLoginAt()).isNotNull();

        verify(userRepository).updateTemporaryPassword(
            eq(user.id()),
            argThat(hash -> passwordEncoder.matches(newPassword, hash)),
            any()
        );
        verify(refreshTokenRepository).save(any());
        verify(sessionTrackerPort).trackSession(
            any(UUID.class),
            eq("mock-jti"),
            eq(Duration.ofMinutes(60))
        );
        assertThat(
            meterRegistry
                .counter(
                    "auth.password.temporary.attempts",
                    "outcome",
                    "success"
                )
                .count()
        ).isEqualTo(1.0);
    }

    @Test
    void shouldThrowBadRequestWhenConcurrentRequestAlreadyChangedTemporaryPassword() {
        String email = "staff.bob@unihub.edu";
        String currentPassword = "TempPassword123!";
        String newPassword = "MyOfficialSecurePassword2026!";
        String encodedTempPassword = passwordEncoder.encode(currentPassword);

        User user = User.builder()
            .id(UUID.randomUUID())
            .organizationId(UUID.randomUUID())
            .role(UserRole.STAFF)
            .email(email)
            .passwordHash(encodedTempPassword)
            .isActive(true)
            .isTemporaryPassword(true)
            .lastLoginAt(null)
            .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(
            userRepository.updateTemporaryPassword(eq(user.id()), any(), any())
        ).thenReturn(false);

        assertThatThrownBy(() ->
            changeTemporaryPasswordUseCase.changeTemporaryPassword(
                email,
                currentPassword,
                newPassword,
                "Mozilla/5.0",
                "127.0.0.1"
            )
        )
            .isInstanceOf(BadRequestException.class)
            .hasMessage("Account does not have a temporary password.");

        verify(refreshTokenRepository, never()).save(any());
        verify(jwtTokenIssuer, never()).issueToken(any(), any(), any(), any());
        assertThat(
            meterRegistry
                .counter(
                    "auth.password.temporary.attempts",
                    "outcome",
                    "failure",
                    "reason",
                    "not_temporary_password"
                )
                .count()
        ).isEqualTo(1.0);
    }

    @Test
    void shouldThrowUnauthorizedWhenUserNotFound() {
        String email = "nonexistent@unihub.edu";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            changeTemporaryPasswordUseCase.changeTemporaryPassword(
                email,
                "SomePassword123!",
                "NewPassword123!",
                "Agent",
                "127.0.0.1"
            )
        )
            .isInstanceOf(UnauthorizedException.class)
            .hasMessage("Invalid email or current password");

        verify(userRepository, never()).save(any());
        verify(refreshTokenRepository, never()).save(any());
        assertThat(
            meterRegistry
                .counter(
                    "auth.password.temporary.attempts",
                    "outcome",
                    "failure",
                    "reason",
                    "invalid_credentials"
                )
                .count()
        ).isEqualTo(1.0);
    }

    @Test
    void shouldThrowUnauthorizedWhenUserInactive() {
        String email = "inactive@unihub.edu";
        String currentPassword = "TempPassword123!";
        String encodedTempPassword = passwordEncoder.encode(currentPassword);

        User user = User.builder()
            .id(UUID.randomUUID())
            .organizationId(UUID.randomUUID())
            .role(UserRole.STAFF)
            .email(email)
            .passwordHash(encodedTempPassword)
            .isActive(false)
            .isTemporaryPassword(true)
            .lastLoginAt(null)
            .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        assertThatThrownBy(() ->
            changeTemporaryPasswordUseCase.changeTemporaryPassword(
                email,
                currentPassword,
                "NewPassword123!",
                "Agent",
                "127.0.0.1"
            )
        )
            .isInstanceOf(UnauthorizedException.class)
            .hasMessage("Invalid email or current password");

        verify(userRepository, never()).save(any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void shouldThrowUnauthorizedWhenCurrentPasswordIncorrect() {
        String email = "staff.bob@unihub.edu";
        String currentPassword = "CorrectTempPassword123!";
        String wrongPassword = "WrongPassword123!";
        String encodedTempPassword = passwordEncoder.encode(currentPassword);

        User user = User.builder()
            .id(UUID.randomUUID())
            .organizationId(UUID.randomUUID())
            .role(UserRole.STAFF)
            .email(email)
            .passwordHash(encodedTempPassword)
            .isActive(true)
            .isTemporaryPassword(true)
            .lastLoginAt(null)
            .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        assertThatThrownBy(() ->
            changeTemporaryPasswordUseCase.changeTemporaryPassword(
                email,
                wrongPassword,
                "NewPassword123!",
                "Agent",
                "127.0.0.1"
            )
        )
            .isInstanceOf(UnauthorizedException.class)
            .hasMessage("Invalid email or current password");

        verify(userRepository, never()).save(any());
        verify(refreshTokenRepository, never()).save(any());
        assertThat(
            meterRegistry
                .counter(
                    "auth.password.temporary.attempts",
                    "outcome",
                    "failure",
                    "reason",
                    "invalid_credentials"
                )
                .count()
        ).isEqualTo(1.0);
    }

    @Test
    void shouldThrowBadRequestWhenUserDoesNotHaveTemporaryPassword() {
        String email = "staff.bob@unihub.edu";
        String currentPassword = "PermanentPassword123!";
        String encodedPassword = passwordEncoder.encode(currentPassword);

        User user = User.builder()
            .id(UUID.randomUUID())
            .organizationId(UUID.randomUUID())
            .role(UserRole.STAFF)
            .email(email)
            .passwordHash(encodedPassword)
            .isActive(true)
            .isTemporaryPassword(false)
            .lastLoginAt(null)
            .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        assertThatThrownBy(() ->
            changeTemporaryPasswordUseCase.changeTemporaryPassword(
                email,
                currentPassword,
                "NewPassword123!",
                "Agent",
                "127.0.0.1"
            )
        )
            .isInstanceOf(BadRequestException.class)
            .hasMessage("Account does not have a temporary password.");

        verify(userRepository, never()).save(any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void shouldThrowValidationExceptionWhenNewPasswordMatchesCurrentPassword() {
        String email = "staff.bob@unihub.edu";
        String currentPassword = "TempPassword123!";
        String encodedTempPassword = passwordEncoder.encode(currentPassword);

        User user = User.builder()
            .id(UUID.randomUUID())
            .organizationId(UUID.randomUUID())
            .role(UserRole.STAFF)
            .email(email)
            .passwordHash(encodedTempPassword)
            .isActive(true)
            .isTemporaryPassword(true)
            .lastLoginAt(null)
            .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        assertThatThrownBy(() ->
            changeTemporaryPasswordUseCase.changeTemporaryPassword(
                email,
                currentPassword,
                currentPassword,
                "Agent",
                "127.0.0.1"
            )
        )
            .isInstanceOf(ValidationException.class)
            .satisfies(ex -> {
                ValidationException ve = (ValidationException) ex;
                assertThat(ve.getProperties()).containsKey("validationErrors");
                assertThat(
                    ve.getProperties().get("validationErrors").toString()
                ).contains(
                    "newPassword: New password must be different from the temporary password."
                );
            });

        verify(userRepository, never()).save(any());
        verify(refreshTokenRepository, never()).save(any());
    }
}
