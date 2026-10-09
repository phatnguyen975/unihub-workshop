package com.unihub.workshop.notifications.presentation.controllers;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unihub.workshop.notifications.application.ports.in.MarkNotificationReadResult;
import com.unihub.workshop.notifications.application.ports.in.MarkNotificationReadUseCase;
import com.unihub.workshop.notifications.domain.model.InAppNotification;
import com.unihub.workshop.notifications.presentation.mapper.NotificationPresentationMapperImpl;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.security.JwtAuthenticatedPrincipal;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class NotificationControllerMarkReadTest {

    private MockMvc mockMvc;

    @Mock
    private MarkNotificationReadUseCase markNotificationReadUseCase;

    private final UUID authenticatedUserId = UUID.fromString(
        "11111111-1111-1111-1111-111111111111"
    );

    @BeforeEach
    void setUp() {
        NotificationController controller =
            NotificationControllerTestBuilder.builder()
                .withMarkNotificationReadUseCase(markNotificationReadUseCase)
                .withNotificationPresentationMapper(
                    new NotificationPresentationMapperImpl()
                )
                .build();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(
                GlobalExceptionHandler.forTesting(),
                new GlobalResponseBodyAdvice()
            )
            .build();
    }

    private Authentication createAuthentication() {
        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
            authenticatedUserId.toString(),
            null,
            "jti-test-123",
            "STUDENT",
            "sid-test-456",
            Instant.now(),
            Instant.now().plusSeconds(3600)
        );
        TestingAuthenticationToken token = new TestingAuthenticationToken(
            principal,
            null,
            "ROLE_STUDENT"
        );
        token.setAuthenticated(true);
        return token;
    }

    @Test
    @DisplayName(
        "PATCH /api/v1/notifications/{id}/read should return 200 OK with updated notification and unread count"
    )
    void markAsRead_whenSuccess_returns200() throws Exception {
        UUID notificationId = UUID.fromString(
            "e4b1c2d3-1111-4aaa-8bbb-000000000001"
        );
        Instant createdAt = Instant.parse("2026-10-08T10:00:00Z");
        Instant readAt = Instant.parse("2026-10-08T15:30:00Z");

        InAppNotification notification = InAppNotification.builder()
            .id(notificationId)
            .userId(authenticatedUserId)
            .eventKey("WORKSHOP_RESCHEDULED")
            .payload("{\"workshopTitle\":\"Career Sprint 101\"}")
            .isRead(true)
            .readAt(readAt)
            .actionUrl("https://unihub.edu.vn/workshops/1")
            .createdAt(createdAt)
            .updatedAt(readAt)
            .build();

        when(
            markNotificationReadUseCase.markAsRead(
                eq(notificationId),
                eq(authenticatedUserId)
            )
        ).thenReturn(new MarkNotificationReadResult(notification, 6L));

        mockMvc
            .perform(
                patch("/api/v1/notifications/{id}/read", notificationId)
                    .principal(createAuthentication())
                    .contentType(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(
                jsonPath("$.message").value("Notification marked as read")
            )
            .andExpect(
                jsonPath("$.data.notification.id").value(
                    notificationId.toString()
                )
            )
            .andExpect(
                jsonPath("$.data.notification.eventKey").value(
                    "WORKSHOP_RESCHEDULED"
                )
            )
            .andExpect(
                jsonPath("$.data.notification.payload.workshopTitle").value(
                    "Career Sprint 101"
                )
            )
            .andExpect(jsonPath("$.data.notification.isRead").value(true))
            .andExpect(
                jsonPath("$.data.notification.readAt").value(
                    "2026-10-08T15:30:00Z"
                )
            )
            .andExpect(
                jsonPath("$.data.notification.actionUrl").value(
                    "https://unihub.edu.vn/workshops/1"
                )
            )
            .andExpect(
                jsonPath("$.data.notification.createdAt").value(
                    "2026-10-08T10:00:00Z"
                )
            )
            .andExpect(jsonPath("$.data.unreadCount").value(6));

        verify(markNotificationReadUseCase).markAsRead(
            notificationId,
            authenticatedUserId
        );
    }

    @Test
    @DisplayName(
        "PATCH /api/v1/notifications/{id}/read should return 200 OK idempotently when already read"
    )
    void markAsRead_whenAlreadyRead_returns200WithCurrentCount()
        throws Exception {
        UUID notificationId = UUID.fromString(
            "e4b1c2d3-1111-4aaa-8bbb-000000000001"
        );
        Instant createdAt = Instant.parse("2026-10-08T10:00:00Z");
        Instant readAt = Instant.parse("2026-10-08T11:00:00Z");

        InAppNotification notification = InAppNotification.builder()
            .id(notificationId)
            .userId(authenticatedUserId)
            .eventKey("WORKSHOP_RESCHEDULED")
            .payload("{}")
            .isRead(true)
            .readAt(readAt)
            .createdAt(createdAt)
            .updatedAt(readAt)
            .build();

        when(
            markNotificationReadUseCase.markAsRead(
                eq(notificationId),
                eq(authenticatedUserId)
            )
        ).thenReturn(new MarkNotificationReadResult(notification, 6L));

        mockMvc
            .perform(
                patch("/api/v1/notifications/{id}/read", notificationId)
                    .principal(createAuthentication())
                    .contentType(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.notification.isRead").value(true))
            .andExpect(
                jsonPath("$.data.notification.readAt").value(
                    "2026-10-08T11:00:00Z"
                )
            )
            .andExpect(jsonPath("$.data.unreadCount").value(6));
    }

    @Test
    @DisplayName(
        "PATCH /api/v1/notifications/{id}/read should return 403 Forbidden when owned by another user"
    )
    void markAsRead_whenOwnedByAnotherUser_returns403() throws Exception {
        UUID notificationId = UUID.randomUUID();

        when(
            markNotificationReadUseCase.markAsRead(
                eq(notificationId),
                eq(authenticatedUserId)
            )
        ).thenThrow(
            new ForbiddenException(
                "You do not have permission to access this notification."
            )
        );

        mockMvc
            .perform(
                patch("/api/v1/notifications/{id}/read", notificationId)
                    .principal(createAuthentication())
                    .contentType(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:forbidden")
            )
            .andExpect(jsonPath("$.error.status").value(403))
            .andExpect(
                jsonPath("$.error.detail").value(
                    "You do not have permission to access this notification."
                )
            );
    }

    @Test
    @DisplayName(
        "PATCH /api/v1/notifications/{id}/read should return 404 Not Found when notification does not exist"
    )
    void markAsRead_whenNotFound_returns404() throws Exception {
        UUID notificationId = UUID.randomUUID();

        when(
            markNotificationReadUseCase.markAsRead(
                eq(notificationId),
                eq(authenticatedUserId)
            )
        ).thenThrow(new NotFoundException("Notification not found."));

        mockMvc
            .perform(
                patch("/api/v1/notifications/{id}/read", notificationId)
                    .principal(createAuthentication())
                    .contentType(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:not-found")
            )
            .andExpect(jsonPath("$.error.status").value(404))
            .andExpect(
                jsonPath("$.error.detail").value("Notification not found.")
            );
    }

    @Test
    @DisplayName(
        "PATCH /api/v1/notifications/{id}/read should return 400 Bad Request when id is invalid UUID"
    )
    void markAsRead_whenInvalidUuid_returns400() throws Exception {
        mockMvc
            .perform(
                patch("/api/v1/notifications/{id}/read", "not-a-valid-uuid")
                    .principal(createAuthentication())
                    .contentType(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:bad-request")
            )
            .andExpect(jsonPath("$.error.status").value(400));
    }
}
