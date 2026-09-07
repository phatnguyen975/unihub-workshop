package com.unihub.workshop.iam.presentation.controllers;

import com.unihub.workshop.iam.application.ports.in.*;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.exception.ServiceUnavailableException;
import com.unihub.workshop.shared.security.JwtAuthenticatedPrincipal;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerRevokeSessionTest {

    private MockMvc mockMvc;
    private RevokeSessionUseCase revokeSessionUseCase;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CURRENT_SESSION_ID = UUID.randomUUID();
    private static final String CURRENT_JTI = "jti-123";

    @BeforeEach
    void setUp() {
        revokeSessionUseCase = mock(RevokeSessionUseCase.class);
        AuthController controller = AuthControllerTestBuilder.builder()
                .withRevokeSessionUseCase(revokeSessionUseCase)
                .build();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(), new GlobalResponseBodyAdvice())
                .build();
    }

    private TestingAuthenticationToken createAuth() {
        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
                USER_ID.toString(),
                null,
                CURRENT_JTI,
                "STUDENT",
                CURRENT_SESSION_ID.toString(),
                Instant.now(),
                Instant.now().plusSeconds(3600));
        return new TestingAuthenticationToken(principal, null, "ROLE_STUDENT");
    }

    @Test
    void shouldReturn204_whenRevokingRemoteSession() throws Exception {
        UUID remoteSessionId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/auth/sessions/" + remoteSessionId)
                .principal(createAuth()))
                .andExpect(status().isNoContent());

        verify(revokeSessionUseCase).revokeSession(
                eq(USER_ID),
                eq(remoteSessionId),
                eq(CURRENT_SESSION_ID),
                eq(CURRENT_JTI),
                any(Instant.class));
    }

    @Test
    void shouldReturn204_whenRevokingCurrentSession() throws Exception {
        mockMvc.perform(delete("/api/v1/auth/sessions/" + CURRENT_SESSION_ID)
                .principal(createAuth()))
                .andExpect(status().isNoContent());

        verify(revokeSessionUseCase).revokeSession(
                eq(USER_ID),
                eq(CURRENT_SESSION_ID),
                eq(CURRENT_SESSION_ID),
                eq(CURRENT_JTI),
                any(Instant.class));
    }

    @Test
    void shouldReturn400_whenSessionIdIsNotAValidUuid() throws Exception {
        mockMvc.perform(delete("/api/v1/auth/sessions/not-a-valid-uuid")
                .principal(createAuth()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.type").value("urn:unihub:error:bad-request"));

        verifyNoInteractions(revokeSessionUseCase);
    }

    @Test
    void shouldReturn401_whenUnauthenticated() throws Exception {
        mockMvc.perform(delete("/api/v1/auth/sessions/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.type").value("urn:unihub:error:unauthorized"));

        verifyNoInteractions(revokeSessionUseCase);
    }

    @Test
    void shouldReturn404_whenSessionNotFoundOrAlreadyRevoked() throws Exception {
        UUID nonExistentId = UUID.randomUUID();

        doThrow(new NotFoundException("Session not found or already revoked"))
                .when(revokeSessionUseCase).revokeSession(any(), any(), any(), any(), any());

        mockMvc.perform(delete("/api/v1/auth/sessions/" + nonExistentId)
                .principal(createAuth()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.type").value("urn:unihub:error:not-found"));
    }

    @Test
    void shouldReturn503WithRetryAfter_whenRedisIsUnavailable() throws Exception {
        UUID sessionId = UUID.randomUUID();

        doThrow(new ServiceUnavailableException("urn:unihub:error:service-unavailable", "Redis is unavailable"))
                .when(revokeSessionUseCase).revokeSession(any(), any(), any(), any(), any());

        mockMvc.perform(delete("/api/v1/auth/sessions/" + sessionId)
                .principal(createAuth()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "30"))
                .andExpect(jsonPath("$.error.type").value("urn:unihub:error:service-unavailable"));
    }
}
