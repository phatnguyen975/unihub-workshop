package com.unihub.workshop.notifications.presentation.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unihub.workshop.notifications.application.ports.in.GetNotificationsQuery;
import com.unihub.workshop.notifications.application.ports.in.GetNotificationsUseCase;
import com.unihub.workshop.notifications.application.ports.in.NotificationsResult;
import com.unihub.workshop.notifications.domain.model.InAppNotification;
import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.shared.security.JwtAuthenticatedPrincipal;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class NotificationControllerListTest {

    private MockMvc mockMvc;
    private GetNotificationsUseCase getNotificationsUseCase;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String ROLE = "STUDENT";

    @BeforeEach
    void setUp() {
        getNotificationsUseCase = mock(GetNotificationsUseCase.class);

        NotificationController controller =
            NotificationControllerTestBuilder.builder()
                .withGetNotificationsUseCase(getNotificationsUseCase)
                .build();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(
                new GlobalExceptionHandler(),
                new GlobalResponseBodyAdvice()
            )
            .build();
    }

    private Authentication createAuthentication(UUID userId, String role) {
        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
            userId.toString(),
            null,
            "jti-test-123",
            role,
            "sid-test-456",
            Instant.now(),
            Instant.now().plusSeconds(3600)
        );
        TestingAuthenticationToken token = new TestingAuthenticationToken(
            principal,
            null,
            "ROLE_" + role
        );
        token.setAuthenticated(true);
        return token;
    }

    @Test
    @DisplayName(
        "GET /api/v1/notifications should return 200 with items, unreadCount, cursor, and metadata"
    )
    void shouldReturnNotificationsWhenAuthenticated() throws Exception {
        Authentication auth = createAuthentication(USER_ID, ROLE);
        UUID notifId1 = UUID.randomUUID();
        UUID notifId2 = UUID.randomUUID();

        InAppNotification n1 = InAppNotification.builder()
            .id(notifId1)
            .userId(USER_ID)
            .eventKey("WORKSHOP_RESCHEDULED")
            .payload(
                "{\"workshopId\":\"123\",\"idempotencyKey\":\"secret-key-1\"}"
            )
            .isRead(false)
            .readAt(null)
            .actionUrl("https://unihub.edu.vn/workshops/123")
            .createdAt(Instant.parse("2026-10-08T10:00:00Z"))
            .build();

        InAppNotification n2 = InAppNotification.builder()
            .id(notifId2)
            .userId(USER_ID)
            .eventKey("USER_STATUS_CHANGED")
            .payload("{\"reason\":\"verified\"}")
            .isRead(true)
            .readAt(Instant.parse("2026-10-08T09:15:00Z"))
            .actionUrl(null)
            .createdAt(Instant.parse("2026-10-08T08:00:00Z"))
            .build();

        CursorPage<InAppNotification> page = new CursorPage<>(
            List.of(n1, n2),
            "next-cursor-token",
            true
        );
        NotificationsResult result = new NotificationsResult(page, 1L);

        when(
            getNotificationsUseCase.getNotifications(
                any(GetNotificationsQuery.class)
            )
        ).thenReturn(result);

        mockMvc
            .perform(
                get("/api/v1/notifications")
                    .principal(auth)
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(
                jsonPath("$.message").value(
                    "Notifications retrieved successfully"
                )
            )
            .andExpect(jsonPath("$.data.items").isArray())
            .andExpect(jsonPath("$.data.items.length()").value(2))
            // Item 1 checks:
            .andExpect(
                jsonPath("$.data.items[0].id").value(notifId1.toString())
            )
            .andExpect(
                jsonPath("$.data.items[0].eventKey").value(
                    "WORKSHOP_RESCHEDULED"
                )
            )
            .andExpect(
                jsonPath("$.data.items[0].payload.workshopId").value("123")
            )
            .andExpect(
                jsonPath(
                    "$.data.items[0].payload.idempotencyKey"
                ).doesNotExist()
            ) // stripped!
            .andExpect(jsonPath("$.data.items[0].isRead").value(false))
            .andExpect(jsonPath("$.data.items[0].readAt").doesNotExist()) // omitted when null
            .andExpect(
                jsonPath("$.data.items[0].actionUrl").value(
                    "https://unihub.edu.vn/workshops/123"
                )
            )
            .andExpect(
                jsonPath("$.data.items[0].createdAt").value(
                    "2026-10-08T10:00:00Z"
                )
            )
            // Item 2 checks:
            .andExpect(
                jsonPath("$.data.items[1].id").value(notifId2.toString())
            )
            .andExpect(
                jsonPath("$.data.items[1].eventKey").value(
                    "USER_STATUS_CHANGED"
                )
            )
            .andExpect(
                jsonPath("$.data.items[1].payload.reason").value("verified")
            )
            .andExpect(jsonPath("$.data.items[1].isRead").value(true))
            .andExpect(
                jsonPath("$.data.items[1].readAt").value("2026-10-08T09:15:00Z")
            )
            .andExpect(jsonPath("$.data.items[1].actionUrl").doesNotExist()) // omitted when null
            .andExpect(
                jsonPath("$.data.items[1].createdAt").value(
                    "2026-10-08T08:00:00Z"
                )
            )
            // Page data & cursor:
            .andExpect(jsonPath("$.data.unreadCount").value(1))
            .andExpect(
                jsonPath("$.data.cursor.nextCursor").value("next-cursor-token")
            )
            .andExpect(jsonPath("$.data.cursor.hasMore").value(true))
            .andExpect(jsonPath("$.metadata.processedBy").isNotEmpty())
            .andExpect(jsonPath("$.metadata.traceId").isNotEmpty());
    }

    @Test
    @DisplayName(
        "GET /api/v1/notifications should omit nextCursor when hasMore is false"
    )
    void shouldOmitNextCursorWhenHasMoreIsFalse() throws Exception {
        Authentication auth = createAuthentication(USER_ID, ROLE);

        CursorPage<InAppNotification> page = new CursorPage<>(
            List.of(),
            null,
            false
        );
        NotificationsResult result = new NotificationsResult(page, 0L);

        when(
            getNotificationsUseCase.getNotifications(
                any(GetNotificationsQuery.class)
            )
        ).thenReturn(result);

        mockMvc
            .perform(
                get("/api/v1/notifications")
                    .principal(auth)
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.cursor.hasMore").value(false))
            .andExpect(jsonPath("$.data.cursor.nextCursor").doesNotExist());
    }

    @Test
    @DisplayName(
        "GET /api/v1/notifications with isRead filter should pass isRead to usecase"
    )
    void shouldPassIsReadFilterToUseCase() throws Exception {
        Authentication auth = createAuthentication(USER_ID, ROLE);

        CursorPage<InAppNotification> page = new CursorPage<>(
            List.of(),
            null,
            false
        );
        when(
            getNotificationsUseCase.getNotifications(
                any(GetNotificationsQuery.class)
            )
        ).thenReturn(new NotificationsResult(page, 0L));

        mockMvc
            .perform(
                get("/api/v1/notifications")
                    .param("isRead", "false")
                    .principal(auth)
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isOk());

        verify(getNotificationsUseCase).getNotifications(
            GetNotificationsQuery.of(USER_ID, false, null, 20)
        );
    }

    @Test
    @DisplayName(
        "GET /api/v1/notifications should return 401 when unauthenticated"
    )
    void shouldReturn401WhenUnauthenticated() throws Exception {
        mockMvc
            .perform(
                get("/api/v1/notifications").accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:unauthorized")
            );
    }

    @Test
    @DisplayName(
        "GET /api/v1/notifications with size < 1 should return 422 Unprocessable Content"
    )
    void shouldReturn422WhenSizeLessThanOne() throws Exception {
        Authentication auth = createAuthentication(USER_ID, ROLE);

        mockMvc
            .perform(
                get("/api/v1/notifications")
                    .param("size", "0")
                    .principal(auth)
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().is(422))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            )
            .andExpect(jsonPath("$.error.validationErrors").isArray());
    }

    @Test
    @DisplayName(
        "GET /api/v1/notifications with size > 100 should return 422 Unprocessable Content"
    )
    void shouldReturn422WhenSizeGreaterThan100() throws Exception {
        Authentication auth = createAuthentication(USER_ID, ROLE);

        mockMvc
            .perform(
                get("/api/v1/notifications")
                    .param("size", "101")
                    .principal(auth)
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().is(422))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            )
            .andExpect(jsonPath("$.error.validationErrors").isArray());
    }

    @Test
    @DisplayName(
        "GET /api/v1/notifications with invalid cursor should return 400 Bad Request"
    )
    void shouldReturn400WhenCursorIsMalformed() throws Exception {
        Authentication auth = createAuthentication(USER_ID, ROLE);

        when(
            getNotificationsUseCase.getNotifications(
                any(GetNotificationsQuery.class)
            )
        ).thenThrow(new BadRequestException("Invalid cursor format"));

        mockMvc
            .perform(
                get("/api/v1/notifications")
                    .param("cursor", "malformed-cursor")
                    .principal(auth)
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:bad-request")
            );
    }
}
