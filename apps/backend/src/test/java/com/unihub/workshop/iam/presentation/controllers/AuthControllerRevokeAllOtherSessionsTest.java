package com.unihub.workshop.iam.presentation.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.unihub.workshop.iam.application.ports.in.RevokeAllOtherSessionsUseCase;
import com.unihub.workshop.shared.exception.ServiceUnavailableException;
import com.unihub.workshop.shared.security.JwtAuthenticatedPrincipal;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerRevokeAllOtherSessionsTest {

    private MockMvc mockMvc;
    private RevokeAllOtherSessionsUseCase revokeAllOtherSessionsUseCase;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CURRENT_SESSION_ID = UUID.randomUUID();
    private static final String CURRENT_JTI = "jti-123";

    @BeforeEach
    void setUp() {
        revokeAllOtherSessionsUseCase = mock(
            RevokeAllOtherSessionsUseCase.class
        );
        AuthController controller = AuthControllerTestBuilder.builder()
            .withRevokeAllOtherSessionsUseCase(revokeAllOtherSessionsUseCase)
            .build();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(
                new GlobalExceptionHandler(),
                new GlobalResponseBodyAdvice()
            )
            .build();
    }

    private TestingAuthenticationToken createAuth(String sid) {
        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
            USER_ID.toString(),
            null,
            CURRENT_JTI,
            "STUDENT",
            sid,
            Instant.now(),
            Instant.now().plusSeconds(3600)
        );
        return new TestingAuthenticationToken(principal, null, "ROLE_STUDENT");
    }

    @Test
    void shouldReturn204_whenRevokingAllOtherSessionsSuccessfully()
        throws Exception {
        mockMvc
            .perform(
                delete("/api/v1/auth/sessions").principal(
                    createAuth(CURRENT_SESSION_ID.toString())
                )
            )
            .andExpect(status().isNoContent());

        verify(revokeAllOtherSessionsUseCase).revokeAllOtherSessions(
            eq(USER_ID),
            eq(CURRENT_SESSION_ID)
        );
    }

    @Test
    void shouldReturn400_whenSidIsMissingFromToken() throws Exception {
        mockMvc
            .perform(
                delete("/api/v1/auth/sessions").principal(createAuth(null))
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:bad-request")
            );

        verifyNoInteractions(revokeAllOtherSessionsUseCase);
    }

    @Test
    void shouldReturn400_whenSidIsNotAValidUuid() throws Exception {
        mockMvc
            .perform(
                delete("/api/v1/auth/sessions").principal(
                    createAuth("invalid-uuid-string")
                )
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:bad-request")
            );

        verifyNoInteractions(revokeAllOtherSessionsUseCase);
    }

    @Test
    void shouldReturn401_whenUnauthenticated() throws Exception {
        mockMvc
            .perform(delete("/api/v1/auth/sessions"))
            .andExpect(status().isUnauthorized())
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:unauthorized")
            );

        verifyNoInteractions(revokeAllOtherSessionsUseCase);
    }

    @Test
    void shouldReturn204_whenOrganizerRevokesAllOtherSessionsSuccessfully()
        throws Exception {
        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
            USER_ID.toString(),
            UUID.randomUUID().toString(),
            CURRENT_JTI,
            "ORGANIZER",
            CURRENT_SESSION_ID.toString(),
            Instant.now(),
            Instant.now().plusSeconds(3600)
        );
        TestingAuthenticationToken auth = new TestingAuthenticationToken(
            principal,
            null,
            "ROLE_ORGANIZER"
        );

        mockMvc
            .perform(delete("/api/v1/auth/sessions").principal(auth))
            .andExpect(status().isNoContent());

        verify(revokeAllOtherSessionsUseCase).revokeAllOtherSessions(
            eq(USER_ID),
            eq(CURRENT_SESSION_ID)
        );
    }

    @Test
    void shouldReturn204_whenStaffRevokesAllOtherSessionsSuccessfully()
        throws Exception {
        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
            USER_ID.toString(),
            null,
            CURRENT_JTI,
            "STAFF",
            CURRENT_SESSION_ID.toString(),
            Instant.now(),
            Instant.now().plusSeconds(3600)
        );
        TestingAuthenticationToken auth = new TestingAuthenticationToken(
            principal,
            null,
            "ROLE_STAFF"
        );

        mockMvc
            .perform(delete("/api/v1/auth/sessions").principal(auth))
            .andExpect(status().isNoContent());

        verify(revokeAllOtherSessionsUseCase).revokeAllOtherSessions(
            eq(USER_ID),
            eq(CURRENT_SESSION_ID)
        );
    }

    @Test
    void shouldReturn400_whenSidIsBlankString() throws Exception {
        mockMvc
            .perform(
                delete("/api/v1/auth/sessions").principal(createAuth("   "))
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:bad-request")
            );

        verifyNoInteractions(revokeAllOtherSessionsUseCase);
    }

    @Test
    void shouldReturn503WithRetryAfter_whenRedisIsUnavailable()
        throws Exception {
        doThrow(
            new ServiceUnavailableException(
                "urn:unihub:error:service-unavailable",
                "Redis is unavailable"
            )
        )
            .when(revokeAllOtherSessionsUseCase)
            .revokeAllOtherSessions(any(), any());

        mockMvc
            .perform(
                delete("/api/v1/auth/sessions").principal(
                    createAuth(CURRENT_SESSION_ID.toString())
                )
            )
            .andExpect(status().isServiceUnavailable())
            .andExpect(header().string(HttpHeaders.RETRY_AFTER, "30"))
            .andExpect(
                jsonPath("$.error.type").value(
                    "urn:unihub:error:service-unavailable"
                )
            );
    }
}
