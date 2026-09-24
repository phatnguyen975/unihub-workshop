package com.unihub.workshop.iam.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.unihub.workshop.iam.application.ports.in.LoginUseCase;
import com.unihub.workshop.iam.application.ports.out.RefreshTokenRepository;
import com.unihub.workshop.iam.application.ports.out.SessionTrackerPort;
import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.domain.model.RefreshToken;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserRole;
import com.unihub.workshop.shared.exception.AppException;
import com.unihub.workshop.shared.security.IssuedToken;
import com.unihub.workshop.shared.security.JwtTokenIssuer;
import com.unihub.workshop.shared.security.SecurityProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class LoginUseCaseImplTest {

    private UserRepository userRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private SessionTrackerPort sessionTrackerPort;
    private JwtTokenIssuer jwtTokenIssuer;
    private SecurityProperties securityProperties;
    private PasswordEncoder passwordEncoder;
    private LoginUseCaseImpl loginUseCase;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        sessionTrackerPort = mock(SessionTrackerPort.class);
        jwtTokenIssuer = mock(JwtTokenIssuer.class);
        securityProperties = mock(SecurityProperties.class);
        passwordEncoder = new Argon2PasswordEncoder(16, 32, 1, 4096, 1);

        when(securityProperties.accessTokenTtlMinutes()).thenReturn(15);
        when(securityProperties.refreshTokenTtlDays()).thenReturn(7);

        loginUseCase = new LoginUseCaseImpl(
            userRepository,
            refreshTokenRepository,
            sessionTrackerPort,
            jwtTokenIssuer,
            passwordEncoder,
            securityProperties
        );
    }

    @Test
    void shouldReturnTokensAndUserProfileOnSuccessfulLogin() {
        String email = "test@example.com";
        String password = "password123";
        String encodedPassword = passwordEncoder.encode(password);

        User user = User.builder()
            .id(UUID.randomUUID())
            .organizationId(UUID.randomUUID())
            .role(UserRole.STUDENT)
            .email(email)
            .passwordHash(encodedPassword)
            .isActive(true)
            .isTemporaryPassword(false)
            .lastLoginAt(null)
            .build();
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(
            jwtTokenIssuer.issueToken(
                eq(user.id().toString()),
                eq("STUDENT"),
                eq(user.organizationId().toString()),
                anyString()
            )
        ).thenReturn(
            new IssuedToken(
                "mock-access-token",
                "mock-jti",
                Instant.now().plusSeconds(900)
            )
        );

        LoginUseCase.LoginResult result = loginUseCase.login(
            email,
            password,
            "device-1",
            "127.0.0.1"
        );

        assertThat(result.accessToken()).isEqualTo("mock-access-token");
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(result.expiresIn()).isEqualTo(15 * 60);
        assertThat(result.user()).isEqualTo(user);

        verify(userRepository).updateLastLoginAt(eq(user.id()), any());
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(
            RefreshToken.class
        );
        verify(refreshTokenRepository).save(captor.capture());
        UUID sessionId = captor.getValue().id();
        assertThat(sessionId).isNotNull();

        verify(jwtTokenIssuer).issueToken(
            eq(user.id().toString()),
            eq("STUDENT"),
            eq(user.organizationId().toString()),
            eq(sessionId.toString())
        );
        verify(sessionTrackerPort).trackSession(
            eq(sessionId),
            eq("mock-jti"),
            eq(Duration.ofMinutes(15))
        );
    }

    @Test
    void shouldHandleNullOrganizationIdGracefully() {
        String email = "student@example.com";
        String password = "password123";
        String encodedPassword = passwordEncoder.encode(password);

        // Student with organizationId = null
        User user = User.builder()
            .id(UUID.randomUUID())
            .organizationId(null)
            .role(UserRole.STUDENT)
            .email(email)
            .passwordHash(encodedPassword)
            .isActive(true)
            .isTemporaryPassword(false)
            .lastLoginAt(null)
            .build();
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(
            jwtTokenIssuer.issueToken(
                eq(user.id().toString()),
                eq("STUDENT"),
                isNull(),
                anyString()
            )
        ).thenReturn(
            new IssuedToken(
                "mock-access-token",
                "mock-jti",
                Instant.now().plusSeconds(900)
            )
        );

        LoginUseCase.LoginResult result = loginUseCase.login(
            email,
            password,
            "device-1",
            "127.0.0.1"
        );

        assertThat(result.accessToken()).isEqualTo("mock-access-token");
        assertThat(result.user()).isEqualTo(user);
        verify(jwtTokenIssuer).issueToken(
            eq(user.id().toString()),
            eq("STUDENT"),
            isNull(),
            anyString()
        );
    }

    @Test
    void shouldThrowInvalidCredentialsOnUnknownEmail() {
        String email = "unknown@example.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            loginUseCase.login(email, "password", "device-1", "127.0.0.1")
        )
            .isInstanceOf(AppException.class)
            .hasFieldOrPropertyWithValue("status", HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldThrowInvalidCredentialsOnWrongPassword() {
        String email = "test@example.com";
        String encodedPassword = passwordEncoder.encode("correct-password");
        User user = User.builder()
            .id(UUID.randomUUID())
            .organizationId(UUID.randomUUID())
            .role(UserRole.STUDENT)
            .email(email)
            .passwordHash(encodedPassword)
            .isActive(true)
            .isTemporaryPassword(false)
            .lastLoginAt(null)
            .build();
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        assertThatThrownBy(() ->
            loginUseCase.login(email, "wrong-password", "device-1", "127.0.0.1")
        )
            .isInstanceOf(AppException.class)
            .hasFieldOrPropertyWithValue("status", HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldThrowAccountDisabledException() {
        String email = "test@example.com";
        String encodedPassword = passwordEncoder.encode("password");
        User user = User.builder()
            .id(UUID.randomUUID())
            .organizationId(UUID.randomUUID())
            .role(UserRole.STUDENT)
            .email(email)
            .passwordHash(encodedPassword)
            .isActive(false)
            .isTemporaryPassword(false)
            .lastLoginAt(null)
            .build();
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        assertThatThrownBy(() ->
            loginUseCase.login(email, "password", "device-1", "127.0.0.1")
        )
            .isInstanceOf(AppException.class)
            .hasFieldOrPropertyWithValue("status", HttpStatus.FORBIDDEN);
    }

    @Test
    void shouldThrowTemporaryPasswordException() {
        String email = "test@example.com";
        String encodedPassword = passwordEncoder.encode("password");
        User user = User.builder()
            .id(UUID.randomUUID())
            .organizationId(UUID.randomUUID())
            .role(UserRole.STUDENT)
            .email(email)
            .passwordHash(encodedPassword)
            .isActive(true)
            .isTemporaryPassword(true)
            .lastLoginAt(null)
            .build();
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        assertThatThrownBy(() ->
            loginUseCase.login(email, "password", "device-1", "127.0.0.1")
        )
            .isInstanceOf(AppException.class)
            .hasFieldOrPropertyWithValue("status", HttpStatus.FORBIDDEN);
    }

    @Test
    void shouldNormalizeEmailBeforeLookingUpUser() {
        String rawEmail = "  Student.Alice@University.EDU  ";
        String normalizedEmail = "student.alice@university.edu";
        String password = "password123";
        String encodedPassword = passwordEncoder.encode(password);

        User user = User.builder()
            .id(UUID.randomUUID())
            .role(UserRole.STUDENT)
            .email(normalizedEmail)
            .passwordHash(encodedPassword)
            .isActive(true)
            .isTemporaryPassword(false)
            .build();

        when(userRepository.findByEmail(normalizedEmail)).thenReturn(
            Optional.of(user)
        );
        when(jwtTokenIssuer.issueToken(any(), any(), any(), any())).thenReturn(
            new IssuedToken(
                "access-token",
                "jti",
                Instant.now().plusSeconds(900)
            )
        );

        LoginUseCase.LoginResult result = loginUseCase.login(
            rawEmail,
            password,
            "device-1",
            "127.0.0.1"
        );

        assertThat(result).isNotNull();
        verify(userRepository).findByEmail(normalizedEmail);
    }
}
