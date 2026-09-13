package com.unihub.workshop.iam.presentation.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unihub.workshop.iam.application.ports.in.ActivateAccountUseCase;
import com.unihub.workshop.shared.exception.ConflictException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerActivateTest {

    private MockMvc mockMvc;
    private ActivateAccountUseCase activateAccountUseCase;

    @BeforeEach
    void setUp() {
        activateAccountUseCase = mock(ActivateAccountUseCase.class);
        AuthController authController = AuthControllerTestBuilder.builder()
            .withActivateAccountUseCase(activateAccountUseCase)
            .build();
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
            .setControllerAdvice(
                new GlobalExceptionHandler(null),
                new GlobalResponseBodyAdvice()
            )
            .build();
    }

    @Test
    void shouldReturn200OnSuccessfulActivation() throws Exception {
        String requestBody = """
        {
            "email": "student@university.edu",
            "otp": "123456"
        }
        """;

        mockMvc
            .perform(
                post("/api/v1/auth/activate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(
                jsonPath("$.message").value(
                    "Account activated successfully. You may now log in."
                )
            );

        verify(activateAccountUseCase).activate(
            "student@university.edu",
            "123456"
        );
    }

    @Test
    void shouldReturn422OnInvalidOtp() throws Exception {
        doThrow(
            new ValidationException(
                "otp",
                "Invalid or expired activation code."
            )
        )
            .when(activateAccountUseCase)
            .activate(any(), any());

        String requestBody = """
        {
            "email": "student@university.edu",
            "otp": "000000"
        }
        """;

        mockMvc
            .perform(
                post("/api/v1/auth/activate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().is(422))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            )
            .andExpect(
                jsonPath("$.error.detail").value(
                    "Invalid or expired activation code."
                )
            )
            .andExpect(
                jsonPath("$.error.validationErrors[0]").value(
                    "otp: Invalid or expired activation code."
                )
            );
    }

    @Test
    void shouldReturn409WhenAccountAlreadyActive() throws Exception {
        doThrow(new ConflictException("Account is already active."))
            .when(activateAccountUseCase)
            .activate(any(), any());

        String requestBody = """
        {
            "email": "active@university.edu",
            "otp": "123456"
        }
        """;

        mockMvc
            .perform(
                post("/api/v1/auth/activate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:conflict")
            );
    }

    @Test
    void shouldReturn422OnMalformedEmail() throws Exception {
        String requestBody = """
        {
            "email": "not-an-email",
            "otp": "123456"
        }
        """;

        mockMvc
            .perform(
                post("/api/v1/auth/activate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().is(422))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            )
            .andExpect(jsonPath("$.error.validationErrors[0]").exists());
    }

    @Test
    void shouldReturn422OnInvalidOtpFormat() throws Exception {
        String requestBody = """
        {
            "email": "student@university.edu",
            "otp": "123"
        }
        """;

        mockMvc
            .perform(
                post("/api/v1/auth/activate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().is(422))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            )
            .andExpect(jsonPath("$.error.validationErrors[0]").exists());
    }
}
