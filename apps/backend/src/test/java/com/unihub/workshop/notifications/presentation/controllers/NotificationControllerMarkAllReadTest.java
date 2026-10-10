package com.unihub.workshop.notifications.presentation.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unihub.workshop.notifications.application.ports.in.MarkAllNotificationsReadUseCase;
import com.unihub.workshop.notifications.application.ports.in.MarkAllReadResult;
import com.unihub.workshop.notifications.application.ports.in.MarkNotificationReadUseCase;
import com.unihub.workshop.notifications.presentation.mapper.NotificationPresentationMapperImpl;
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
class NotificationControllerMarkAllReadTest {

    private MockMvc mockMvc;

    @Mock
    private MarkAllNotificationsReadUseCase markAllNotificationsReadUseCase;

    @Mock
    private MarkNotificationReadUseCase markNotificationReadUseCase;

    private final UUID authenticatedUserId = UUID.fromString(
        "11111111-1111-1111-1111-111111111111"
    );

    @BeforeEach
    void setUp() {
        NotificationController controller =
            NotificationControllerTestBuilder.builder()
                .withMarkAllNotificationsReadUseCase(
                    markAllNotificationsReadUseCase
                )
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
        "PATCH /api/v1/notifications/read-all should return 200 OK with updatedCount and unreadCount = 0"
    )
    void markAllAsRead_whenUnreadExist_returns200() throws Exception {
        when(
            markAllNotificationsReadUseCase.markAllAsRead(
                eq(authenticatedUserId)
            )
        ).thenReturn(new MarkAllReadResult(5, 0L));

        mockMvc
            .perform(
                patch("/api/v1/notifications/read-all")
                    .principal(createAuthentication())
                    .contentType(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(
                jsonPath("$.message").value("All notifications marked as read")
            )
            .andExpect(jsonPath("$.data.updatedCount").value(5))
            .andExpect(jsonPath("$.data.unreadCount").value(0))
            .andExpect(jsonPath("$.metadata.processedBy").isNotEmpty())
            .andExpect(jsonPath("$.metadata.traceId").isNotEmpty());

        verify(markAllNotificationsReadUseCase).markAllAsRead(
            authenticatedUserId
        );
        verify(markNotificationReadUseCase, never()).markAsRead(any(), any());
    }

    @Test
    @DisplayName(
        "PATCH /api/v1/notifications/read-all should return 200 OK with updatedCount = 0 and unreadCount = 0 when no unread notifications exist"
    )
    void markAllAsRead_whenNoUnread_returns200() throws Exception {
        when(
            markAllNotificationsReadUseCase.markAllAsRead(
                eq(authenticatedUserId)
            )
        ).thenReturn(new MarkAllReadResult(0, 0L));

        mockMvc
            .perform(
                patch("/api/v1/notifications/read-all")
                    .principal(createAuthentication())
                    .contentType(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(
                jsonPath("$.message").value("All notifications marked as read")
            )
            .andExpect(jsonPath("$.data.updatedCount").value(0))
            .andExpect(jsonPath("$.data.unreadCount").value(0));

        verify(markAllNotificationsReadUseCase).markAllAsRead(
            authenticatedUserId
        );
        verify(markNotificationReadUseCase, never()).markAsRead(any(), any());
    }

    @Test
    @DisplayName(
        "PATCH /api/v1/notifications/read-all route precedence guarantees it routes to bulk endpoint, not single endpoint"
    )
    void markAllAsRead_routePrecedence_doesNotTriggerSingleEndpoint()
        throws Exception {
        when(
            markAllNotificationsReadUseCase.markAllAsRead(
                eq(authenticatedUserId)
            )
        ).thenReturn(new MarkAllReadResult(1, 0L));

        mockMvc
            .perform(
                patch("/api/v1/notifications/read-all")
                    .principal(createAuthentication())
                    .contentType(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.updatedCount").value(1));

        verify(markAllNotificationsReadUseCase).markAllAsRead(
            authenticatedUserId
        );
        verify(markNotificationReadUseCase, never()).markAsRead(any(), any());
    }

    @Test
    @DisplayName(
        "PATCH /api/v1/notifications/read-all should return 401 Unauthorized when unauthenticated"
    )
    void markAllAsRead_whenUnauthenticated_returns401() throws Exception {
        mockMvc
            .perform(
                patch("/api/v1/notifications/read-all").contentType(
                    MediaType.APPLICATION_JSON
                )
            )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:unauthorized")
            );
    }
}
