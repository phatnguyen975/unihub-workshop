package com.unihub.workshop.iam.presentation.controllers;

import com.unihub.workshop.iam.application.ports.in.RegisterUserUseCase;
import com.unihub.workshop.shared.exception.ConflictException;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerRegisterTest {

    private MockMvc mockMvc;
    private RegisterUserUseCase registerUserUseCase;

    @BeforeEach
    void setUp() {
        registerUserUseCase = mock(RegisterUserUseCase.class);
        AuthController authController = AuthControllerTestBuilder.builder()
                .withRegisterUserUseCase(registerUserUseCase)
                .build();
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler(null), new GlobalResponseBodyAdvice())
                .build();
    }

    @Test
    void shouldReturn201OnSuccessfulRegistration() throws Exception {
        UUID userId = UUID.randomUUID();
        when(registerUserUseCase.register("alice@university.edu", "Str0ng!Pass", "Alice Smith"))
                .thenReturn(userId);

        String requestBody = """
                {
                    "email": "alice@university.edu",
                    "password": "Str0ng!Pass",
                    "fullName": "Alice Smith"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(userId.toString()))
                .andExpect(jsonPath("$.message")
                        .value("Registration successful. Please check your email for the activation code."));
    }

    @Test
    void shouldReturn409OnDuplicateEmail() throws Exception {
        when(registerUserUseCase.register(any(), any(), any()))
                .thenThrow(new ConflictException("An account with this email already exists."));

        String requestBody = """
                {
                    "email": "existing@university.edu",
                    "password": "Str0ng!Pass",
                    "fullName": "Alice Smith"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.type").value("urn:unihub:error:conflict"));
    }

    @Test
    void shouldReturn422OnInvalidEmailFormat() throws Exception {
        String requestBody = """
                {
                    "email": "not-an-email",
                    "password": "Str0ng!Pass",
                    "fullName": "Alice Smith"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.type").value("urn:unihub:error:validation"))
                .andExpect(jsonPath("$.error.validationErrors[0]").exists());
    }

    @Test
    void shouldReturn422OnWeakPassword() throws Exception {
        String requestBody = """
                {
                    "email": "alice@university.edu",
                    "password": "password",
                    "fullName": "Alice Smith"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.type").value("urn:unihub:error:validation"))
                .andExpect(jsonPath("$.error.validationErrors[0]").exists());
    }
}
