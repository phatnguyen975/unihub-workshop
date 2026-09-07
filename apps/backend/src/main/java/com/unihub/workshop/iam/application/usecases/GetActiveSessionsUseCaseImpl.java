package com.unihub.workshop.iam.application.usecases;

import com.unihub.workshop.iam.application.ports.in.GetActiveSessionsUseCase;
import com.unihub.workshop.iam.application.ports.out.RefreshTokenRepository;
import com.unihub.workshop.iam.domain.model.ActiveSession;
import com.unihub.workshop.iam.domain.model.CursorPage;
import com.unihub.workshop.shared.exception.BadRequestException;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class GetActiveSessionsUseCaseImpl implements GetActiveSessionsUseCase {

    private final RefreshTokenRepository refreshTokenRepository;
    private final MeterRegistry meterRegistry;

    public GetActiveSessionsUseCaseImpl(
            RefreshTokenRepository refreshTokenRepository,
            MeterRegistry meterRegistry) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.meterRegistry = meterRegistry;
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<ActiveSession> getActiveSessions(UUID userId, UUID currentSessionId, String cursor,
            Integer size) {
        if (userId == null) {
            throw new BadRequestException("userId must not be null");
        }

        int limit = (size == null) ? 20 : size;
        if (limit < 1 || limit > 100) {
            throw new BadRequestException("size must be between 1 and 100");
        }

        CursorPage<ActiveSession> page = refreshTokenRepository.findActiveSessions(userId, cursor, limit);

        List<ActiveSession> items = page.items().stream()
                .map(session -> session
                        .withCurrentSession(currentSessionId != null && session.id().equals(currentSessionId)))
                .collect(Collectors.toList());

        meterRegistry.counter("auth.sessions.listed", "outcome", "success").increment();

        return new CursorPage<>(items, page.nextCursor(), page.hasMore());
    }
}
