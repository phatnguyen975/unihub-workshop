package com.unihub.workshop.shared.realtime.presentation;

import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.realtime.application.StreamSubscriptionService;
import com.unihub.workshop.shared.security.JwtAuthenticatedPrincipal;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("StreamController Unit Tests")
class StreamControllerTest {

    private MockMvc mockMvc;
    private StreamSubscriptionService subscriptionService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String ROLE = "STUDENT";
    private static final UUID ORG_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        subscriptionService = mock(StreamSubscriptionService.class);
        StreamController controller = new StreamController(subscriptionService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(), new GlobalResponseBodyAdvice())
                .build();
    }

    private Authentication createAuthentication(UUID userId, String role, UUID orgId) {
        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
                userId.toString(),
                orgId != null ? orgId.toString() : null,
                "jti-test-123",
                role,
                "sid-test-456",
                Instant.now(),
                Instant.now().plusSeconds(3600));
        TestingAuthenticationToken token = new TestingAuthenticationToken(principal, null, "ROLE_" + role);
        token.setAuthenticated(true);
        return token;
    }

    @Test
    @DisplayName("should establish SSE stream with 200 OK and expected headers when authenticated")
    void shouldEstablishStream_whenAuthenticated() throws Exception {
        Authentication auth = createAuthentication(USER_ID, ROLE, ORG_ID);
        SseEmitter emitter = new SseEmitter(1_800_000L);
        when(subscriptionService.subscribe(eq(USER_ID), eq(ROLE), eq(ORG_ID.toString()))).thenReturn(emitter);

        mockMvc.perform(get("/api/v1/stream/events")
                .principal(auth)
                .accept(MediaType.TEXT_EVENT_STREAM_VALUE))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE,
                        Matchers.startsWith(MediaType.TEXT_EVENT_STREAM_VALUE)))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform"))
                .andExpect(header().string("X-Accel-Buffering", "no"));

        verify(subscriptionService).subscribe(eq(USER_ID), eq(ROLE), eq(ORG_ID.toString()));
    }

    @Test
    @DisplayName("should return 401 Unauthorized when unauthenticated")
    void shouldReturn401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/stream/events")
                .accept(MediaType.TEXT_EVENT_STREAM_VALUE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.type").value("urn:unihub:error:unauthorized"))
                .andExpect(jsonPath("$.error.status").value(401));

        verifyNoInteractions(subscriptionService);
    }

    @Test
    @DisplayName("should return 403 Forbidden when user account is inactive or banned")
    void shouldReturn403_whenUserIsInactive() throws Exception {
        Authentication auth = createAuthentication(USER_ID, ROLE, ORG_ID);
        when(subscriptionService.subscribe(any(), any(), any()))
                .thenThrow(new ForbiddenException("urn:unihub:error:account-inactive",
                        "User account is inactive or banned"));

        mockMvc.perform(get("/api/v1/stream/events")
                .principal(auth)
                .accept(MediaType.TEXT_EVENT_STREAM_VALUE))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.type").value("urn:unihub:error:account-inactive"))
                .andExpect(jsonPath("$.error.status").value(403))
                .andExpect(jsonPath("$.error.detail").value("User account is inactive or banned"));
    }
}
