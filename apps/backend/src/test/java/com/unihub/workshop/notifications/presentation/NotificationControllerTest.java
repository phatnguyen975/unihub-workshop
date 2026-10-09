package com.unihub.workshop.notifications.presentation;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unihub.workshop.notifications.application.ports.in.GetUnreadCountUseCase;
import com.unihub.workshop.notifications.presentation.controllers.NotificationController;
import com.unihub.workshop.shared.security.JwtAuthenticatedPrincipal;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class NotificationControllerTest {

    private MockMvc mockMvc;
    private GetUnreadCountUseCase getUnreadCountUseCase;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String ROLE = "STUDENT";

    @BeforeEach
    void setUp() {
        getUnreadCountUseCase = mock(GetUnreadCountUseCase.class);
        NotificationController controller = new NotificationController(
            getUnreadCountUseCase
        );

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
        "GET /api/v1/notifications/unread-count should return 200 with unread count and metadata when authenticated"
    )
    void shouldReturnUnreadCountWhenAuthenticated() throws Exception {
        Authentication auth = createAuthentication(USER_ID, ROLE);
        when(getUnreadCountUseCase.getUnreadCount(USER_ID)).thenReturn(7L);

        mockMvc
            .perform(
                get("/api/v1/notifications/unread-count")
                    .principal(auth)
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(
                jsonPath("$.message").value(
                    "Unread count retrieved successfully"
                )
            )
            .andExpect(jsonPath("$.data.unreadCount").value(7))
            .andExpect(jsonPath("$.metadata.processedBy").isNotEmpty())
            .andExpect(jsonPath("$.metadata.traceId").isNotEmpty());

        verify(getUnreadCountUseCase).getUnreadCount(USER_ID);
    }

    @Test
    @DisplayName(
        "GET /api/v1/notifications/unread-count should return 401 when principal is missing"
    )
    void shouldReturn401WhenUnauthenticated() throws Exception {
        mockMvc
            .perform(
                get("/api/v1/notifications/unread-count").accept(
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
