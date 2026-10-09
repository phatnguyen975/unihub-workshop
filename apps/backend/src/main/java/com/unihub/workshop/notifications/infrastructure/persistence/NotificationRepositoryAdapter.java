package com.unihub.workshop.notifications.infrastructure.persistence;

import com.unihub.workshop.notifications.application.ports.out.NotificationRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class NotificationRepositoryAdapter implements NotificationRepository {

    private final SpringDataNotificationRepository springDataNotificationRepository;

    @Override
    public long countUnreadByUserId(UUID userId) {
        return springDataNotificationRepository.countByUserIdAndIsReadFalse(
            userId
        );
    }
}
