package com.unihub.workshop.iam.presentation.controllers;

import com.unihub.workshop.iam.application.ports.in.*;
import com.unihub.workshop.iam.domain.model.ActiveSession;
import com.unihub.workshop.iam.domain.model.CursorPage;
import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.security.JwtAuthenticatedPrincipal;
import com.unihub.workshop.shared.security.PublicKeyService;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerGetSessionsTest {

    private MockMvc mockMvc;
    private GetActiveSessionsUseCase getActiveSessionsUseCase;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CURRENT_SESSION_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        getActiveSessionsUseCase = mock(GetActiveSessionsUseCase.class);

        AuthController controller = new AuthController(
                mock(LoginUseCase.class),
                mock(RefreshTokenUseCase.class),
                mock(LogoutUseCase.class),
                mock(PublicKeyService.class),
                mock(RegisterUserUseCase.class),
                mock(ActivateAccountUseCase.class),
                mock(ResendOtpUseCase.class),
                mock(ResetPasswordUseCase.class),
                mock(ChangeTemporaryPasswordUseCase.class),
                getActiveSessionsUseCase);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(), new GlobalResponseBodyAdvice())
                .build();
    }

    private TestingAuthenticationToken createAuth() {
        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
                USER_ID.toString(),
                null,
                "jti-123",
                "STUDENT",
                CURRENT_SESSION_ID.toString(),
                Instant.now(),
                Instant.now().plusSeconds(3600));
        return new TestingAuthenticationToken(principal, null, "ROLE_STUDENT");
    }

    @Test
    void shouldReturn200WithActiveSessionsAndCurrentSessionFlag() throws Exception {
        UUID otherSessionId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-04T10:00:00Z");

        ActiveSession currentSession = new ActiveSession(
                CURRENT_SESSION_ID, "Chrome 120 on Windows 11", "14.232.208.12", now, true);
        ActiveSession otherSession = new ActiveSession(
                otherSessionId, "UniHub Mobile App on iPhone 15 Pro", "118.69.182.40", now.minusSeconds(3600), false);

        CursorPage<ActiveSession> page = new CursorPage<>(
                List.of(currentSession, otherSession),
                "next-cursor-token",
                true);

        when(getActiveSessionsUseCase.getActiveSessions(eq(USER_ID), eq(CURRENT_SESSION_ID), isNull(), eq(20)))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/auth/sessions")
                .principal(createAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Active sessions retrieved"))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].id").value(CURRENT_SESSION_ID.toString()))
                .andExpect(jsonPath("$.data.items[0].deviceInfo").value("Chrome 120 on Windows 11"))
                .andExpect(jsonPath("$.data.items[0].ipAddress").value("14.232.208.12"))
                .andExpect(jsonPath("$.data.items[0].isCurrentSession").value(true))
                .andExpect(jsonPath("$.data.items[1].id").value(otherSessionId.toString()))
                .andExpect(jsonPath("$.data.items[1].deviceInfo").value("UniHub Mobile App on iPhone 15 Pro"))
                .andExpect(jsonPath("$.data.items[1].ipAddress").value("118.69.182.40"))
                .andExpect(jsonPath("$.data.items[1].isCurrentSession").value(false))
                .andExpect(jsonPath("$.data.cursor.nextCursor").value("next-cursor-token"))
                .andExpect(jsonPath("$.data.cursor.hasMore").value(true));
    }

    @Test
    void shouldReturn200WithEmptyItems_whenNoSessions() throws Exception {
        CursorPage<ActiveSession> page = new CursorPage<>(List.of(), null, false);

        when(getActiveSessionsUseCase.getActiveSessions(eq(USER_ID), eq(CURRENT_SESSION_ID), isNull(), eq(20)))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/auth/sessions")
                .principal(createAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.cursor.hasMore").value(false))
                .andExpect(jsonPath("$.data.cursor.nextCursor").doesNotExist());
    }

    @Test
    void shouldReturn400_whenSizeIsOutOfRange() throws Exception {
        when(getActiveSessionsUseCase.getActiveSessions(any(), any(), any(), eq(0)))
                .thenThrow(new BadRequestException("size must be between 1 and 100"));

        mockMvc.perform(get("/api/v1/auth/sessions?size=0")
                .principal(createAuth()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.type").value("urn:unihub:error:bad-request"));
    }

    @Test
    void shouldReturn400_whenCursorIsMalformed() throws Exception {
        when(getActiveSessionsUseCase.getActiveSessions(any(), any(), eq("bad-cursor"), eq(20)))
                .thenThrow(new BadRequestException("Invalid cursor format"));

        mockMvc.perform(get("/api/v1/auth/sessions?cursor=bad-cursor")
                .principal(createAuth()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.type").value("urn:unihub:error:bad-request"));
    }

    @Test
    void shouldReturn401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/auth/sessions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.type").value("urn:unihub:error:unauthorized"));
    }
}
