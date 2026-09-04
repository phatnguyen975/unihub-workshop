package com.unihub.workshop.iam.presentation.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unihub.workshop.iam.application.ports.in.ActivateAccountUseCase;
import com.unihub.workshop.iam.application.ports.in.LoginUseCase;
import com.unihub.workshop.iam.application.ports.in.LogoutUseCase;
import com.unihub.workshop.iam.application.ports.in.RefreshTokenUseCase;
import com.unihub.workshop.iam.application.ports.in.RegisterUserUseCase;
import com.unihub.workshop.iam.application.ports.in.ResendOtpUseCase;
import com.unihub.workshop.shared.security.PublicKeyService;
import com.unihub.workshop.iam.presentation.dto.RefreshTokenRequest;
import com.unihub.workshop.shared.exception.UnauthorizedException;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(
                        loginUseCase,
                        refreshTokenUseCase,
                        mock(LogoutUseCase.class),
                        mock(PublicKeyService.class),
                        mock(RegisterUserUseCase.class),
                        mock(ActivateAccountUseCase.class),
                        mock(ResendOtpUseCase.class)))
                .setControllerAdvice(new GlobalExceptionHandler(), new GlobalResponseBodyAdvice())
                .build();
    }

    @Test
    void shouldReturn200OnSuccessfulRefresh() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("valid-refresh-token");
        LoginUseCase.LoginResult result = new LoginUseCase.LoginResult(
                "new-access-token",
                "new-refresh-token",
                "Bearer",
                3600);

        when(refreshTokenUseCase.refresh(anyString(), anyString(), anyString())).thenReturn(result);

        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("User-Agent", "Test-Agent")
                .with(request1 -> {
                    request1.setRemoteAddr("127.0.0.1");
                    return request1;
                }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Token refreshed"))
                .andExpect(jsonPath("$.data.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("new-refresh-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(3600));
    }

    @Test
    void shouldReturn422OnBlankRefreshToken() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("");

        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andDo(MockMvcResultHandlers.print()).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.type").value("urn:unihub:error:validation"))
                .andExpect(
                        jsonPath("$.error.validationErrors[0]").value("refreshToken: refreshToken must not be blank"));
    }

    @Test
    void shouldReturn401WhenUseCaseThrowsUnauthorized() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("invalid-refresh-token");

        when(refreshTokenUseCase.refresh(anyString(), anyString(), anyString()))
                .thenThrow(new UnauthorizedException("urn:unihub:error:authentication", "Invalid refresh token"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("User-Agent", "Test-Agent")
                .with(request1 -> {
                    request1.setRemoteAddr("127.0.0.1");
                    return request1;
                }))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.type").value("urn:unihub:error:authentication"))
                .andExpect(jsonPath("$.error.detail").value("Invalid refresh token"));
    }
}
