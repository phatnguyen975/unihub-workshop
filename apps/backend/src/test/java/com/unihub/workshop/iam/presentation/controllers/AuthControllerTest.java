package com.unihub.workshop.iam.presentation.controllers;

import com.unihub.workshop.iam.application.ports.in.LoginUseCase;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private MockMvc mockMvc;
    private LoginUseCase loginUseCase;

    @BeforeEach
    void setUp() {
        loginUseCase = mock(LoginUseCase.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(loginUseCase, mock(com.unihub.workshop.iam.application.ports.in.RefreshTokenUseCase.class)))
                .setControllerAdvice(new GlobalExceptionHandler(), new GlobalResponseBodyAdvice())
                .build();
    }

    @Test
    void shouldReturn200OnSuccessfulLogin() throws Exception {
        when(loginUseCase.login(eq("test@example.com"), eq("password"), any(), any()))
                .thenReturn(new LoginUseCase.LoginResult("access-token", "refresh-token", "Bearer", 900L));

        String requestBody = """
                {
                    "email": "test@example.com",
                    "password": "password"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .header("User-Agent", "Test-Agent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(900));
    }

    @Test
    void shouldReturn401OnInvalidCredentials() throws Exception {
        when(loginUseCase.login(any(), any(), any(), any()))
                .thenThrow(new com.unihub.workshop.shared.exception.UnauthorizedException("urn:unihub:error:authentication", "Invalid credentials"));

        String requestBody = """
                {
                    "email": "wrong@example.com",
                    "password": "wrong"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.type").value("urn:unihub:error:authentication"));
    }

    @Test
    void shouldReturn403OnAccountDisabled() throws Exception {
        when(loginUseCase.login(any(), any(), any(), any()))
                .thenThrow(new com.unihub.workshop.shared.exception.ForbiddenException("urn:unihub:error:account-disabled", "Account disabled"));

        String requestBody = """
                {
                    "email": "disabled@example.com",
                    "password": "password"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.type").value("urn:unihub:error:account-disabled"));
    }
    
    @Test
    void shouldReturn403OnTemporaryPassword() throws Exception {
        when(loginUseCase.login(any(), any(), any(), any()))
                .thenThrow(new com.unihub.workshop.shared.exception.ForbiddenException("urn:unihub:error:temporary-password", "Temporary password change required"));

        String requestBody = """
                {
                    "email": "temp@example.com",
                    "password": "password"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.type").value("urn:unihub:error:temporary-password"));
    }
}
