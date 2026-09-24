package com.unihub.workshop.iam.application.usecases;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.unihub.workshop.iam.application.ports.out.JtiBlacklistPort;
import com.unihub.workshop.iam.application.ports.out.RefreshTokenRepository;
import com.unihub.workshop.iam.application.ports.out.SessionTrackerPort;
import com.unihub.workshop.iam.domain.model.RefreshToken;
import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.exception.ServiceUnavailableException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RevokeSessionUseCaseImplTest {

    private RefreshTokenRepository refreshTokenRepository;
    private JtiBlacklistPort jtiBlacklistPort;
    private SessionTrackerPort sessionTrackerPort;
    private RevokeSessionUseCaseImpl useCase;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CURRENT_SESSION_ID = UUID.randomUUID();
    private static final String CURRENT_JTI = "current-jti-123";

    @BeforeEach
    void setUp() {
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        jtiBlacklistPort = mock(JtiBlacklistPort.class);
        sessionTrackerPort = mock(SessionTrackerPort.class);

        useCase = new RevokeSessionUseCaseImpl(
            refreshTokenRepository,
            jtiBlacklistPort,
            sessionTrackerPort
        );
    }

    private RefreshToken createActiveToken(UUID id, UUID userId) {
        return new RefreshToken(
            id,
            userId,
            "hash-" + id,
            "Chrome on Windows",
            "127.0.0.1",
            Instant.now().plusSeconds(3600),
            null
        );
    }

    @Test
    void shouldRevokeCurrentSessionSuccessfully() {
        Instant expiresAt = Instant.now().plusSeconds(1800);
        RefreshToken activeToken = createActiveToken(
            CURRENT_SESSION_ID,
            USER_ID
        );

        when(
            refreshTokenRepository.findActiveSessionByIdAndUserId(
                eq(CURRENT_SESSION_ID),
                eq(USER_ID),
                any(Instant.class)
            )
        ).thenReturn(Optional.of(activeToken));

        useCase.revokeSession(
            USER_ID,
            CURRENT_SESSION_ID,
            CURRENT_SESSION_ID,
            CURRENT_JTI,
            expiresAt
        );

        verify(jtiBlacklistPort).blacklistJti(
            eq(CURRENT_JTI),
            any(Duration.class)
        );
        verify(sessionTrackerPort).removeSession(CURRENT_SESSION_ID);
        verify(refreshTokenRepository).revokeActiveSession(
            eq(CURRENT_SESSION_ID),
            eq(USER_ID),
            any(Instant.class)
        );
    }

    @Test
    void shouldRevokeRemoteSessionSuccessfully_whenActiveJtiExistsInRedis() {
        UUID remoteSessionId = UUID.randomUUID();
        String remoteJti = "remote-jti-456";
        Duration remainingTtl = Duration.ofMinutes(45);
        RefreshToken activeToken = createActiveToken(remoteSessionId, USER_ID);

        when(
            refreshTokenRepository.findActiveSessionByIdAndUserId(
                eq(remoteSessionId),
                eq(USER_ID),
                any(Instant.class)
            )
        ).thenReturn(Optional.of(activeToken));
        when(sessionTrackerPort.getActiveJti(remoteSessionId)).thenReturn(
            Optional.of(remoteJti)
        );
        when(sessionTrackerPort.getRemainingTtl(remoteSessionId)).thenReturn(
            remainingTtl
        );

        useCase.revokeSession(
            USER_ID,
            remoteSessionId,
            CURRENT_SESSION_ID,
            CURRENT_JTI,
            Instant.now().plusSeconds(1800)
        );

        verify(jtiBlacklistPort).blacklistJti(remoteJti, remainingTtl);
        verify(sessionTrackerPort).removeSession(remoteSessionId);
        verify(refreshTokenRepository).revokeActiveSession(
            eq(remoteSessionId),
            eq(USER_ID),
            any(Instant.class)
        );
    }

    @Test
    void shouldRevokeRemoteSessionSuccessfully_whenNoActiveJtiInRedis() {
        UUID remoteSessionId = UUID.randomUUID();
        RefreshToken activeToken = createActiveToken(remoteSessionId, USER_ID);

        when(
            refreshTokenRepository.findActiveSessionByIdAndUserId(
                eq(remoteSessionId),
                eq(USER_ID),
                any(Instant.class)
            )
        ).thenReturn(Optional.of(activeToken));
        when(sessionTrackerPort.getActiveJti(remoteSessionId)).thenReturn(
            Optional.empty()
        );

        useCase.revokeSession(
            USER_ID,
            remoteSessionId,
            CURRENT_SESSION_ID,
            CURRENT_JTI,
            Instant.now().plusSeconds(1800)
        );

        verify(jtiBlacklistPort, never()).blacklistJti(any(), any());
        verify(sessionTrackerPort).removeSession(remoteSessionId);
        verify(refreshTokenRepository).revokeActiveSession(
            eq(remoteSessionId),
            eq(USER_ID),
            any(Instant.class)
        );
    }

    @Test
    void shouldThrowNotFoundException_whenSessionDoesNotExistOrIsNotActive() {
        UUID nonExistentId = UUID.randomUUID();

        when(
            refreshTokenRepository.findActiveSessionByIdAndUserId(
                eq(nonExistentId),
                eq(USER_ID),
                any(Instant.class)
            )
        ).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            useCase.revokeSession(
                USER_ID,
                nonExistentId,
                CURRENT_SESSION_ID,
                CURRENT_JTI,
                Instant.now().plusSeconds(1800)
            )
        )
            .isInstanceOf(NotFoundException.class)
            .hasMessage("Session not found or already revoked");

        verifyNoInteractions(jtiBlacklistPort);
        verify(sessionTrackerPort, never()).removeSession(any());
        verify(refreshTokenRepository, never()).revokeActiveSession(
            any(),
            any(),
            any()
        );
    }

    @Test
    void shouldFailClosed_whenRedisIsUnavailableDuringCurrentSessionRevocation() {
        Instant expiresAt = Instant.now().plusSeconds(1800);
        RefreshToken activeToken = createActiveToken(
            CURRENT_SESSION_ID,
            USER_ID
        );

        when(
            refreshTokenRepository.findActiveSessionByIdAndUserId(
                eq(CURRENT_SESSION_ID),
                eq(USER_ID),
                any(Instant.class)
            )
        ).thenReturn(Optional.of(activeToken));
        doThrow(
            new ServiceUnavailableException(
                "urn:unihub:error:service-unavailable",
                "Redis is unavailable"
            )
        )
            .when(jtiBlacklistPort)
            .blacklistJti(anyString(), any());

        assertThatThrownBy(() ->
            useCase.revokeSession(
                USER_ID,
                CURRENT_SESSION_ID,
                CURRENT_SESSION_ID,
                CURRENT_JTI,
                expiresAt
            )
        ).isInstanceOf(ServiceUnavailableException.class);

        verify(refreshTokenRepository, never()).revokeActiveSession(
            any(),
            any(),
            any()
        );
    }

    @Test
    void shouldFailClosed_whenRedisIsUnavailableDuringRemoteSessionRevocation() {
        UUID remoteSessionId = UUID.randomUUID();
        RefreshToken activeToken = createActiveToken(remoteSessionId, USER_ID);

        when(
            refreshTokenRepository.findActiveSessionByIdAndUserId(
                eq(remoteSessionId),
                eq(USER_ID),
                any(Instant.class)
            )
        ).thenReturn(Optional.of(activeToken));
        when(sessionTrackerPort.getActiveJti(remoteSessionId)).thenThrow(
            new ServiceUnavailableException(
                "urn:unihub:error:service-unavailable",
                "Redis is unavailable"
            )
        );

        assertThatThrownBy(() ->
            useCase.revokeSession(
                USER_ID,
                remoteSessionId,
                CURRENT_SESSION_ID,
                CURRENT_JTI,
                Instant.now().plusSeconds(1800)
            )
        ).isInstanceOf(ServiceUnavailableException.class);

        verify(refreshTokenRepository, never()).revokeActiveSession(
            any(),
            any(),
            any()
        );
    }

    @Test
    void shouldThrowBadRequestException_whenUserIdOrSessionIdToRevokeIsNull() {
        assertThatThrownBy(() ->
            useCase.revokeSession(
                null,
                UUID.randomUUID(),
                CURRENT_SESSION_ID,
                CURRENT_JTI,
                Instant.now()
            )
        ).isInstanceOf(BadRequestException.class);

        assertThatThrownBy(() ->
            useCase.revokeSession(
                USER_ID,
                null,
                CURRENT_SESSION_ID,
                CURRENT_JTI,
                Instant.now()
            )
        ).isInstanceOf(BadRequestException.class);
    }

    @Test
    void shouldRevokeCurrentSession_whenTokenExpiresAtIsInThePast_clampsTtlToZero() {
        Instant pastExpiration = Instant.now().minusSeconds(100);
        RefreshToken activeToken = createActiveToken(
            CURRENT_SESSION_ID,
            USER_ID
        );

        when(
            refreshTokenRepository.findActiveSessionByIdAndUserId(
                eq(CURRENT_SESSION_ID),
                eq(USER_ID),
                any(Instant.class)
            )
        ).thenReturn(Optional.of(activeToken));

        useCase.revokeSession(
            USER_ID,
            CURRENT_SESSION_ID,
            CURRENT_SESSION_ID,
            CURRENT_JTI,
            pastExpiration
        );

        verify(jtiBlacklistPort).blacklistJti(
            eq(CURRENT_JTI),
            eq(Duration.ZERO)
        );
        verify(sessionTrackerPort).removeSession(CURRENT_SESSION_ID);
        verify(refreshTokenRepository).revokeActiveSession(
            eq(CURRENT_SESSION_ID),
            eq(USER_ID),
            any(Instant.class)
        );
    }

    @Test
    void shouldRevokeRemoteSession_whenActiveJtiIsBlank_doesNotBlacklist() {
        UUID remoteSessionId = UUID.randomUUID();
        RefreshToken activeToken = createActiveToken(remoteSessionId, USER_ID);

        when(
            refreshTokenRepository.findActiveSessionByIdAndUserId(
                eq(remoteSessionId),
                eq(USER_ID),
                any(Instant.class)
            )
        ).thenReturn(Optional.of(activeToken));
        when(sessionTrackerPort.getActiveJti(remoteSessionId)).thenReturn(
            Optional.of("   ")
        );

        useCase.revokeSession(
            USER_ID,
            remoteSessionId,
            CURRENT_SESSION_ID,
            CURRENT_JTI,
            Instant.now().plusSeconds(1800)
        );

        verify(jtiBlacklistPort, never()).blacklistJti(any(), any());
        verify(sessionTrackerPort).removeSession(remoteSessionId);
        verify(refreshTokenRepository).revokeActiveSession(
            eq(remoteSessionId),
            eq(USER_ID),
            any(Instant.class)
        );
    }

    @Test
    void shouldRevokeRemoteSession_whenRemainingTtlIsZeroOrNegative_clampsToZero() {
        UUID remoteSessionId = UUID.randomUUID();
        String remoteJti = "remote-jti-789";
        RefreshToken activeToken = createActiveToken(remoteSessionId, USER_ID);

        when(
            refreshTokenRepository.findActiveSessionByIdAndUserId(
                eq(remoteSessionId),
                eq(USER_ID),
                any(Instant.class)
            )
        ).thenReturn(Optional.of(activeToken));
        when(sessionTrackerPort.getActiveJti(remoteSessionId)).thenReturn(
            Optional.of(remoteJti)
        );
        when(sessionTrackerPort.getRemainingTtl(remoteSessionId)).thenReturn(
            Duration.ofSeconds(-10)
        );

        useCase.revokeSession(
            USER_ID,
            remoteSessionId,
            CURRENT_SESSION_ID,
            CURRENT_JTI,
            Instant.now().plusSeconds(1800)
        );

        verify(jtiBlacklistPort).blacklistJti(eq(remoteJti), eq(Duration.ZERO));
        verify(sessionTrackerPort).removeSession(remoteSessionId);
        verify(refreshTokenRepository).revokeActiveSession(
            eq(remoteSessionId),
            eq(USER_ID),
            any(Instant.class)
        );
    }
}
