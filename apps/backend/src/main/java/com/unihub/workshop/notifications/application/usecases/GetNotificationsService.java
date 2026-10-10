package com.unihub.workshop.notifications.application.usecases;

import com.unihub.workshop.notifications.application.ports.in.GetNotificationsQuery;
import com.unihub.workshop.notifications.application.ports.in.GetNotificationsUseCase;
import com.unihub.workshop.notifications.application.ports.in.NotificationsResult;
import com.unihub.workshop.notifications.application.ports.out.NotificationRepository;
import com.unihub.workshop.notifications.domain.model.InAppNotification;
import com.unihub.workshop.shared.pagination.CursorPage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetNotificationsService implements GetNotificationsUseCase {

    private final NotificationRepository notificationRepository;

    @Override
    @Transactional(readOnly = true)
    public NotificationsResult getNotifications(GetNotificationsQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("query must not be null");
        }

        CursorPage<InAppNotification> page =
            notificationRepository.findNotificationsByUserId(
                query.userId(),
                query.isRead(),
                query.cursor(),
                query.size()
            );

        long unreadCount = notificationRepository.countUnreadByUserId(
            query.userId()
        );

        return new NotificationsResult(page, unreadCount);
    }
}
