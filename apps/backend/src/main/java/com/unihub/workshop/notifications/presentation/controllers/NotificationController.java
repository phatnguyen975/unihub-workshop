package com.unihub.workshop.notifications.presentation.controllers;

import com.unihub.workshop.notifications.application.ports.in.GetNotificationsQuery;
import com.unihub.workshop.notifications.application.ports.in.GetNotificationsUseCase;
import com.unihub.workshop.notifications.application.ports.in.GetUnreadCountUseCase;
import com.unihub.workshop.notifications.application.ports.in.NotificationsResult;
import com.unihub.workshop.notifications.presentation.dto.NotificationPageData;
import com.unihub.workshop.notifications.presentation.dto.UnreadCountResponse;
import com.unihub.workshop.notifications.presentation.mapper.NotificationPresentationMapper;
import com.unihub.workshop.shared.security.SecurityContextUtils;
import com.unihub.workshop.shared.web.ApiResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final GetUnreadCountUseCase getUnreadCountUseCase;
    private final GetNotificationsUseCase getNotificationsUseCase;
    private final NotificationPresentationMapper notificationPresentationMapper;

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> getUnreadCount(
        Authentication authentication
    ) {
        UUID userId = SecurityContextUtils.extractUserId(authentication);
        long count = getUnreadCountUseCase.getUnreadCount(userId);
        return ResponseEntity.ok(
            ApiResponse.success(
                UnreadCountResponse.of(count),
                "Unread count retrieved successfully"
            )
        );
    }

    @GetMapping
    public ResponseEntity<ApiResponse<NotificationPageData>> getNotifications(
        @RequestParam(required = false) String cursor,
        @RequestParam(required = false, defaultValue = "20") Integer size,
        @RequestParam(required = false) Boolean isRead,
        Authentication authentication
    ) {
        UUID userId = SecurityContextUtils.extractUserId(authentication);
        GetNotificationsQuery query = GetNotificationsQuery.of(
            userId,
            isRead,
            cursor,
            size
        );
        NotificationsResult result = getNotificationsUseCase.getNotifications(
            query
        );

        NotificationPageData data = notificationPresentationMapper.toPageData(
            result
        );

        return ResponseEntity.ok(
            ApiResponse.success(data, "Notifications retrieved successfully")
        );
    }
}
