package com.unihub.workshop.notifications.application.usecases;

import com.unihub.workshop.notifications.application.ports.in.GetUnreadCountUseCase;
import com.unihub.workshop.notifications.application.ports.out.NotificationRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetUnreadCountService implements GetUnreadCountUseCase {

    private final NotificationRepository notificationRepository;

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCount(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        return notificationRepository.countUnreadByUserId(userId);
    }
}
