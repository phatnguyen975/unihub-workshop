package com.unihub.workshop.iam.application.usecases;

import com.unihub.workshop.iam.application.ports.in.GetActiveSessionsUseCase;
import com.unihub.workshop.iam.application.ports.out.RefreshTokenRepository;
import com.unihub.workshop.iam.domain.model.ActiveSession;
import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.pagination.CursorPage;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetActiveSessionsUseCaseImpl implements GetActiveSessionsUseCase {

    private final RefreshTokenRepository refreshTokenRepository;

    @Override
    @Transactional
    public CursorPage<ActiveSession> getActiveSessions(
        UUID userId,
        UUID currentSessionId,
        String cursor,
        Integer size
    ) {
        if (userId == null) {
            throw new BadRequestException("userId must not be null");
        }

        int pageSize = size == null ? 20 : size;
        if (pageSize < 1 || pageSize > 100) {
            throw new BadRequestException("size must be between 1 and 100");
        }

        CursorPage<ActiveSession> page =
            refreshTokenRepository.findActiveSessions(userId, cursor, pageSize);

        List<ActiveSession> items = page
            .items()
            .stream()
            .map(session ->
                session
                    .toBuilder()
                    .isCurrentSession(
                        currentSessionId != null &&
                            session.id().equals(currentSessionId)
                    )
                    .build()
            )
            .toList();

        return new CursorPage<>(items, page.nextCursor(), page.hasMore());
    }
}
