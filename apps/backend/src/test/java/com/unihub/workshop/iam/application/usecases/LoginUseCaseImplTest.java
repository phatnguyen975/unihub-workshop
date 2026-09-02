package com.unihub.workshop.iam.application.usecases;

import com.unihub.workshop.iam.application.ports.in.LoginUseCase;
import com.unihub.workshop.iam.application.ports.out.RefreshTokenRepository;
import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserRole;
import com.unihub.workshop.shared.exception.AppException;
import com.unihub.workshop.shared.security.JwtTokenIssuer;
import com.unihub.workshop.shared.security.SecurityProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LoginUseCaseImplTest {

    private UserRepository userRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private JwtTokenIssuer jwtTokenIssuer;
    private SecurityProperties securityProperties;
    private PasswordEncoder passwordEncoder;
    private MeterRegistry meterRegistry;
    private LoginUseCaseImpl loginUseCase;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        jwtTokenIssuer = mock(JwtTokenIssuer.class);
        securityProperties = mock(SecurityProperties.class);
        passwordEncoder = new Argon2PasswordEncoder(16, 32, 1, 4096, 1);
        meterRegistry = new SimpleMeterRegistry();

        when(securityProperties.accessTokenTtlMinutes()).thenReturn(15);
        when(securityProperties.refreshTokenTtlDays()).thenReturn(7);

        loginUseCase = new LoginUseCaseImpl(userRepository, refreshTokenRepository, jwtTokenIssuer, passwordEncoder,
                securityProperties, meterRegistry);
    }

    @Test
    void shouldReturnTokensOnSuccessfulLogin() {
        String email = "test@example.com";
        String password = "password123";
        String encodedPassword = passwordEncoder.encode(password);

        User user = new User(UUID.randomUUID(), UUID.randomUUID(), UserRole.STUDENT, email, encodedPassword, true,
                false, null);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(jwtTokenIssuer.issueAccessToken(user.id().toString(), "STUDENT", user.organizationId().toString()))
                .thenReturn("mock-access-token");

        LoginUseCase.LoginResult result = loginUseCase.login(email, password, "device-1", "127.0.0.1");

        assertThat(result.accessToken()).isEqualTo("mock-access-token");
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(result.expiresIn()).isEqualTo(15 * 60);

        verify(userRepository).updateLastLoginAt(eq(user.id()), any());
        verify(refreshTokenRepository).save(any());
        assertThat(meterRegistry.counter("auth.login.attempts", "outcome", "success").count()).isEqualTo(1.0);
    }

    @Test
    void shouldThrowInvalidCredentialsOnUnknownEmail() {
        String email = "unknown@example.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loginUseCase.login(email, "password", "device-1", "127.0.0.1"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.UNAUTHORIZED);

        assertThat(meterRegistry.counter("auth.login.attempts", "outcome", "failure", "reason", "invalid_credentials")
                .count()).isEqualTo(1.0);
    }

    @Test
    void shouldThrowInvalidCredentialsOnWrongPassword() {
        String email = "test@example.com";
        String encodedPassword = passwordEncoder.encode("correct-password");
        User user = new User(UUID.randomUUID(), UUID.randomUUID(), UserRole.STUDENT, email, encodedPassword, true,
                false, null);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> loginUseCase.login(email, "wrong-password", "device-1", "127.0.0.1"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.UNAUTHORIZED);

        assertThat(meterRegistry.counter("auth.login.attempts", "outcome", "failure", "reason", "invalid_credentials")
                .count()).isEqualTo(1.0);
    }

    @Test
    void shouldThrowAccountDisabledException() {
        String email = "test@example.com";
        String encodedPassword = passwordEncoder.encode("password");
        User user = new User(UUID.randomUUID(), UUID.randomUUID(), UserRole.STUDENT, email, encodedPassword, false,
                false, null);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> loginUseCase.login(email, "password", "device-1", "127.0.0.1"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.FORBIDDEN);

        assertThat(meterRegistry.counter("auth.login.attempts", "outcome", "failure", "reason", "account_disabled")
                .count()).isEqualTo(1.0);
    }

    @Test
    void shouldThrowTemporaryPasswordException() {
        String email = "test@example.com";
        String encodedPassword = passwordEncoder.encode("password");
        User user = new User(UUID.randomUUID(), UUID.randomUUID(), UserRole.STUDENT, email, encodedPassword, true, true,
                null);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> loginUseCase.login(email, "password", "device-1", "127.0.0.1"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.FORBIDDEN);

        assertThat(meterRegistry.counter("auth.login.attempts", "outcome", "failure", "reason", "temporary_password")
                .count()).isEqualTo(1.0);
    }
}
