package com.unihub.workshop.iam.presentation.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unihub.workshop.iam.application.ports.in.LoginUseCase;
import com.unihub.workshop.iam.application.ports.in.LogoutUseCase;
import com.unihub.workshop.iam.application.ports.in.RefreshTokenUseCase;
import com.unihub.workshop.iam.presentation.dto.LogoutRequest;
import com.unihub.workshop.shared.exception.ServiceUnavailableException;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerLogoutTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private LogoutUseCase logoutUseCase;

    private final UUID userId = UUID.randomUUID();
    private final String jti = "test-jti-123";

    @BeforeEach
    void setUp() {
        LoginUseCase loginUseCase = mock(LoginUseCase.class);
        RefreshTokenUseCase refreshTokenUseCase = mock(
            RefreshTokenUseCase.class
        );
        logoutUseCase = mock(LogoutUseCase.class);
        objectMapper = new ObjectMapper();

        AuthController authController = AuthControllerTestBuilder.builder()
            .withLoginUseCase(loginUseCase)
            .withRefreshTokenUseCase(refreshTokenUseCase)
            .withLogoutUseCase(logoutUseCase)
            .build();

        mockMvc = MockMvcBuilders.standaloneSetup(authController)
            .setControllerAdvice(
                new GlobalExceptionHandler(),
                new GlobalResponseBodyAdvice()
            )
            .build();
    }

    private JwtAuthenticationToken createMockAuth() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("sub", userId.toString())
            .claim("jti", jti)
            .claim("sid", UUID.randomUUID().toString())
            .claim("exp", Instant.now().plusSeconds(3600))
            .build();
        return new JwtAuthenticationToken(jwt);
    }

    @Test
    void shouldReturn204OnSuccessfulLogoutWithSpecificRefreshToken()
        throws Exception {
        LogoutRequest request = new LogoutRequest("some-refresh-token");

        mockMvc
            .perform(
                post("/api/v1/auth/logout")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .principal(createMockAuth())
            )
            .andExpect(status().isNoContent());

        verify(logoutUseCase).logout(
            eq(jti),
            any(Instant.class),
            eq(userId),
            eq("some-refresh-token")
        );
    }

    @Test
    void shouldReturn204OnSuccessfulLogoutWithoutBody() throws Exception {
        mockMvc
            .perform(post("/api/v1/auth/logout").principal(createMockAuth()))
            .andExpect(status().isNoContent());

        verify(logoutUseCase).logout(
            eq(jti),
            any(Instant.class),
            eq(userId),
            isNull()
        );
    }

    @Test
    void shouldReturn204OnSuccessfulLogoutWithEmptyJsonBody() throws Exception {
        mockMvc
            .perform(
                post("/api/v1/auth/logout")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
                    .principal(createMockAuth())
            )
            .andExpect(status().isNoContent());

        verify(logoutUseCase).logout(
            eq(jti),
            any(Instant.class),
            eq(userId),
            isNull()
        );
    }

    @Test
    void shouldAutoTrimRefreshTokenWhitespace() throws Exception {
        LogoutRequest request = new LogoutRequest("   padded-token   ");

        mockMvc
            .perform(
                post("/api/v1/auth/logout")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .principal(createMockAuth())
            )
            .andExpect(status().isNoContent());

        verify(logoutUseCase).logout(
            eq(jti),
            any(Instant.class),
            eq(userId),
            eq("padded-token")
        );
    }

    @Test
    void shouldReturn422WhenRefreshTokenExceedsMaxLength() throws Exception {
        String oversizedToken = "a".repeat(256);
        LogoutRequest request = new LogoutRequest(oversizedToken);

        mockMvc
            .perform(
                post("/api/v1/auth/logout")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .principal(createMockAuth())
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            )
            .andExpect(jsonPath("$.error.status").value(422));

        verifyNoInteractions(logoutUseCase);
    }

    @Test
    void shouldReturn400OnMalformedJsonPayload() throws Exception {
        mockMvc
            .perform(
                post("/api/v1/auth/logout")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{malformed-json")
                    .principal(createMockAuth())
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:bad-request")
            )
            .andExpect(jsonPath("$.error.status").value(400));

        verifyNoInteractions(logoutUseCase);
    }

    @Test
    void shouldReturn415OnUnsupportedMediaType() throws Exception {
        mockMvc
            .perform(
                post("/api/v1/auth/logout")
                    .contentType(MediaType.TEXT_PLAIN)
                    .content("some-plain-text")
                    .principal(createMockAuth())
            )
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(
                jsonPath("$.error.type").value(
                    "urn:unihub:error:unsupported-media-type"
                )
            )
            .andExpect(jsonPath("$.error.status").value(415));

        verifyNoInteractions(logoutUseCase);
    }

    @Test
    void shouldReturn401WhenUnauthenticated() throws Exception {
        mockMvc
            .perform(post("/api/v1/auth/logout"))
            .andExpect(status().isUnauthorized())
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:unauthorized")
            )
            .andExpect(jsonPath("$.error.status").value(401));

        verifyNoInteractions(logoutUseCase);
    }

    @Test
    void shouldReturn503AndRetryAfterWhenRedisIsDown() throws Exception {
        LogoutRequest request = new LogoutRequest("some-refresh-token");

        doThrow(
            new ServiceUnavailableException(
                "urn:unihub:error:service-unavailable",
                "Redis down"
            )
        )
            .when(logoutUseCase)
            .logout(any(), any(), any(), any());

        mockMvc
            .perform(
                post("/api/v1/auth/logout")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .principal(createMockAuth())
            )
            .andExpect(status().isServiceUnavailable())
            .andExpect(header().string("Retry-After", "30"))
            .andExpect(
                jsonPath("$.error.type").value(
                    "urn:unihub:error:service-unavailable"
                )
            )
            .andExpect(jsonPath("$.error.status").value(503));
    }
}
