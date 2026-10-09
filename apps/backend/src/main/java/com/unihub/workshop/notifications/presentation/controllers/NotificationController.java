package com.unihub.workshop.notifications.presentation.controllers;

import com.unihub.workshop.notifications.application.ports.in.GetUnreadCountUseCase;
import com.unihub.workshop.notifications.presentation.responses.UnreadCountResponse;
import com.unihub.workshop.shared.security.SecurityContextUtils;
import com.unihub.workshop.shared.web.ApiResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final GetUnreadCountUseCase getUnreadCountUseCase;

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
}
