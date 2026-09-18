package com.unihub.workshop.iam.presentation.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unihub.workshop.iam.application.ports.in.ChangeTemporaryPasswordUseCase;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserRole;
import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.exception.UnauthorizedException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerTemporaryPasswordTest {

    private MockMvc mockMvc;
    private ChangeTemporaryPasswordUseCase changeTemporaryPasswordUseCase;

    @BeforeEach
    void setUp() {
        changeTemporaryPasswordUseCase = mock(
            ChangeTemporaryPasswordUseCase.class
        );
        AuthController authController = AuthControllerTestBuilder.builder()
            .withChangeTemporaryPasswordUseCase(changeTemporaryPasswordUseCase)
            .build();
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
            .setControllerAdvice(
                new GlobalExceptionHandler(),
                new GlobalResponseBodyAdvice()
            )
            .build();
    }

    @Test
    void shouldReturn200OnSuccessfulTemporaryPasswordChange() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        User user = User.builder()
            .id(userId)
            .organizationId(orgId)
            .role(UserRole.STAFF)
            .email("staff.bob@unihub.edu")
            .passwordHash("encodedHash")
            .isActive(true)
            .isTemporaryPassword(false)
            .lastLoginAt(Instant.now())
            .build();

        ChangeTemporaryPasswordUseCase.ChangeTemporaryPasswordResult result =
            new ChangeTemporaryPasswordUseCase.ChangeTemporaryPasswordResult(
                "mock-access-token",
                "mock-refresh-token",
                "Bearer",
                3600L,
                user
            );

        when(
            changeTemporaryPasswordUseCase.changeTemporaryPassword(
                eq("staff.bob@unihub.edu"),
                eq("TempPassword123!"),
                eq("MyOfficialSecurePassword2026!"),
                any(),
                any()
            )
        ).thenReturn(result);

        String requestBody = """
        {
            "email": "staff.bob@unihub.edu",
            "currentPassword": "TempPassword123!",
            "newPassword": "MyOfficialSecurePassword2026!"
        }
        """;

        mockMvc
            .perform(
                post("/api/v1/auth/password/temporary")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(
                jsonPath("$.message").value(
                    "Password updated successfully. Welcome to UniHub!"
                )
            )
            .andExpect(
                jsonPath("$.data.accessToken").value("mock-access-token")
            )
            .andExpect(
                jsonPath("$.data.refreshToken").value("mock-refresh-token")
            )
            .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.data.expiresIn").value(3600))
            .andExpect(
                jsonPath("$.data.user.email").value("staff.bob@unihub.edu")
            )
            .andExpect(jsonPath("$.data.user.role").value("STAFF"));

        verify(changeTemporaryPasswordUseCase).changeTemporaryPassword(
            eq("staff.bob@unihub.edu"),
            eq("TempPassword123!"),
            eq("MyOfficialSecurePassword2026!"),
            any(),
            any()
        );
    }

    @Test
    void shouldReturn401WhenCurrentPasswordIncorrectOrUserNotFound()
        throws Exception {
        when(
            changeTemporaryPasswordUseCase.changeTemporaryPassword(
                any(),
                any(),
                any(),
                any(),
                any()
            )
        ).thenThrow(
            new UnauthorizedException("Invalid email or current password")
        );

        String requestBody = """
        {
            "email": "staff.bob@unihub.edu",
            "currentPassword": "WrongPassword123!",
            "newPassword": "MyOfficialSecurePassword2026!"
        }
        """;

        mockMvc
            .perform(
                post("/api/v1/auth/password/temporary")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:unauthorized")
            );
    }

    @Test
    void shouldReturn400WhenAccountDoesNotHaveTemporaryPassword()
        throws Exception {
        when(
            changeTemporaryPasswordUseCase.changeTemporaryPassword(
                any(),
                any(),
                any(),
                any(),
                any()
            )
        ).thenThrow(
            new BadRequestException(
                "Account does not have a temporary password."
            )
        );

        String requestBody = """
        {
            "email": "staff.bob@unihub.edu",
            "currentPassword": "PermanentPassword123!",
            "newPassword": "MyOfficialSecurePassword2026!"
        }
        """;

        mockMvc
            .perform(
                post("/api/v1/auth/password/temporary")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:bad-request")
            );
    }

    @Test
    void shouldReturn422WhenNewPasswordMatchesCurrentPassword()
        throws Exception {
        when(
            changeTemporaryPasswordUseCase.changeTemporaryPassword(
                any(),
                any(),
                any(),
                any(),
                any()
            )
        ).thenThrow(
            new ValidationException(
                "newPassword",
                "New password must be different from the temporary password."
            )
        );

        String requestBody = """
        {
            "email": "staff.bob@unihub.edu",
            "currentPassword": "TempPassword123!",
            "newPassword": "TempPassword123!"
        }
        """;

        mockMvc
            .perform(
                post("/api/v1/auth/password/temporary")
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
                    "newPassword: New password must be different from the temporary password."
                )
            );
    }

    @Test
    void shouldReturn422WhenPasswordTooShort() throws Exception {
        String requestBody = """
        {
            "email": "staff.bob@unihub.edu",
            "currentPassword": "TempPassword123!",
            "newPassword": "Weak1!"
        }
        """;

        mockMvc
            .perform(
                post("/api/v1/auth/password/temporary")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            )
            .andExpect(jsonPath("$.error.validationErrors[0]").exists());

        verify(changeTemporaryPasswordUseCase, never()).changeTemporaryPassword(
            any(),
            any(),
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
            "currentPassword": "TempPassword123!",
            "newPassword": "MyOfficialSecurePassword2026!"
        }
        """;

        mockMvc
            .perform(
                post("/api/v1/auth/password/temporary")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            )
            .andExpect(jsonPath("$.error.validationErrors[0]").exists());

        verify(changeTemporaryPasswordUseCase, never()).changeTemporaryPassword(
            any(),
            any(),
            any(),
            any(),
            any()
        );
    }

    @Test
    void shouldReturn422WhenCurrentPasswordBlank() throws Exception {
        String requestBody = """
        {
            "email": "staff.bob@unihub.edu",
            "currentPassword": "",
            "newPassword": "MyOfficialSecurePassword2026!"
        }
        """;

        mockMvc
            .perform(
                post("/api/v1/auth/password/temporary")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            )
            .andExpect(jsonPath("$.error.validationErrors[0]").exists());

        verify(changeTemporaryPasswordUseCase, never()).changeTemporaryPassword(
            any(),
            any(),
            any(),
            any(),
            any()
        );
    }

    @Test
    void shouldTrimWhitespaceFromEmailOnTemporaryPasswordChange()
        throws Exception {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        User user = User.builder()
            .id(userId)
            .organizationId(orgId)
            .role(UserRole.STAFF)
            .email("staff.bob@unihub.edu")
            .passwordHash("encodedHash")
            .isActive(true)
            .isTemporaryPassword(false)
            .lastLoginAt(Instant.now())
            .build();

        ChangeTemporaryPasswordUseCase.ChangeTemporaryPasswordResult result =
            new ChangeTemporaryPasswordUseCase.ChangeTemporaryPasswordResult(
                "mock-access-token",
                "mock-refresh-token",
                "Bearer",
                3600L,
                user
            );

        when(
            changeTemporaryPasswordUseCase.changeTemporaryPassword(
                eq("staff.bob@unihub.edu"),
                eq("TempPassword123!"),
                eq("MyOfficialSecurePassword2026!"),
                any(),
                any()
            )
        ).thenReturn(result);

        String requestBody = """
        {
            "email": "   staff.bob@unihub.edu   ",
            "currentPassword": "TempPassword123!",
            "newPassword": "MyOfficialSecurePassword2026!"
        }
        """;

        mockMvc
            .perform(
                post("/api/v1/auth/password/temporary")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(changeTemporaryPasswordUseCase).changeTemporaryPassword(
            eq("staff.bob@unihub.edu"),
            eq("TempPassword123!"),
            eq("MyOfficialSecurePassword2026!"),
            any(),
            any()
        );
    }

    @Test
    void shouldReturn400OnMalformedJson() throws Exception {
        mockMvc
            .perform(
                post("/api/v1/auth/password/temporary")
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
                post("/api/v1/auth/password/temporary")
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
                post("/api/v1/auth/password/temporary")
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
