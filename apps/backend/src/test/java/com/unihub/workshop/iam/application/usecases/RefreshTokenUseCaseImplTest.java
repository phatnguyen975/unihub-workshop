package com.unihub.workshop.iam.application.usecases;

import com.unihub.workshop.iam.application.ports.in.RefreshTokenUseCase.RefreshResult;
import com.unihub.workshop.iam.application.ports.out.RefreshTokenRepository;
import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.domain.model.RefreshToken;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserRole;
import com.unihub.workshop.iam.application.utils.TokenUtils;
import com.unihub.workshop.shared.exception.UnauthorizedException;
import com.unihub.workshop.shared.security.JwtTokenIssuer;
import com.unihub.workshop.shared.security.SecurityProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.unihub.workshop.iam.application.ports.out.SessionTrackerPort;
import com.unihub.workshop.shared.security.IssuedToken;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenUseCaseImplTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SessionTrackerPort sessionTrackerPort;
    @Mock
    private JwtTokenIssuer jwtTokenIssuer;

    private SecurityProperties securityProperties;
    private MeterRegistry meterRegistry;

    private RefreshTokenUseCaseImpl useCase;

    private final UUID userId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();
    private final UUID tokenId = UUID.randomUUID();

    private User activeUser;
    private RefreshToken validToken;

    @BeforeEach
    void setUp() {
        securityProperties = new SecurityProperties(java.util.List.of(), 15, 60, 7);
        meterRegistry = new SimpleMeterRegistry();

        useCase = new RefreshTokenUseCaseImpl(
                refreshTokenRepository,
                userRepository,
                sessionTrackerPort,
                jwtTokenIssuer,
                securityProperties,
                meterRegistry);

        activeUser = new User(userId, orgId, UserRole.STUDENT, "student@test.com", "hash", true, false, null);

        String rawToken = "raw-refresh-token";
        String tokenHash = TokenUtils.hash(rawToken);

        validToken = new RefreshToken(
                tokenId,
                userId,
                tokenHash,
                "Old Device",
                "127.0.0.1",
                Instant.now().plusSeconds(3600),
                null);
    }

    @Test
    void shouldRefreshSuccessfully() {
        when(refreshTokenRepository.findByTokenHash(TokenUtils.hash("raw-refresh-token")))
                .thenReturn(Optional.of(validToken));
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser));
        when(jwtTokenIssuer.issueToken(eq(userId.toString()), eq("STUDENT"), eq(orgId.toString()), anyString()))
                .thenReturn(new IssuedToken("new-access-token", "new-jti", Instant.now().plusSeconds(900)));

        RefreshResult result = useCase.refresh("raw-refresh-token", "New Device", "192.168.1.1");

        assertThat(result.accessToken()).isEqualTo("new-access-token");
        assertThat(result.refreshToken()).isNotBlank().isNotEqualTo("raw-refresh-token");
        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(result.expiresIn()).isEqualTo(3600);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(2)).save(captor.capture());

        RefreshToken revoked = captor.getAllValues().get(0);
        assertThat(revoked.id()).isEqualTo(tokenId);
        assertThat(revoked.revokedAt()).isNotNull();
        verify(sessionTrackerPort).removeSession(eq(tokenId));

        RefreshToken issued = captor.getAllValues().get(1);
        assertThat(issued.id()).isNotEqualTo(tokenId);
        assertThat(issued.deviceInfo()).isEqualTo("New Device");
        assertThat(issued.ipAddress()).isEqualTo("192.168.1.1");
        assertThat(issued.revokedAt()).isNull();
        assertThat(issued.expiresAt()).isAfter(Instant.now());
        assertThat(issued.tokenHash()).isEqualTo(TokenUtils.hash(result.refreshToken()));

        verify(jwtTokenIssuer).issueToken(eq(userId.toString()), eq("STUDENT"), eq(orgId.toString()), eq(issued.id().toString()));
        verify(sessionTrackerPort).trackSession(eq(issued.id()), eq("new-jti"), eq(Duration.ofMinutes(60)));

        assertThat(meterRegistry.counter("auth.token.refresh", "outcome", "success").count()).isEqualTo(1);
    }

    @Test
    void shouldRefreshSuccessfullyWithNullOrganizationId() {
        User studentUserWithoutOrg = new User(userId, null, UserRole.STUDENT, "student@test.com", "hash", true, false,
                null);
        when(refreshTokenRepository.findByTokenHash(TokenUtils.hash("raw-refresh-token")))
                .thenReturn(Optional.of(validToken));
        when(userRepository.findById(userId)).thenReturn(Optional.of(studentUserWithoutOrg));
        when(jwtTokenIssuer.issueToken(eq(userId.toString()), eq("STUDENT"), isNull(), anyString()))
                .thenReturn(new IssuedToken("new-access-token", "new-jti", Instant.now().plusSeconds(900)));

        RefreshResult result = useCase.refresh("raw-refresh-token", "New Device", "192.168.1.1");

        assertThat(result.accessToken()).isEqualTo("new-access-token");
        verify(jwtTokenIssuer).issueToken(eq(userId.toString()), eq("STUDENT"), isNull(), anyString());
    }

    @Test
    void shouldThrowUnauthorized_WhenTokenNotFound() {
        when(refreshTokenRepository.findByTokenHash(TokenUtils.hash("unknown-token"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.refresh("unknown-token", "Device", "IP"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid refresh token")
                .hasFieldOrPropertyWithValue("type", "urn:unihub:error:unauthorized");

        assertThat(meterRegistry.counter("auth.token.refresh", "outcome", "failure", "reason", "not_found").count())
                .isEqualTo(1);
    }

    @Test
    void shouldThrowUnauthorized_WhenTokenRevoked() {
        RefreshToken revoked = new RefreshToken(tokenId, userId, validToken.tokenHash(), "Dev", "IP",
                Instant.now().plusSeconds(3600), Instant.now().minusSeconds(10));
        when(refreshTokenRepository.findByTokenHash(TokenUtils.hash("raw"))).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> useCase.refresh("raw", "Device", "IP"))
                .isInstanceOf(UnauthorizedException.class);

        assertThat(meterRegistry.counter("auth.token.refresh", "outcome", "failure", "reason", "revoked").count())
                .isEqualTo(1);
    }

    @Test
    void shouldThrowUnauthorized_WhenTokenExpired() {
        RefreshToken expired = new RefreshToken(tokenId, userId, validToken.tokenHash(), "Dev", "IP",
                Instant.now().minusSeconds(10), null);
        when(refreshTokenRepository.findByTokenHash(TokenUtils.hash("raw"))).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> useCase.refresh("raw", "Device", "IP"))
                .isInstanceOf(UnauthorizedException.class);

        assertThat(meterRegistry.counter("auth.token.refresh", "outcome", "failure", "reason", "expired").count())
                .isEqualTo(1);
    }

    @Test
    void shouldThrowUnauthorized_WhenUserNotFound() {
        when(refreshTokenRepository.findByTokenHash(TokenUtils.hash("raw"))).thenReturn(Optional.of(validToken));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.refresh("raw", "Device", "IP"))
                .isInstanceOf(UnauthorizedException.class);

        assertThat(
                meterRegistry.counter("auth.token.refresh", "outcome", "failure", "reason", "user_not_found").count())
                .isEqualTo(1);
    }

    @Test
    void shouldThrowUnauthorized_WhenUserDisabled() {
        User disabledUser = new User(userId, orgId, UserRole.STUDENT, "student@test.com", "hash", false, false, null);
        when(refreshTokenRepository.findByTokenHash(TokenUtils.hash("raw"))).thenReturn(Optional.of(validToken));
        when(userRepository.findById(userId)).thenReturn(Optional.of(disabledUser));

        assertThatThrownBy(() -> useCase.refresh("raw", "Device", "IP"))
                .isInstanceOf(UnauthorizedException.class);

        assertThat(
                meterRegistry.counter("auth.token.refresh", "outcome", "failure", "reason", "account_disabled").count())
                .isEqualTo(1);
    }
}
