package com.unihub.workshop.iam.presentation.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerLogoutTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private LogoutUseCase logoutUseCase;

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

    @Test
    void shouldReturn204OnSuccessfulLogout() throws Exception {
        LogoutRequest request = new LogoutRequest("some-refresh-token");

        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("sub", UUID.randomUUID().toString())
            .claim("jti", "some-jti")
            .claim("sid", UUID.randomUUID().toString())
            .claim("exp", Instant.now().plusSeconds(3600))
            .build();

        mockMvc
            .perform(
                post("/api/v1/auth/logout")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .principal(new JwtAuthenticationToken(jwt))
            )
            .andDo(MockMvcResultHandlers.print())
            .andExpect(status().isNoContent());

        verify(logoutUseCase).logout(
            any(),
            eq("some-jti"),
            any(),
            any(),
            eq("some-refresh-token")
        );
    }

    @Test
    void shouldReturn503AndRetryAfterWhenRedisIsDown() throws Exception {
        LogoutRequest request = new LogoutRequest("some-refresh-token");

        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("sub", UUID.randomUUID().toString())
            .claim("jti", "some-jti")
            .claim("sid", UUID.randomUUID().toString())
            .claim("exp", Instant.now().plusSeconds(3600))
            .build();

        doThrow(
            new ServiceUnavailableException(
                "urn:unihub:error:service-unavailable",
                "Redis down"
            )
        )
            .when(logoutUseCase)
            .logout(any(), any(), any(), any(), any());

        mockMvc
            .perform(
                post("/api/v1/auth/logout")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .principal(new JwtAuthenticationToken(jwt))
            )
            .andExpect(status().isServiceUnavailable())
            .andExpect(header().string("Retry-After", "30"));
    }
}
