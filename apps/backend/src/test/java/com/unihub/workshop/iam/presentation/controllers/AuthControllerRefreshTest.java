package com.unihub.workshop.iam.presentation.controllers;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unihub.workshop.iam.application.ports.in.LoginUseCase;
import com.unihub.workshop.iam.application.ports.in.RefreshTokenUseCase;
import com.unihub.workshop.iam.application.ports.in.RefreshTokenUseCase.RefreshResult;
import com.unihub.workshop.iam.presentation.dto.RefreshTokenRequest;
import com.unihub.workshop.shared.exception.UnauthorizedException;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerRefreshTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private LoginUseCase loginUseCase;
    private RefreshTokenUseCase refreshTokenUseCase;

    @BeforeEach
    void setUp() {
        loginUseCase = mock(LoginUseCase.class);
        refreshTokenUseCase = mock(RefreshTokenUseCase.class);
        objectMapper = new ObjectMapper();

        AuthController authController = AuthControllerTestBuilder.builder()
            .withLoginUseCase(loginUseCase)
            .withRefreshTokenUseCase(refreshTokenUseCase)
            .build();

        mockMvc = MockMvcBuilders.standaloneSetup(authController)
            .setControllerAdvice(
                new GlobalExceptionHandler(),
                new GlobalResponseBodyAdvice()
            )
            .build();
    }

    @Test
    void shouldReturn200OnSuccessfulRefresh() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest(
            "valid-refresh-token"
        );
        RefreshResult result = new RefreshResult(
            "new-access-token",
            "new-refresh-token",
            "Bearer",
            3600
        );

        when(
            refreshTokenUseCase.refresh(anyString(), anyString(), anyString())
        ).thenReturn(result);

        mockMvc
            .perform(
                post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .header("User-Agent", "Test-Agent")
                    .with(request1 -> {
                        request1.setRemoteAddr("127.0.0.1");
                        return request1;
                    })
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Token refreshed"))
            .andExpect(jsonPath("$.data.accessToken").value("new-access-token"))
            .andExpect(
                jsonPath("$.data.refreshToken").value("new-refresh-token")
            )
            .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.data.expiresIn").value(3600));
    }

    @Test
    void shouldAutoTrimRefreshTokenBeforeCallingUseCase() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest(
            "   valid-refresh-token-with-spaces   "
        );
        RefreshResult result = new RefreshResult(
            "new-access-token",
            "new-refresh-token",
            "Bearer",
            3600
        );

        when(
            refreshTokenUseCase.refresh(
                eq("valid-refresh-token-with-spaces"),
                anyString(),
                anyString()
            )
        ).thenReturn(result);

        mockMvc
            .perform(
                post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .header("User-Agent", "Test-Agent")
                    .with(request1 -> {
                        request1.setRemoteAddr("127.0.0.1");
                        return request1;
                    })
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(refreshTokenUseCase).refresh(
            eq("valid-refresh-token-with-spaces"),
            eq("Test-Agent"),
            eq("127.0.0.1")
        );
    }

    @Test
    void shouldReturn401WhenUseCaseThrowsUnauthorized() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest(
            "invalid-refresh-token"
        );

        when(
            refreshTokenUseCase.refresh(anyString(), anyString(), anyString())
        ).thenThrow(new UnauthorizedException("Invalid refresh token"));

        mockMvc
            .perform(
                post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .header("User-Agent", "Test-Agent")
                    .with(request1 -> {
                        request1.setRemoteAddr("127.0.0.1");
                        return request1;
                    })
            )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:unauthorized")
            )
            .andExpect(
                jsonPath("$.error.detail").value("Invalid refresh token")
            );
    }

    @Test
    void shouldReturn422OnBlankRefreshToken() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("");

        mockMvc
            .perform(
                post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            )
            .andExpect(
                jsonPath("$.error.validationErrors[0]").value(
                    "refreshToken: refreshToken must not be blank"
                )
            );
    }

    @Test
    void shouldReturn422OnEmptyBody() throws Exception {
        mockMvc
            .perform(
                post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            );
    }

    @Test
    void shouldReturn422WhenRefreshTokenExceedsMaxLength() throws Exception {
        String oversizedToken = "a".repeat(256);
        RefreshTokenRequest request = new RefreshTokenRequest(oversizedToken);

        mockMvc
            .perform(
                post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            )
            .andExpect(
                jsonPath("$.error.validationErrors[0]").value(
                    "refreshToken: refreshToken must not exceed 255 characters"
                )
            );
    }

    @Test
    void shouldReturn400OnMalformedJson() throws Exception {
        mockMvc
            .perform(
                post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"refreshToken\": ")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:bad-request")
            );
    }

    @Test
    void shouldReturn415OnUnsupportedMediaType() throws Exception {
        mockMvc
            .perform(
                post("/api/v1/auth/refresh")
                    .contentType(MediaType.TEXT_PLAIN)
                    .content("raw-token")
            )
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value(
                    "urn:unihub:error:unsupported-media-type"
                )
            );
    }
}
