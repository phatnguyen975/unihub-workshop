package com.unihub.workshop.iam.presentation.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unihub.workshop.iam.application.ports.in.ResetPasswordUseCase;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerResetPasswordTest {

    private MockMvc mockMvc;
    private ResetPasswordUseCase resetPasswordUseCase;

    @BeforeEach
    void setUp() {
        resetPasswordUseCase = mock(ResetPasswordUseCase.class);
        AuthController authController = AuthControllerTestBuilder.builder()
            .withResetPasswordUseCase(resetPasswordUseCase)
            .build();
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
            .setControllerAdvice(
                new GlobalExceptionHandler(),
                new GlobalResponseBodyAdvice()
            )
            .build();
    }

    @Test
    void shouldReturn200OnSuccessfulPasswordReset() throws Exception {
        String requestBody = """
        {
            "email": "student@university.edu",
            "otp": "849201",
            "newPassword": "BrandNewSecurePassword2026!"
        }
        """;

        mockMvc
            .perform(
                post("/api/v1/auth/password/reset")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(
                jsonPath("$.message").value(
                    "Password reset successfully. Please sign in with your new password."
                )
            );

        verify(resetPasswordUseCase).resetPassword(
            "student@university.edu",
            "849201",
            "BrandNewSecurePassword2026!"
        );
    }

    @Test
    void shouldReturn422WhenOtpInvalidOrExpired() throws Exception {
        doThrow(
            new ValidationException(
                "otp",
                "Invalid or expired verification code."
            )
        )
            .when(resetPasswordUseCase)
            .resetPassword(any(), any(), any());

        String requestBody = """
        {
            "email": "student@university.edu",
            "otp": "999999",
            "newPassword": "BrandNewSecurePassword2026!"
        }
        """;

        mockMvc
            .perform(
                post("/api/v1/auth/password/reset")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            )
            .andExpect(
                jsonPath("$.error.validationErrors[0]").value(
                    "otp: Invalid or expired verification code."
                )
            );
    }

    @Test
    void shouldReturn422WhenPasswordTooShort() throws Exception {
        String requestBody = """
        {
            "email": "student@university.edu",
            "otp": "849201",
            "newPassword": "Weak1!"
        }
        """;

        mockMvc
            .perform(
                post("/api/v1/auth/password/reset")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            )
            .andExpect(jsonPath("$.error.validationErrors[0]").exists());

        verify(resetPasswordUseCase, never()).resetPassword(
            any(),
            any(),
            any()
        );
    }

    @Test
    void shouldReturn422WhenOtpMalformed() throws Exception {
        String requestBody = """
        {
            "email": "student@university.edu",
            "otp": "123",
            "newPassword": "BrandNewSecurePassword2026!"
        }
        """;

        mockMvc
            .perform(
                post("/api/v1/auth/password/reset")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            )
            .andExpect(jsonPath("$.error.validationErrors[0]").exists());

        verify(resetPasswordUseCase, never()).resetPassword(
            any(),
            any(),
            any()
        );
    }

    @Test
    void shouldReturn422WhenEmailMalformed() throws Exception {
        String requestBody = """
        {
            "email": "invalid-email",
            "otp": "849201",
            "newPassword": "BrandNewSecurePassword2026!"
        }
        """;

        mockMvc
            .perform(
                post("/api/v1/auth/password/reset")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            )
            .andExpect(jsonPath("$.error.validationErrors[0]").exists());

        verify(resetPasswordUseCase, never()).resetPassword(
            any(),
            any(),
            any()
        );
    }

    @Test
    void shouldReturn404WhenEmailNotFound() throws Exception {
        doThrow(new NotFoundException("No account found for this email."))
            .when(resetPasswordUseCase)
            .resetPassword(any(), any(), any());

        String requestBody = """
        {
            "email": "unknown@university.edu",
            "otp": "849201",
            "newPassword": "BrandNewSecurePassword2026!"
        }
        """;

        mockMvc
            .perform(
                post("/api/v1/auth/password/reset")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:not-found")
            )
            .andExpect(
                jsonPath("$.error.detail").value(
                    "No account found for this email."
                )
            );
    }

    @Test
    void shouldTrimWhitespaceFromEmailAndOtpOnResetPassword() throws Exception {
        String requestBody = """
        {
            "email": "   student@university.edu   ",
            "otp": "   849201   ",
            "newPassword": "BrandNewSecurePassword2026!"
        }
        """;

        mockMvc
            .perform(
                post("/api/v1/auth/password/reset")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(resetPasswordUseCase).resetPassword(
            "student@university.edu",
            "849201",
            "BrandNewSecurePassword2026!"
        );
    }

    @Test
    void shouldReturn400OnMalformedJson() throws Exception {
        mockMvc
            .perform(
                post("/api/v1/auth/password/reset")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{ malformed json }")
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
                post("/api/v1/auth/password/reset")
                    .contentType(MediaType.TEXT_PLAIN)
                    .content("raw plain text body")
            )
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value(
                    "urn:unihub:error:unsupported-media-type"
                )
            );
    }

    @Test
    void shouldReturn422WhenRequiredFieldsMissing() throws Exception {
        mockMvc
            .perform(
                post("/api/v1/auth/password/reset")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            )
            .andExpect(jsonPath("$.error.validationErrors[0]").exists());
    }
}
