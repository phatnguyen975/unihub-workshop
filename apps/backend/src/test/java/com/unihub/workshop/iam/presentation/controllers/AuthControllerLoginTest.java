package com.unihub.workshop.iam.presentation.controllers;

import com.unihub.workshop.iam.application.ports.in.ActivateAccountUseCase;
import com.unihub.workshop.iam.application.ports.in.ChangeTemporaryPasswordUseCase;
import com.unihub.workshop.iam.application.ports.in.GetActiveSessionsUseCase;
import com.unihub.workshop.iam.application.ports.in.LoginUseCase;
import com.unihub.workshop.iam.application.ports.in.LogoutUseCase;
import com.unihub.workshop.iam.application.ports.in.RefreshTokenUseCase;
import com.unihub.workshop.iam.application.ports.in.RegisterUserUseCase;
import com.unihub.workshop.iam.application.ports.in.ResendOtpUseCase;
import com.unihub.workshop.iam.application.ports.in.ResetPasswordUseCase;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserRole;
import com.unihub.workshop.shared.security.PublicKeyService;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.UnauthorizedException;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerLoginTest {

    private MockMvc mockMvc;
    private LoginUseCase loginUseCase;

    @BeforeEach
    void setUp() {
        loginUseCase = mock(LoginUseCase.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new AuthController(loginUseCase, mock(RefreshTokenUseCase.class),
                                mock(LogoutUseCase.class),
                                mock(PublicKeyService.class),
                                mock(RegisterUserUseCase.class),
                                mock(ActivateAccountUseCase.class),
                                mock(ResendOtpUseCase.class),
                                mock(ResetPasswordUseCase.class),
                                mock(ChangeTemporaryPasswordUseCase.class),
                                mock(GetActiveSessionsUseCase.class)))
                .setControllerAdvice(new GlobalExceptionHandler(), new GlobalResponseBodyAdvice())
                .build();
    }

    @Test
    void shouldReturn200OnSuccessfulLogin() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        User mockUser = new User(userId, orgId, UserRole.STUDENT, "test@example.com", "hash", true, false, null);

        when(loginUseCase.login(eq("test@example.com"), eq("password"), any(), any()))
                .thenReturn(new LoginUseCase.LoginResult("access-token", "refresh-token", "Bearer",
                        900L, mockUser));

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
                .andExpect(jsonPath("$.data.expiresIn").value(900))
                .andExpect(jsonPath("$.data.user.id").value(userId.toString()))
                .andExpect(jsonPath("$.data.user.email").value("test@example.com"))
                .andExpect(jsonPath("$.data.user.role").value("STUDENT"))
                .andExpect(jsonPath("$.data.user.organizationId").value(orgId.toString()));
    }

    @Test
    void shouldReturn401OnInvalidCredentials() throws Exception {
        when(loginUseCase.login(any(), any(), any(), any()))
                .thenThrow(new UnauthorizedException("Invalid email or password"));

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
                .andExpect(jsonPath("$.error.type").value("urn:unihub:error:unauthorized"));
    }

    @Test
    void shouldReturn403OnAccountDisabled() throws Exception {
        when(loginUseCase.login(any(), any(), any(), any()))
                .thenThrow(new ForbiddenException(
                        "urn:unihub:error:account-disabled", "Account is disabled"));

        String requestBody = """
                {
                    "email": "disabled@example.com",
                    "password": "password"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.type").value("urn:unihub:error:account-disabled"));
    }

    @Test
    void shouldReturn403OnTemporaryPassword() throws Exception {
        when(loginUseCase.login(any(), any(), any(), any()))
                .thenThrow(new ForbiddenException(
                        "urn:unihub:error:temporary-password",
                        "Temporary password change required"));

        String requestBody = """
                {
                    "email": "temp@example.com",
                    "password": "password"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.type").value("urn:unihub:error:temporary-password"));
    }

    @Test
    void shouldReturn422OnInvalidEmail() throws Exception {
        String requestBody = """
                {
                    "email": "not-a-valid-email",
                    "password": "password"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.type").value("urn:unihub:error:validation"));
    }
}
