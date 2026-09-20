package com.unihub.workshop.iam.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.unihub.workshop.iam.domain.model.ActiveSession;
import com.unihub.workshop.iam.domain.model.CursorPage;
import com.unihub.workshop.iam.domain.model.RefreshToken;
import com.unihub.workshop.iam.infrastructure.persistence.mapper.RefreshTokenPersistenceMapper;
import com.unihub.workshop.iam.infrastructure.persistence.mapper.RefreshTokenPersistenceMapperImpl;
import com.unihub.workshop.shared.exception.BadRequestException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

class RefreshTokenRepositoryAdapterTest {

    private RefreshTokenJpaRepository repository;
    private RefreshTokenPersistenceMapper tokenMapper;
    private RefreshTokenRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        repository = mock(RefreshTokenJpaRepository.class);
        tokenMapper = new RefreshTokenPersistenceMapperImpl();
        adapter = new RefreshTokenRepositoryAdapter(repository, tokenMapper);
    }

    @Test
    void shouldFindActiveSessions_withoutCursor_returnsItemsAndNextCursorWhenHasMore() {
        UUID userId = UUID.randomUUID();
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();

        Instant t1 = Instant.parse("2026-09-04T10:00:00Z");
        Instant t2 = Instant.parse("2026-09-04T09:00:00Z");
        Instant t3 = Instant.parse("2026-09-04T08:00:00Z");

        RefreshTokenJpaEntity e1 = createEntity(
            id1,
            userId,
            "Chrome",
            "1.1.1.1",
            t1
        );
        RefreshTokenJpaEntity e2 = createEntity(
            id2,
            userId,
            "Firefox",
            "2.2.2.2",
            t2
        );
        RefreshTokenJpaEntity e3 = createEntity(
            id3,
            userId,
            "Safari",
            "3.3.3.3",
            t3
        );

        when(
            repository.findFirstPage(
                eq(userId),
                any(Instant.class),
                eq(PageRequest.of(0, 3))
            )
        ).thenReturn(List.of(e1, e2, e3));

        CursorPage<ActiveSession> page = adapter.findActiveSessions(
            userId,
            null,
            2
        );

        assertThat(page.items()).hasSize(2);
        assertThat(page.items().get(0).id()).isEqualTo(id1);
        assertThat(page.items().get(1).id()).isEqualTo(id2);
        assertThat(page.hasMore()).isTrue();
        assertThat(page.nextCursor()).isNotNull();

        // Decode next cursor and verify it points to e2
        String decoded = new String(
            Base64.getDecoder().decode(page.nextCursor())
        );
        assertThat(decoded).contains(id2.toString());
    }

    @Test
    void shouldFindActiveSessions_withValidCursor_decodesAndQueriesRepository()
        throws Exception {
        UUID userId = UUID.randomUUID();
        UUID cursorId = UUID.randomUUID();
        Instant cursorTime = Instant.parse("2026-09-04T09:00:00Z");

        ObjectMapper mapper = new ObjectMapper().registerModule(
            new JavaTimeModule()
        );
        String cursorJson = mapper.writeValueAsString(
            new CursorData(cursorTime, cursorId)
        );
        String cursor = Base64.getEncoder().encodeToString(
            cursorJson.getBytes()
        );

        UUID id3 = UUID.randomUUID();
        Instant t3 = Instant.parse("2026-09-04T08:00:00Z");
        RefreshTokenJpaEntity e3 = createEntity(
            id3,
            userId,
            "Safari",
            "3.3.3.3",
            t3
        );

        when(
            repository.findNextPage(
                eq(userId),
                any(Instant.class),
                eq(cursorTime),
                eq(cursorId),
                eq(PageRequest.of(0, 3))
            )
        ).thenReturn(List.of(e3));

        CursorPage<ActiveSession> page = adapter.findActiveSessions(
            userId,
            cursor,
            2
        );

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).id()).isEqualTo(id3);
        assertThat(page.hasMore()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void shouldThrowBadRequestException_whenCursorIsMalformed() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() ->
            adapter.findActiveSessions(userId, "not-valid-base64", 20)
        )
            .isInstanceOf(BadRequestException.class)
            .hasMessage("Invalid cursor format");

        String invalidJson = Base64.getEncoder().encodeToString(
            "{\"random\":123}".getBytes()
        );
        assertThatThrownBy(() ->
            adapter.findActiveSessions(userId, invalidJson, 20)
        )
            .isInstanceOf(BadRequestException.class)
            .hasMessage("Invalid cursor format");

        String missingFieldJson = Base64.getEncoder().encodeToString(
            "{\"id\":\"00000000-0000-0000-0000-000000000000\"}".getBytes()
        );
        assertThatThrownBy(() ->
            adapter.findActiveSessions(userId, missingFieldJson, 20)
        )
            .isInstanceOf(BadRequestException.class)
            .hasMessage("Invalid cursor format");
    }

    @Test
    void shouldFindActiveSessions_withWhitespacePaddedCursor_trimsAndSucceeds()
        throws Exception {
        UUID userId = UUID.randomUUID();
        UUID cursorId = UUID.randomUUID();
        Instant cursorTime = Instant.parse("2026-09-04T09:00:00Z");

        ObjectMapper mapper = new ObjectMapper().registerModule(
            new JavaTimeModule()
        );
        String cursorJson = mapper.writeValueAsString(
            new CursorData(cursorTime, cursorId)
        );
        String cursor =
            "   " +
            Base64.getEncoder().encodeToString(cursorJson.getBytes()) +
            "   ";

        UUID id = UUID.randomUUID();
        RefreshTokenJpaEntity entity = createEntity(
            id,
            userId,
            "Safari",
            "3.3.3.3",
            Instant.parse("2026-09-04T08:00:00Z")
        );

        when(
            repository.findNextPage(
                eq(userId),
                any(Instant.class),
                eq(cursorTime),
                eq(cursorId),
                eq(PageRequest.of(0, 3))
            )
        ).thenReturn(List.of(entity));

        CursorPage<ActiveSession> page = adapter.findActiveSessions(
            userId,
            cursor,
            2
        );

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).id()).isEqualTo(id);
    }

    @Test
    void shouldFindActiveSessionByIdAndUserId() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        RefreshTokenJpaEntity entity = createEntity(
            sessionId,
            userId,
            "Chrome",
            "127.0.0.1",
            now
        );

        when(
            repository.findActiveSessionByIdAndUserId(
                eq(sessionId),
                eq(userId),
                any(Instant.class)
            )
        ).thenReturn(Optional.of(entity));

        Optional<RefreshToken> result = adapter.findActiveSessionByIdAndUserId(
            sessionId,
            userId,
            now
        );

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(sessionId);
        assertThat(result.get().userId()).isEqualTo(userId);
    }

    @Test
    void shouldRevokeActiveSession() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant revokedAt = Instant.now();

        when(
            repository.revokeActiveSession(
                eq(sessionId),
                eq(userId),
                eq(revokedAt),
                eq(revokedAt)
            )
        ).thenReturn(1);

        boolean success = adapter.revokeActiveSession(
            sessionId,
            userId,
            revokedAt
        );

        assertThat(success).isTrue();
    }

    @Test
    void shouldFindActiveRemoteSessions() {
        UUID userId = UUID.randomUUID();
        UUID currentSessionId = UUID.randomUUID();
        UUID remoteSessionId = UUID.randomUUID();
        Instant now = Instant.now();

        RefreshTokenJpaEntity remoteEntity = createEntity(
            remoteSessionId,
            userId,
            "Firefox",
            "192.168.1.1",
            now
        );

        when(
            repository.findActiveRemoteSessions(
                eq(userId),
                eq(currentSessionId),
                any(Instant.class)
            )
        ).thenReturn(List.of(remoteEntity));

        List<RefreshToken> results = adapter.findActiveRemoteSessions(
            userId,
            currentSessionId,
            now
        );

        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo(remoteSessionId);
        assertThat(results.get(0).userId()).isEqualTo(userId);
    }

    @Test
    void shouldRevokeAllOtherActiveSessions() {
        UUID userId = UUID.randomUUID();
        UUID currentSessionId = UUID.randomUUID();
        Instant revokedAt = Instant.now();

        when(
            repository.revokeAllOtherActiveSessions(
                eq(userId),
                eq(currentSessionId),
                eq(revokedAt),
                eq(revokedAt)
            )
        ).thenReturn(3);

        int count = adapter.revokeAllOtherActiveSessions(
            userId,
            currentSessionId,
            revokedAt
        );

        assertThat(count).isEqualTo(3);
    }

    private RefreshTokenJpaEntity createEntity(
        UUID id,
        UUID userId,
        String deviceInfo,
        String ipAddress,
        Instant createdAt
    ) {
        RefreshTokenJpaEntity entity = new RefreshTokenJpaEntity();
        entity.setId(id);
        entity.setUserId(userId);
        entity.setTokenHash("hash-" + id);
        entity.setDeviceInfo(deviceInfo);
        entity.setIpAddress(ipAddress);
        entity.setCreatedAt(createdAt);
        entity.setExpiresAt(Instant.now().plusSeconds(3600));
        entity.setRevokedAt(null);
        return entity;
    }

    private record CursorData(Instant createdAt, UUID id) {}
}
