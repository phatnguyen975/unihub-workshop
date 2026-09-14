package com.unihub.workshop.iam.presentation.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.unihub.workshop.iam.application.ports.in.ChangeUserPasswordUseCase;
import com.unihub.workshop.shared.exception.ServiceUnavailableException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.shared.security.JwtAuthenticatedPrincipal;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
import java.time.Instant;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class UserControllerChangePasswordTest {

    private MockMvc mockMvc;
    private ChangeUserPasswordUseCase changeUserPasswordUseCase;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CURRENT_SESSION_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        changeUserPasswordUseCase = mock(ChangeUserPasswordUseCase.class);
        UserController controller = UserControllerTestBuilder.builder()
            .withChangeUserPasswordUseCase(changeUserPasswordUseCase)
            .build();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(
                new GlobalExceptionHandler(null),
                new GlobalResponseBodyAdvice()
            )
            .build();
    }

    private TestingAuthenticationToken createAuth(String sid) {
        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
            USER_ID.toString(),
            null,
            "jti-123",
            "STUDENT",
            sid,
            Instant.now(),
            Instant.now().plusSeconds(3600)
        );
        return new TestingAuthenticationToken(principal, null, "ROLE_STUDENT");
    }

    @Test
    void shouldReturn200_whenPasswordChangedSuccessfully() throws Exception {
        mockMvc
            .perform(
                patch("/api/v1/users/me/password")
                    .principal(createAuth(CURRENT_SESSION_ID.toString()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "currentPassword": "OldPassword123!",
                            "newPassword": "NewPassword456@"
                        }
                        """
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(
                jsonPath("$.message").value("Password changed successfully.")
            )
            .andExpect(jsonPath("$.data").doesNotExist());

        verify(changeUserPasswordUseCase).changePassword(
            eq(USER_ID),
            eq(CURRENT_SESSION_ID),
            eq("OldPassword123!"),
            eq("NewPassword456@")
        );
    }

    @Test
    void shouldReturn422_whenCurrentPasswordDoesNotMatch() throws Exception {
        doThrow(
            new ValidationException(
                "currentPassword",
                "Current password does not match"
            )
        )
            .when(changeUserPasswordUseCase)
            .changePassword(any(), any(), any(), any());

        mockMvc
            .perform(
                patch("/api/v1/users/me/password")
                    .principal(createAuth(CURRENT_SESSION_ID.toString()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "currentPassword": "WrongPassword123!",
                            "newPassword": "NewPassword456@"
                        }
                        """
                    )
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            )
            .andExpect(
                jsonPath("$.error.validationErrors[0]").value(
                    Matchers.containsString("currentPassword")
                )
            );
    }

    @Test
    void shouldReturn422_whenNewPasswordSameAsCurrentPassword()
        throws Exception {
        doThrow(
            new ValidationException(
                "newPassword",
                "New password must be different from current password"
            )
        )
            .when(changeUserPasswordUseCase)
            .changePassword(any(), any(), any(), any());

        mockMvc
            .perform(
                patch("/api/v1/users/me/password")
                    .principal(createAuth(CURRENT_SESSION_ID.toString()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "currentPassword": "OldPassword123!",
                            "newPassword": "OldPassword123!"
                        }
                        """
                    )
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            )
            .andExpect(
                jsonPath("$.error.validationErrors[0]").value(
                    Matchers.containsString("newPassword")
                )
            );
    }

    @Test
    void shouldReturn422_whenNewPasswordIsWeak() throws Exception {
        doThrow(
            new ValidationException(
                "newPassword",
                "Password must be between 8 and 255 characters"
            )
        )
            .when(changeUserPasswordUseCase)
            .changePassword(any(), any(), any(), any());

        mockMvc
            .perform(
                patch("/api/v1/users/me/password")
                    .principal(createAuth(CURRENT_SESSION_ID.toString()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "currentPassword": "OldPassword123!",
                            "newPassword": "short"
                        }
                        """
                    )
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            )
            .andExpect(
                jsonPath("$.error.validationErrors[0]").value(
                    Matchers.containsString("newPassword")
                )
            );
    }

    @Test
    void shouldReturn422_whenFieldsAreBlank() throws Exception {
        mockMvc
            .perform(
                patch("/api/v1/users/me/password")
                    .principal(createAuth(CURRENT_SESSION_ID.toString()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "currentPassword": "",
                            "newPassword": ""
                        }
                        """
                    )
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            );

        verifyNoInteractions(changeUserPasswordUseCase);
    }

    @Test
    void shouldReturn503WithRetryAfter_whenRedisIsUnavailable()
        throws Exception {
        doThrow(
            new ServiceUnavailableException(
                "urn:unihub:error:service-unavailable",
                "Redis is unavailable"
            )
        )
            .when(changeUserPasswordUseCase)
            .changePassword(any(), any(), any(), any());

        mockMvc
            .perform(
                patch("/api/v1/users/me/password")
                    .principal(createAuth(CURRENT_SESSION_ID.toString()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "currentPassword": "OldPassword123!",
                            "newPassword": "NewPassword456@"
                        }
                        """
                    )
            )
            .andExpect(status().isServiceUnavailable())
            .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value(
                    "urn:unihub:error:service-unavailable"
                )
            );
    }

    @Test
    void shouldReturn400_whenSidMissingFromToken() throws Exception {
        mockMvc
            .perform(
                patch("/api/v1/users/me/password")
                    .principal(createAuth(null))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "currentPassword": "OldPassword123!",
                            "newPassword": "NewPassword456@"
                        }
                        """
                    )
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:bad-request")
            );

        verifyNoInteractions(changeUserPasswordUseCase);
    }

    @Test
    void shouldReturn401_whenUnauthenticated() throws Exception {
        mockMvc
            .perform(
                patch("/api/v1/users/me/password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "currentPassword": "OldPassword123!",
                            "newPassword": "NewPassword456@"
                        }
                        """
                    )
            )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:unauthorized")
            );

        verifyNoInteractions(changeUserPasswordUseCase);
    }
}
