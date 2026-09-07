package com.unihub.workshop.iam.application.usecases;

import com.unihub.workshop.iam.application.ports.out.RefreshTokenRepository;
import com.unihub.workshop.iam.domain.model.ActiveSession;
import com.unihub.workshop.iam.domain.model.CursorPage;
import com.unihub.workshop.shared.exception.BadRequestException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetActiveSessionsUseCaseImplTest {

    private RefreshTokenRepository refreshTokenRepository;
    private MeterRegistry meterRegistry;
    private Counter counter;
    private GetActiveSessionsUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        meterRegistry = mock(MeterRegistry.class);
        counter = mock(Counter.class);

        when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(counter);

        useCase = new GetActiveSessionsUseCaseImpl(refreshTokenRepository, meterRegistry);
    }

    @Test
    void shouldThrowBadRequestException_whenUserIdIsNull() {
        assertThatThrownBy(() -> useCase.getActiveSessions(null, UUID.randomUUID(), null, 20))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("userId must not be null");
    }

    @Test
    void shouldThrowBadRequestException_whenSizeIsLessThanOne() {
        UUID userId = UUID.randomUUID();
        assertThatThrownBy(() -> useCase.getActiveSessions(userId, UUID.randomUUID(), null, 0))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("size must be between 1 and 100");
    }

    @Test
    void shouldThrowBadRequestException_whenSizeIsGreaterThan100() {
        UUID userId = UUID.randomUUID();
        assertThatThrownBy(() -> useCase.getActiveSessions(userId, UUID.randomUUID(), null, 101))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("size must be between 1 and 100");
    }

    @Test
    void shouldDefaultSizeTo20_whenSizeIsNull() {
        UUID userId = UUID.randomUUID();
        UUID currentSessionId = UUID.randomUUID();

        when(refreshTokenRepository.findActiveSessions(eq(userId), eq(null), eq(20)))
                .thenReturn(new CursorPage<>(List.of(), null, false));

        CursorPage<ActiveSession> result = useCase.getActiveSessions(userId, currentSessionId, null, null);

        assertThat(result).isNotNull();
        assertThat(result.items()).isEmpty();
    }

    @Test
    void shouldMarkCurrentSession_whenSessionIdMatchesCurrentSessionId() {
        UUID userId = UUID.randomUUID();
        UUID currentSessionId = UUID.randomUUID();
        UUID otherSessionId = UUID.randomUUID();

        ActiveSession s1 = new ActiveSession(currentSessionId, "Chrome", "1.1.1.1", Instant.now(), false);
        ActiveSession s2 = new ActiveSession(otherSessionId, "Firefox", "2.2.2.2", Instant.now().minusSeconds(60),
                false);

        when(refreshTokenRepository.findActiveSessions(eq(userId), eq("cursor-abc"), eq(10)))
                .thenReturn(new CursorPage<>(List.of(s1, s2), "next-cursor", true));

        CursorPage<ActiveSession> result = useCase.getActiveSessions(userId, currentSessionId, "cursor-abc", 10);

        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(0).id()).isEqualTo(currentSessionId);
        assertThat(result.items().get(0).isCurrentSession()).isTrue();
        assertThat(result.items().get(1).id()).isEqualTo(otherSessionId);
        assertThat(result.items().get(1).isCurrentSession()).isFalse();
        assertThat(result.nextCursor()).isEqualTo("next-cursor");
        assertThat(result.hasMore()).isTrue();

        verify(meterRegistry).counter("auth.sessions.listed", "outcome", "success");
        verify(counter).increment();
    }

    @Test
    void shouldMarkAllAsFalse_whenCurrentSessionIdDoesNotMatchAnySession() {
        UUID userId = UUID.randomUUID();
        UUID currentSessionId = UUID.randomUUID();
        UUID otherSessionId1 = UUID.randomUUID();
        UUID otherSessionId2 = UUID.randomUUID();

        ActiveSession s1 = new ActiveSession(otherSessionId1, "Chrome", "1.1.1.1", Instant.now(), false);
        ActiveSession s2 = new ActiveSession(otherSessionId2, "Firefox", "2.2.2.2", Instant.now().minusSeconds(60),
                false);

        when(refreshTokenRepository.findActiveSessions(eq(userId), eq(null), eq(20)))
                .thenReturn(new CursorPage<>(List.of(s1, s2), null, false));

        CursorPage<ActiveSession> result = useCase.getActiveSessions(userId, currentSessionId, null, 20);

        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(0).isCurrentSession()).isFalse();
        assertThat(result.items().get(1).isCurrentSession()).isFalse();
    }

    @Test
    void shouldMarkAllAsFalse_whenCurrentSessionIdIsNull() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        ActiveSession s1 = new ActiveSession(sessionId, "Chrome", "1.1.1.1", Instant.now(), false);

        when(refreshTokenRepository.findActiveSessions(eq(userId), eq(null), eq(20)))
                .thenReturn(new CursorPage<>(List.of(s1), null, false));

        CursorPage<ActiveSession> result = useCase.getActiveSessions(userId, null, null, 20);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).isCurrentSession()).isFalse();
    }
}
