package com.unihub.workshop.iam.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.unihub.workshop.iam.application.ports.in.RefreshTokenUseCase.RefreshResult;
import com.unihub.workshop.iam.application.ports.out.RefreshTokenRepository;
import com.unihub.workshop.iam.application.ports.out.SessionTrackerPort;
import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.application.utils.TokenUtils;
import com.unihub.workshop.iam.domain.model.RefreshToken;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserRole;
import com.unihub.workshop.shared.exception.UnauthorizedException;
import com.unihub.workshop.shared.security.IssuedToken;
import com.unihub.workshop.shared.security.JwtTokenIssuer;
import com.unihub.workshop.shared.security.SecurityProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    private RefreshTokenUseCaseImpl useCase;

    private final UUID userId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();
    private final UUID tokenId = UUID.randomUUID();

    private User activeUser;
    private RefreshToken validToken;

    @BeforeEach
    void setUp() {
        securityProperties = new SecurityProperties(
            java.util.List.of(),
            15,
            60,
            7
        );

        useCase = new RefreshTokenUseCaseImpl(
            refreshTokenRepository,
            userRepository,
            sessionTrackerPort,
            jwtTokenIssuer,
            securityProperties
        );

        activeUser = User.builder()
            .id(userId)
            .organizationId(orgId)
            .role(UserRole.STUDENT)
            .email("student@test.com")
            .passwordHash("hash")
            .isActive(true)
            .isTemporaryPassword(false)
            .lastLoginAt(null)
            .build();

        String rawToken = "raw-refresh-token";
        String tokenHash = TokenUtils.hash(rawToken);

        validToken = new RefreshToken(
            tokenId,
            userId,
            tokenHash,
            "Old Device",
            "127.0.0.1",
            Instant.now().plusSeconds(3600),
            null
        );
    }

    @Test
    void shouldRefreshSuccessfully() {
        when(
            refreshTokenRepository.findByTokenHash(
                TokenUtils.hash("raw-refresh-token")
            )
        ).thenReturn(Optional.of(validToken));
        when(userRepository.findById(userId)).thenReturn(
            Optional.of(activeUser)
        );
        when(
            jwtTokenIssuer.issueToken(
                eq(userId.toString()),
                eq("STUDENT"),
                eq(orgId.toString()),
                anyString()
            )
        ).thenReturn(
            new IssuedToken(
                "new-access-token",
                "new-jti",
                Instant.now().plusSeconds(900)
            )
        );

        RefreshResult result = useCase.refresh(
            "raw-refresh-token",
            "New Device",
            "192.168.1.1"
        );

        assertThat(result.accessToken()).isEqualTo("new-access-token");
        assertThat(result.refreshToken())
            .isNotBlank()
            .isNotEqualTo("raw-refresh-token");
        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(result.expiresIn()).isEqualTo(3600);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(
            RefreshToken.class
        );
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
        assertThat(issued.tokenHash()).isEqualTo(
            TokenUtils.hash(result.refreshToken())
        );

        verify(jwtTokenIssuer).issueToken(
            eq(userId.toString()),
            eq("STUDENT"),
            eq(orgId.toString()),
            eq(issued.id().toString())
        );
        verify(sessionTrackerPort).trackSession(
            eq(issued.id()),
            eq("new-jti"),
            eq(Duration.ofMinutes(60))
        );
    }

    @Test
    void shouldRefreshSuccessfullyWithNullOrganizationId() {
        User studentUserWithoutOrg = User.builder()
            .id(userId)
            .organizationId(null)
            .role(UserRole.STUDENT)
            .email("student@test.com")
            .passwordHash("hash")
            .isActive(true)
            .isTemporaryPassword(false)
            .lastLoginAt(null)
            .build();
        when(
            refreshTokenRepository.findByTokenHash(
                TokenUtils.hash("raw-refresh-token")
            )
        ).thenReturn(Optional.of(validToken));
        when(userRepository.findById(userId)).thenReturn(
            Optional.of(studentUserWithoutOrg)
        );
        when(
            jwtTokenIssuer.issueToken(
                eq(userId.toString()),
                eq("STUDENT"),
                isNull(),
                anyString()
            )
        ).thenReturn(
            new IssuedToken(
                "new-access-token",
                "new-jti",
                Instant.now().plusSeconds(900)
            )
        );

        RefreshResult result = useCase.refresh(
            "raw-refresh-token",
            "New Device",
            "192.168.1.1"
        );

        assertThat(result.accessToken()).isEqualTo("new-access-token");
        verify(jwtTokenIssuer).issueToken(
            eq(userId.toString()),
            eq("STUDENT"),
            isNull(),
            anyString()
        );
    }

    @Test
    void shouldFallbackToOldDeviceInfoAndIpAddressWhenNewAreBlankOrNull() {
        when(
            refreshTokenRepository.findByTokenHash(
                TokenUtils.hash("raw-refresh-token")
            )
        ).thenReturn(Optional.of(validToken));
        when(userRepository.findById(userId)).thenReturn(
            Optional.of(activeUser)
        );
        when(
            jwtTokenIssuer.issueToken(
                eq(userId.toString()),
                eq("STUDENT"),
                eq(orgId.toString()),
                anyString()
            )
        ).thenReturn(
            new IssuedToken(
                "new-access-token",
                "new-jti",
                Instant.now().plusSeconds(900)
            )
        );

        RefreshResult result = useCase.refresh("raw-refresh-token", "", null);

        assertThat(result.accessToken()).isEqualTo("new-access-token");

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(
            RefreshToken.class
        );
        verify(refreshTokenRepository, times(2)).save(captor.capture());

        RefreshToken issued = captor.getAllValues().get(1);
        assertThat(issued.deviceInfo()).isEqualTo("Old Device");
        assertThat(issued.ipAddress()).isEqualTo("127.0.0.1");
    }

    @Test
    void shouldThrowUnauthorized_WhenTokenNotFound() {
        when(
            refreshTokenRepository.findByTokenHash(
                TokenUtils.hash("unknown-token")
            )
        ).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            useCase.refresh("unknown-token", "Device", "IP")
        )
            .isInstanceOf(UnauthorizedException.class)
            .hasMessage("Invalid refresh token")
            .hasFieldOrPropertyWithValue(
                "type",
                "urn:unihub:error:unauthorized"
            );
    }

    @Test
    void shouldThrowUnauthorized_WhenTokenRevoked() {
        RefreshToken revoked = new RefreshToken(
            tokenId,
            userId,
            validToken.tokenHash(),
            "Dev",
            "IP",
            Instant.now().plusSeconds(3600),
            Instant.now().minusSeconds(10)
        );
        when(
            refreshTokenRepository.findByTokenHash(TokenUtils.hash("raw"))
        ).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> useCase.refresh("raw", "Device", "IP"))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessage("Invalid refresh token");
    }

    @Test
    void shouldThrowUnauthorized_WhenTokenExpired() {
        RefreshToken expired = new RefreshToken(
            tokenId,
            userId,
            validToken.tokenHash(),
            "Dev",
            "IP",
            Instant.now().minusSeconds(10),
            null
        );
        when(
            refreshTokenRepository.findByTokenHash(TokenUtils.hash("raw"))
        ).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> useCase.refresh("raw", "Device", "IP"))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessage("Invalid refresh token");
    }

    @Test
    void shouldThrowUnauthorized_WhenUserNotFound() {
        when(
            refreshTokenRepository.findByTokenHash(TokenUtils.hash("raw"))
        ).thenReturn(Optional.of(validToken));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.refresh("raw", "Device", "IP"))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessage("Invalid refresh token");
    }

    @Test
    void shouldThrowUnauthorized_WhenUserDisabled() {
        User disabledUser = User.builder()
            .id(userId)
            .organizationId(orgId)
            .role(UserRole.STUDENT)
            .email("student@test.com")
            .passwordHash("hash")
            .isActive(false)
            .isTemporaryPassword(false)
            .lastLoginAt(null)
            .build();
        when(
            refreshTokenRepository.findByTokenHash(TokenUtils.hash("raw"))
        ).thenReturn(Optional.of(validToken));
        when(userRepository.findById(userId)).thenReturn(
            Optional.of(disabledUser)
        );

        assertThatThrownBy(() -> useCase.refresh("raw", "Device", "IP"))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessage("Invalid refresh token");
    }

    @Test
    void shouldThrowUnauthorized_WhenUserHasTemporaryPassword() {
        User tempPasswordUser = User.builder()
            .id(userId)
            .organizationId(orgId)
            .role(UserRole.STUDENT)
            .email("student@test.com")
            .passwordHash("hash")
            .isActive(true)
            .isTemporaryPassword(true)
            .lastLoginAt(null)
            .build();
        when(
            refreshTokenRepository.findByTokenHash(TokenUtils.hash("raw"))
        ).thenReturn(Optional.of(validToken));
        when(userRepository.findById(userId)).thenReturn(
            Optional.of(tempPasswordUser)
        );

        assertThatThrownBy(() -> useCase.refresh("raw", "Device", "IP"))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessage("Invalid refresh token");
    }
}
