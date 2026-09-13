package com.unihub.workshop.iam.application.usecases;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.unihub.workshop.iam.application.ports.out.JtiBlacklistPort;
import com.unihub.workshop.iam.application.ports.out.RefreshTokenRepository;
import com.unihub.workshop.iam.application.ports.out.SessionTrackerPort;
import com.unihub.workshop.iam.domain.model.RefreshToken;
import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.exception.ServiceUnavailableException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RevokeAllOtherSessionsUseCaseImplTest {

    private RefreshTokenRepository refreshTokenRepository;
    private JtiBlacklistPort jtiBlacklistPort;
    private SessionTrackerPort sessionTrackerPort;
    private MeterRegistry meterRegistry;
    private Counter counter;

    private RevokeAllOtherSessionsUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        jtiBlacklistPort = mock(JtiBlacklistPort.class);
        sessionTrackerPort = mock(SessionTrackerPort.class);
        meterRegistry = mock(MeterRegistry.class);
        counter = mock(Counter.class);

        when(
            meterRegistry.counter(anyString(), any(String[].class))
        ).thenReturn(counter);

        useCase = new RevokeAllOtherSessionsUseCaseImpl(
            refreshTokenRepository,
            jtiBlacklistPort,
            sessionTrackerPort,
            meterRegistry
        );
    }

    @Test
    void shouldThrowBadRequestException_whenUserIdIsNull() {
        UUID currentSessionId = UUID.randomUUID();

        assertThatThrownBy(() ->
            useCase.revokeAllOtherSessions(null, currentSessionId)
        )
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("must not be null");

        verifyNoInteractions(
            refreshTokenRepository,
            jtiBlacklistPort,
            sessionTrackerPort
        );
    }

    @Test
    void shouldThrowBadRequestException_whenCurrentSessionIdIsNull() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> useCase.revokeAllOtherSessions(userId, null))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("must not be null");

        verifyNoInteractions(
            refreshTokenRepository,
            jtiBlacklistPort,
            sessionTrackerPort
        );
    }

    @Test
    void shouldRevokeAllOtherSessionsSuccessfully_whenRemoteSessionsExist() {
        UUID userId = UUID.randomUUID();
        UUID currentSessionId = UUID.randomUUID();
        UUID remoteId1 = UUID.randomUUID();
        UUID remoteId2 = UUID.randomUUID();

        RefreshToken r1 = new RefreshToken(
            remoteId1,
            userId,
            "hash1",
            "Firefox",
            "1.1.1.1",
            Instant.now().plusSeconds(3600),
            null
        );
        RefreshToken r2 = new RefreshToken(
            remoteId2,
            userId,
            "hash2",
            "Safari",
            "2.2.2.2",
            Instant.now().plusSeconds(1800),
            null
        );

        when(
            refreshTokenRepository.findActiveRemoteSessions(
                eq(userId),
                eq(currentSessionId),
                any(Instant.class)
            )
        ).thenReturn(List.of(r1, r2));

        when(sessionTrackerPort.getActiveJti(remoteId1)).thenReturn(
            Optional.of("jti-remote-1")
        );
        when(sessionTrackerPort.getRemainingTtl(remoteId1)).thenReturn(
            Duration.ofSeconds(3600)
        );

        when(sessionTrackerPort.getActiveJti(remoteId2)).thenReturn(
            Optional.of("jti-remote-2")
        );
        when(sessionTrackerPort.getRemainingTtl(remoteId2)).thenReturn(
            Duration.ofSeconds(1800)
        );

        useCase.revokeAllOtherSessions(userId, currentSessionId);

        // Verify Redis blacklist and session removal occurred for both remote sessions
        verify(jtiBlacklistPort).blacklistJti(
            "jti-remote-1",
            Duration.ofSeconds(3600)
        );
        verify(sessionTrackerPort).removeSession(remoteId1);

        verify(jtiBlacklistPort).blacklistJti(
            "jti-remote-2",
            Duration.ofSeconds(1800)
        );
        verify(sessionTrackerPort).removeSession(remoteId2);

        // Verify primary DB bulk revocation was invoked
        verify(refreshTokenRepository).revokeAllOtherActiveSessions(
            eq(userId),
            eq(currentSessionId),
            any(Instant.class)
        );

        // Verify metrics
        verify(meterRegistry).counter(
            "auth.sessions.revoked",
            "outcome",
            "success",
            "target",
            "all_others"
        );
        verify(counter).increment();
    }

    @Test
    void shouldHandleZeroRemoteSessionsGracefully() {
        UUID userId = UUID.randomUUID();
        UUID currentSessionId = UUID.randomUUID();

        when(
            refreshTokenRepository.findActiveRemoteSessions(
                eq(userId),
                eq(currentSessionId),
                any(Instant.class)
            )
        ).thenReturn(List.of());

        useCase.revokeAllOtherSessions(userId, currentSessionId);

        // No Redis calls needed
        verifyNoInteractions(jtiBlacklistPort);
        verify(sessionTrackerPort, never()).removeSession(any());

        // Verify metrics recorded
        verify(meterRegistry).counter(
            "auth.sessions.revoked",
            "outcome",
            "success",
            "target",
            "all_others"
        );
        verify(counter).increment();
    }

    @Test
    void shouldFailClosedAndNotMutateDb_whenRedisFailsOnBlacklist() {
        UUID userId = UUID.randomUUID();
        UUID currentSessionId = UUID.randomUUID();
        UUID remoteId1 = UUID.randomUUID();

        RefreshToken r1 = new RefreshToken(
            remoteId1,
            userId,
            "hash1",
            "Firefox",
            "1.1.1.1",
            Instant.now().plusSeconds(3600),
            null
        );

        when(
            refreshTokenRepository.findActiveRemoteSessions(
                eq(userId),
                eq(currentSessionId),
                any(Instant.class)
            )
        ).thenReturn(List.of(r1));

        when(sessionTrackerPort.getActiveJti(remoteId1)).thenReturn(
            Optional.of("jti-remote-1")
        );
        when(sessionTrackerPort.getRemainingTtl(remoteId1)).thenReturn(
            Duration.ofSeconds(3600)
        );

        doThrow(
            new ServiceUnavailableException(
                "urn:unihub:error:service-unavailable",
                "Redis down"
            )
        )
            .when(jtiBlacklistPort)
            .blacklistJti("jti-remote-1", Duration.ofSeconds(3600));

        assertThatThrownBy(() ->
            useCase.revokeAllOtherSessions(userId, currentSessionId)
        ).isInstanceOf(ServiceUnavailableException.class);

        // DB update must NOT have occurred
        verify(refreshTokenRepository, never()).revokeAllOtherActiveSessions(
            any(),
            any(),
            any()
        );
    }

    @Test
    void shouldFailClosedAndNotMutateDb_whenRedisFailsOnSessionRemoval() {
        UUID userId = UUID.randomUUID();
        UUID currentSessionId = UUID.randomUUID();
        UUID remoteId1 = UUID.randomUUID();

        RefreshToken r1 = new RefreshToken(
            remoteId1,
            userId,
            "hash1",
            "Firefox",
            "1.1.1.1",
            Instant.now().plusSeconds(3600),
            null
        );

        when(
            refreshTokenRepository.findActiveRemoteSessions(
                eq(userId),
                eq(currentSessionId),
                any(Instant.class)
            )
        ).thenReturn(List.of(r1));

        when(sessionTrackerPort.getActiveJti(remoteId1)).thenReturn(
            Optional.of("jti-remote-1")
        );
        when(sessionTrackerPort.getRemainingTtl(remoteId1)).thenReturn(
            Duration.ofSeconds(3600)
        );

        doThrow(
            new ServiceUnavailableException(
                "urn:unihub:error:service-unavailable",
                "Redis down"
            )
        )
            .when(sessionTrackerPort)
            .removeSession(remoteId1);

        assertThatThrownBy(() ->
            useCase.revokeAllOtherSessions(userId, currentSessionId)
        ).isInstanceOf(ServiceUnavailableException.class);

        // DB update must NOT have occurred
        verify(refreshTokenRepository, never()).revokeAllOtherActiveSessions(
            any(),
            any(),
            any()
        );
    }
}
