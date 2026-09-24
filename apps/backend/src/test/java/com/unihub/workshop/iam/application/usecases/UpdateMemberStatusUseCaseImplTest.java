package com.unihub.workshop.iam.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.unihub.workshop.iam.application.ports.in.UpdateMemberStatusCommand;
import com.unihub.workshop.iam.application.ports.out.JtiBlacklistPort;
import com.unihub.workshop.iam.application.ports.out.RefreshTokenRepository;
import com.unihub.workshop.iam.application.ports.out.SessionTrackerPort;
import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.domain.model.RefreshToken;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserRole;
import com.unihub.workshop.shared.events.UserStatusChangedEvent;
import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.exception.ServiceUnavailableException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class UpdateMemberStatusUseCaseImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JtiBlacklistPort jtiBlacklistPort;

    @Mock
    private SessionTrackerPort sessionTrackerPort;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private UpdateMemberStatusUseCaseImpl useCase;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID targetUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new UpdateMemberStatusUseCaseImpl(
            userRepository,
            refreshTokenRepository,
            jtiBlacklistPort,
            sessionTrackerPort,
            eventPublisher
        );
    }

    private User createSampleUser(UUID id, UUID orgId, boolean isActive) {
        Instant now = Instant.now();
        return new User(
            id,
            orgId,
            UserRole.STAFF,
            "staff@example.com",
            "+84901234567",
            "hash123",
            "Staff Member",
            null,
            isActive,
            false,
            null,
            null,
            now,
            now,
            callerId,
            null
        );
    }

    @Test
    @DisplayName(
        "Ban member: invalidates Redis sessions, revokes DB tokens, updates is_active=false, and publishes event"
    )
    void shouldBanMemberSuccessfully_whenValidRequest() {
        User targetUser = createSampleUser(targetUserId, organizationId, true);
        when(
            userRepository.findByIdAndOrganizationId(
                targetUserId,
                organizationId
            )
        ).thenReturn(Optional.of(targetUser));

        UUID session1Id = UUID.randomUUID();
        UUID session2Id = UUID.randomUUID();
        RefreshToken session1 = new RefreshToken(
            session1Id,
            targetUserId,
            "hash1",
            "device1",
            "127.0.0.1",
            Instant.now().plusSeconds(3600),
            null
        );
        RefreshToken session2 = new RefreshToken(
            session2Id,
            targetUserId,
            "hash2",
            "device2",
            "127.0.0.1",
            Instant.now().plusSeconds(7200),
            null
        );
        when(
            refreshTokenRepository.findAllActiveSessionsForUser(
                eq(targetUserId),
                any(Instant.class)
            )
        ).thenReturn(List.of(session1, session2));

        when(sessionTrackerPort.getActiveJti(session1Id)).thenReturn(
            Optional.of("jti-1")
        );
        when(sessionTrackerPort.getRemainingTtl(session1Id)).thenReturn(
            Duration.ofMinutes(30)
        );
        when(sessionTrackerPort.getActiveJti(session2Id)).thenReturn(
            Optional.of("jti-2")
        );
        when(sessionTrackerPort.getRemainingTtl(session2Id)).thenReturn(
            Duration.ofMinutes(60)
        );

        UpdateMemberStatusCommand command = new UpdateMemberStatusCommand(
            organizationId,
            callerId,
            targetUserId,
            false
        );

        User result = useCase.execute(command);

        assertThat(result).isNotNull();
        assertThat(result.isActive()).isFalse();

        // Verify Redis blacklist & session removal
        verify(jtiBlacklistPort).blacklistJti("jti-1", Duration.ofMinutes(30));
        verify(jtiBlacklistPort).blacklistJti("jti-2", Duration.ofMinutes(60));
        verify(sessionTrackerPort).removeSession(session1Id);
        verify(sessionTrackerPort).removeSession(session2Id);

        // Verify DB mutations
        verify(refreshTokenRepository).revokeAllActiveTokensForUser(
            eq(targetUserId),
            any(Instant.class)
        );
        verify(userRepository).updateIsActive(
            targetUserId,
            organizationId,
            false
        );

        // Verify outbox event
        ArgumentCaptor<UserStatusChangedEvent> eventCaptor =
            ArgumentCaptor.forClass(UserStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        UserStatusChangedEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.userId()).isEqualTo(targetUserId);
        assertThat(publishedEvent.organizationId()).isEqualTo(organizationId);
        assertThat(publishedEvent.email()).isEqualTo("staff@example.com");
        assertThat(publishedEvent.isActive()).isFalse();
    }

    @Test
    @DisplayName(
        "Ban member fail-closed: Redis failure throws 503 and aborts DB mutations and event"
    )
    void shouldFailClosedWith503_whenRedisFailsDuringBan() {
        User targetUser = createSampleUser(targetUserId, organizationId, true);
        when(
            userRepository.findByIdAndOrganizationId(
                targetUserId,
                organizationId
            )
        ).thenReturn(Optional.of(targetUser));

        UUID sessionId = UUID.randomUUID();
        RefreshToken session = new RefreshToken(
            sessionId,
            targetUserId,
            "hash1",
            "device1",
            "127.0.0.1",
            Instant.now().plusSeconds(3600),
            null
        );
        when(
            refreshTokenRepository.findAllActiveSessionsForUser(
                eq(targetUserId),
                any(Instant.class)
            )
        ).thenReturn(List.of(session));

        when(sessionTrackerPort.getActiveJti(sessionId)).thenThrow(
            new ServiceUnavailableException(
                "urn:unihub:error:service-unavailable",
                "Redis unreachable"
            )
        );

        UpdateMemberStatusCommand command = new UpdateMemberStatusCommand(
            organizationId,
            callerId,
            targetUserId,
            false
        );

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ServiceUnavailableException.class)
            .satisfies(ex -> {
                ServiceUnavailableException sue =
                    (ServiceUnavailableException) ex;
                assertThat(sue.getType()).isEqualTo(
                    "urn:unihub:error:service-unavailable"
                );
                assertThat(sue.getRetryAfterSeconds()).isEqualTo(30);
                assertThat(sue.getMessage()).contains(
                    "Session revocation service is temporarily unavailable"
                );
            });

        // Fail-closed verification: NO DB writes, NO events
        verify(refreshTokenRepository, never()).revokeAllActiveTokensForUser(
            any(),
            any()
        );
        verify(userRepository, never()).updateIsActive(
            any(),
            any(),
            anyBoolean()
        );
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName(
        "Self-modification forbidden: Caller modifying their own status throws 403"
    )
    void shouldRejectSelfStatusModification_with403Forbidden() {
        UpdateMemberStatusCommand command = new UpdateMemberStatusCommand(
            organizationId,
            callerId,
            callerId,
            false
        );

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ForbiddenException.class)
            .satisfies(ex -> {
                ForbiddenException fe = (ForbiddenException) ex;
                assertThat(fe.getType()).isEqualTo(
                    "urn:unihub:error:self-status-modification-forbidden"
                );
                assertThat(fe.getMessage()).isEqualTo(
                    "Organizers cannot modify their own active status."
                );
            });

        verifyNoInteractions(userRepository);
        verifyNoInteractions(refreshTokenRepository);
        verifyNoInteractions(jtiBlacklistPort);
        verifyNoInteractions(sessionTrackerPort);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName(
        "Unban member: updates is_active=true, publishes event, and performs NO Redis calls"
    )
    void shouldUnbanMemberSuccessfully_withoutRedisCalls() {
        User targetUser = createSampleUser(targetUserId, organizationId, false);
        when(
            userRepository.findByIdAndOrganizationId(
                targetUserId,
                organizationId
            )
        ).thenReturn(Optional.of(targetUser));

        UpdateMemberStatusCommand command = new UpdateMemberStatusCommand(
            organizationId,
            callerId,
            targetUserId,
            true
        );

        User result = useCase.execute(command);

        assertThat(result).isNotNull();
        assertThat(result.isActive()).isTrue();

        // Zero Redis operations on unban
        verifyNoInteractions(jtiBlacklistPort);
        verifyNoInteractions(sessionTrackerPort);
        verifyNoInteractions(refreshTokenRepository);

        // DB update and event published
        verify(userRepository).updateIsActive(
            targetUserId,
            organizationId,
            true
        );
        ArgumentCaptor<UserStatusChangedEvent> eventCaptor =
            ArgumentCaptor.forClass(UserStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().isActive()).isTrue();
    }

    @Test
    @DisplayName(
        "Idempotent no-op: when requested isActive matches current status, perform zero writes and return existing user"
    )
    void shouldReturnCurrentStatusAsNoOp_whenStatusAlreadyMatches() {
        User targetUser = createSampleUser(targetUserId, organizationId, false);
        when(
            userRepository.findByIdAndOrganizationId(
                targetUserId,
                organizationId
            )
        ).thenReturn(Optional.of(targetUser));

        UpdateMemberStatusCommand command = new UpdateMemberStatusCommand(
            organizationId,
            callerId,
            targetUserId,
            false
        );

        User result = useCase.execute(command);

        assertThat(result).isEqualTo(targetUser);

        // Zero Redis calls, Zero DB mutations, Zero events
        verifyNoInteractions(jtiBlacklistPort);
        verifyNoInteractions(sessionTrackerPort);
        verifyNoInteractions(refreshTokenRepository);
        verify(userRepository, never()).updateIsActive(
            any(),
            any(),
            anyBoolean()
        );
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName(
        "Member not found: returns 404 when target does not exist or belongs to different org"
    )
    void shouldThrow404NotFound_whenUserNotFoundInOrganization() {
        when(
            userRepository.findByIdAndOrganizationId(
                targetUserId,
                organizationId
            )
        ).thenReturn(Optional.empty());

        UpdateMemberStatusCommand command = new UpdateMemberStatusCommand(
            organizationId,
            callerId,
            targetUserId,
            false
        );

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(NotFoundException.class)
            .hasMessage("Member not found in your organization.");

        verify(userRepository, never()).updateIsActive(
            any(),
            any(),
            anyBoolean()
        );
        verifyNoInteractions(refreshTokenRepository);
        verifyNoInteractions(jtiBlacklistPort);
        verifyNoInteractions(sessionTrackerPort);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Null parameters: throws BadRequestException")
    void shouldThrowBadRequest_whenParametersAreNull() {
        assertThatThrownBy(() ->
            useCase.execute(
                new UpdateMemberStatusCommand(
                    null,
                    callerId,
                    targetUserId,
                    true
                )
            )
        ).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() ->
            useCase.execute(
                new UpdateMemberStatusCommand(
                    organizationId,
                    null,
                    targetUserId,
                    true
                )
            )
        ).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() ->
            useCase.execute(
                new UpdateMemberStatusCommand(
                    organizationId,
                    callerId,
                    null,
                    true
                )
            )
        ).isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("Null command: throws BadRequestException")
    void shouldThrowBadRequest_whenCommandIsNull() {
        assertThatThrownBy(() -> useCase.execute(null))
            .isInstanceOf(BadRequestException.class)
            .hasMessage(
                "Organization ID, caller ID, and target user ID must not be null"
            );
    }

    @Test
    @DisplayName(
        "Ban member with 0 active sessions: revokes DB tokens, updates is_active=false, and publishes event"
    )
    void shouldBanMemberSuccessfully_whenUserHasZeroActiveSessions() {
        User targetUser = createSampleUser(targetUserId, organizationId, true);
        when(
            userRepository.findByIdAndOrganizationId(
                targetUserId,
                organizationId
            )
        ).thenReturn(Optional.of(targetUser));
        when(
            refreshTokenRepository.findAllActiveSessionsForUser(
                eq(targetUserId),
                any(Instant.class)
            )
        ).thenReturn(List.of());

        UpdateMemberStatusCommand command = new UpdateMemberStatusCommand(
            organizationId,
            callerId,
            targetUserId,
            false
        );

        User result = useCase.execute(command);

        assertThat(result).isNotNull();
        assertThat(result.isActive()).isFalse();

        verifyNoInteractions(jtiBlacklistPort);
        verifyNoInteractions(sessionTrackerPort);

        verify(refreshTokenRepository).revokeAllActiveTokensForUser(
            eq(targetUserId),
            any(Instant.class)
        );
        verify(userRepository).updateIsActive(
            targetUserId,
            organizationId,
            false
        );
        verify(eventPublisher).publishEvent(any(UserStatusChangedEvent.class));
    }

    @Test
    @DisplayName(
        "Ban member: skips blacklist when remoteJti is blank string, but still removes session"
    )
    void shouldSkipBlacklist_whenRemoteJtiIsBlank() {
        User targetUser = createSampleUser(targetUserId, organizationId, true);
        when(
            userRepository.findByIdAndOrganizationId(
                targetUserId,
                organizationId
            )
        ).thenReturn(Optional.of(targetUser));

        UUID sessionId = UUID.randomUUID();
        RefreshToken session = new RefreshToken(
            sessionId,
            targetUserId,
            "hash1",
            "device1",
            "127.0.0.1",
            Instant.now().plusSeconds(3600),
            null
        );
        when(
            refreshTokenRepository.findAllActiveSessionsForUser(
                eq(targetUserId),
                any(Instant.class)
            )
        ).thenReturn(List.of(session));

        when(sessionTrackerPort.getActiveJti(sessionId)).thenReturn(
            Optional.of("   ")
        );

        UpdateMemberStatusCommand command = new UpdateMemberStatusCommand(
            organizationId,
            callerId,
            targetUserId,
            false
        );

        User result = useCase.execute(command);

        assertThat(result.isActive()).isFalse();
        verify(jtiBlacklistPort, never()).blacklistJti(any(), any());
        verify(sessionTrackerPort).removeSession(sessionId);
        verify(userRepository).updateIsActive(
            targetUserId,
            organizationId,
            false
        );
    }

    @Test
    @DisplayName(
        "Ban member: clamps remaining TTL to Duration.ZERO when TTL is negative or null"
    )
    void shouldClampRemainingTtlToZero_whenTtlIsNegativeOrNull() {
        User targetUser = createSampleUser(targetUserId, organizationId, true);
        when(
            userRepository.findByIdAndOrganizationId(
                targetUserId,
                organizationId
            )
        ).thenReturn(Optional.of(targetUser));

        UUID session1Id = UUID.randomUUID();
        UUID session2Id = UUID.randomUUID();
        RefreshToken session1 = new RefreshToken(
            session1Id,
            targetUserId,
            "hash1",
            "device1",
            "127.0.0.1",
            Instant.now().plusSeconds(3600),
            null
        );
        RefreshToken session2 = new RefreshToken(
            session2Id,
            targetUserId,
            "hash2",
            "device2",
            "127.0.0.1",
            Instant.now().plusSeconds(7200),
            null
        );
        when(
            refreshTokenRepository.findAllActiveSessionsForUser(
                eq(targetUserId),
                any(Instant.class)
            )
        ).thenReturn(List.of(session1, session2));

        when(sessionTrackerPort.getActiveJti(session1Id)).thenReturn(
            Optional.of("jti-1")
        );
        when(sessionTrackerPort.getRemainingTtl(session1Id)).thenReturn(null);

        when(sessionTrackerPort.getActiveJti(session2Id)).thenReturn(
            Optional.of("jti-2")
        );
        when(sessionTrackerPort.getRemainingTtl(session2Id)).thenReturn(
            Duration.ofSeconds(-10)
        );

        UpdateMemberStatusCommand command = new UpdateMemberStatusCommand(
            organizationId,
            callerId,
            targetUserId,
            false
        );

        useCase.execute(command);

        verify(jtiBlacklistPort).blacklistJti("jti-1", Duration.ZERO);
        verify(jtiBlacklistPort).blacklistJti("jti-2", Duration.ZERO);
        verify(sessionTrackerPort).removeSession(session1Id);
        verify(sessionTrackerPort).removeSession(session2Id);
    }

    @Test
    @DisplayName(
        "Ban member fail-closed: exception during removeSession throws 503 and aborts DB mutations"
    )
    void shouldFailClosedWith503_whenRemoveSessionThrowsException() {
        User targetUser = createSampleUser(targetUserId, organizationId, true);
        when(
            userRepository.findByIdAndOrganizationId(
                targetUserId,
                organizationId
            )
        ).thenReturn(Optional.of(targetUser));

        UUID sessionId = UUID.randomUUID();
        RefreshToken session = new RefreshToken(
            sessionId,
            targetUserId,
            "hash1",
            "device1",
            "127.0.0.1",
            Instant.now().plusSeconds(3600),
            null
        );
        when(
            refreshTokenRepository.findAllActiveSessionsForUser(
                eq(targetUserId),
                any(Instant.class)
            )
        ).thenReturn(List.of(session));

        when(sessionTrackerPort.getActiveJti(sessionId)).thenReturn(
            Optional.empty()
        );
        doThrow(new RuntimeException("Redis connection timeout"))
            .when(sessionTrackerPort)
            .removeSession(sessionId);

        UpdateMemberStatusCommand command = new UpdateMemberStatusCommand(
            organizationId,
            callerId,
            targetUserId,
            false
        );

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ServiceUnavailableException.class)
            .satisfies(ex -> {
                ServiceUnavailableException sue =
                    (ServiceUnavailableException) ex;
                assertThat(sue.getRetryAfterSeconds()).isEqualTo(30);
            });

        verify(refreshTokenRepository, never()).revokeAllActiveTokensForUser(
            any(),
            any()
        );
        verify(userRepository, never()).updateIsActive(
            any(),
            any(),
            anyBoolean()
        );
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName(
        "Self-modification forbidden: Caller unbanning their own status (isActive=true) throws 403"
    )
    void shouldRejectSelfStatusModification_whenUnbanningSelf() {
        UpdateMemberStatusCommand command = new UpdateMemberStatusCommand(
            organizationId,
            callerId,
            callerId,
            true
        );

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ForbiddenException.class)
            .satisfies(ex -> {
                ForbiddenException fe = (ForbiddenException) ex;
                assertThat(fe.getType()).isEqualTo(
                    "urn:unihub:error:self-status-modification-forbidden"
                );
            });

        verifyNoInteractions(userRepository);
        verifyNoInteractions(refreshTokenRepository);
        verifyNoInteractions(jtiBlacklistPort);
        verifyNoInteractions(sessionTrackerPort);
        verifyNoInteractions(eventPublisher);
    }
}
