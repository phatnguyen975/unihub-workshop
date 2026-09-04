package com.unihub.workshop.iam.presentation.controllers;

import com.unihub.workshop.iam.application.ports.in.ActivateAccountUseCase;
import com.unihub.workshop.iam.application.ports.in.LoginUseCase;
import com.unihub.workshop.iam.application.ports.in.LogoutUseCase;
import com.unihub.workshop.iam.application.ports.in.RefreshTokenUseCase;
import com.unihub.workshop.iam.application.ports.in.RegisterUserUseCase;
import com.unihub.workshop.iam.application.ports.in.ResendOtpUseCase;
import com.unihub.workshop.shared.security.PublicKeyService;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerResendOtpTest {

    private MockMvc mockMvc;
    private ResendOtpUseCase resendOtpUseCase;

    @BeforeEach
    void setUp() {
        resendOtpUseCase = mock(ResendOtpUseCase.class);
        AuthController authController = new AuthController(
                mock(LoginUseCase.class),
                mock(RefreshTokenUseCase.class),
                mock(LogoutUseCase.class),
                mock(PublicKeyService.class),
                mock(RegisterUserUseCase.class),
                mock(ActivateAccountUseCase.class),
                resendOtpUseCase);

        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler(), new GlobalResponseBodyAdvice())
                .build();
    }

    @Test
    void shouldReturn200OnSuccessfulOtpResend() throws Exception {
        String requestBody = """
                {
                    "email": "student@university.edu"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/otp/resend")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("A new activation code has been sent to your email."));

        verify(resendOtpUseCase).resendOtp("student@university.edu");
    }

    @Test
    void shouldReturn422WhenAccountNotFoundOrAlreadyActive() throws Exception {
        doThrow(new IllegalStateException("No inactive account found for this email."))
                .when(resendOtpUseCase).resendOtp(any());

        String requestBody = """
                {
                    "email": "unknown@university.edu"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/otp/resend")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.type").value("urn:unihub:error:state"))
                .andExpect(jsonPath("$.error.detail").value("No inactive account found for this email."));
    }

    @Test
    void shouldReturn422OnMalformedEmail() throws Exception {
        String requestBody = """
                {
                    "email": "not-an-email"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/otp/resend")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.type").value("urn:unihub:error:validation"))
                .andExpect(jsonPath("$.error.validationErrors[0]").exists());
    }

    @Test
    void shouldReturn422OnBlankEmail() throws Exception {
        String requestBody = """
                {
                    "email": ""
                }
                """;

        mockMvc.perform(post("/api/v1/auth/otp/resend")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.type").value("urn:unihub:error:validation"))
                .andExpect(jsonPath("$.error.validationErrors[0]").exists());
    }
}
