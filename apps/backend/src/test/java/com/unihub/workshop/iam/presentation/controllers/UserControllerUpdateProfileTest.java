package com.unihub.workshop.iam.presentation.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unihub.workshop.iam.application.ports.in.UpdateUserProfileCommand;
import com.unihub.workshop.iam.application.ports.in.UpdateUserProfileUseCase;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserRole;
import com.unihub.workshop.shared.exception.UnauthorizedException;
import com.unihub.workshop.shared.security.JwtAuthenticatedPrincipal;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
import java.time.Instant;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class UserControllerUpdateProfileTest {

    private MockMvc mockMvc;
    private UpdateUserProfileUseCase updateUserProfileUseCase;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID CURRENT_SESSION_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        updateUserProfileUseCase = mock(UpdateUserProfileUseCase.class);
        UserController controller = UserControllerTestBuilder.builder()
            .withUpdateUserProfileUseCase(updateUserProfileUseCase)
            .build();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(
                new GlobalExceptionHandler(),
                new GlobalResponseBodyAdvice()
            )
            .build();
    }

    private TestingAuthenticationToken createAuth() {
        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
            USER_ID.toString(),
            ORG_ID.toString(),
            "jti-123",
            "STUDENT",
            CURRENT_SESSION_ID.toString(),
            Instant.now(),
            Instant.now().plusSeconds(3600)
        );
        return new TestingAuthenticationToken(principal, null, "ROLE_STUDENT");
    }

    @Test
    void shouldReturn200_whenPartialUpdateFullNameOnly() throws Exception {
        User updatedUser = new User(
            USER_ID,
            ORG_ID,
            UserRole.STUDENT,
            "alice.nguyen@student.hcmut.edu.vn",
            "+84912345678",
            "argon2hash",
            "Alice Thu Nguyen",
            "https://cdn.unihub.vn/avatars/alice.jpg",
            true,
            false,
            Instant.parse("2026-09-07T14:30:00Z"),
            UUID.randomUUID(),
            Instant.now(),
            Instant.now(),
            null,
            USER_ID
        );

        when(
            updateUserProfileUseCase.updateProfile(
                eq(USER_ID),
                any(UpdateUserProfileCommand.class)
            )
        ).thenReturn(updatedUser);

        mockMvc
            .perform(
                patch("/api/v1/users/me")
                    .principal(createAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "fullName": "Alice Thu Nguyen"
                        }
                        """
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(
                jsonPath("$.message").value("Profile updated successfully.")
            )
            .andExpect(jsonPath("$.data.fullName").value("Alice Thu Nguyen"))
            .andExpect(jsonPath("$.data.phoneNumber").value("+84912345678"))
            .andExpect(
                jsonPath("$.data.organizationId").value(ORG_ID.toString())
            );
    }

    @Test
    void shouldReturn200_whenClearingPhoneNumber() throws Exception {
        User updatedUser = new User(
            USER_ID,
            ORG_ID,
            UserRole.STUDENT,
            "alice.nguyen@student.hcmut.edu.vn",
            null,
            "argon2hash",
            "Alice Nguyen",
            "https://cdn.unihub.vn/avatars/alice.jpg",
            true,
            false,
            null,
            null,
            Instant.now(),
            Instant.now(),
            null,
            USER_ID
        );

        when(
            updateUserProfileUseCase.updateProfile(
                eq(USER_ID),
                any(UpdateUserProfileCommand.class)
            )
        ).thenReturn(updatedUser);

        mockMvc
            .perform(
                patch("/api/v1/users/me")
                    .principal(createAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "phoneNumber": null
                        }
                        """
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.phoneNumber").doesNotExist());
    }

    @Test
    void shouldReturn422_whenPhoneNumberIsInvalid() throws Exception {
        mockMvc
            .perform(
                patch("/api/v1/users/me")
                    .principal(createAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "phoneNumber": "not-a-phone"
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
                    Matchers.containsString("phoneNumber")
                )
            );
    }

    @Test
    void shouldReturn422_whenFullNameIsBlank() throws Exception {
        mockMvc
            .perform(
                patch("/api/v1/users/me")
                    .principal(createAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "fullName": "   "
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
                    Matchers.containsString("fullName")
                )
            );
    }

    @Test
    void shouldReturn422_whenAvatarUrlIsInvalid() throws Exception {
        mockMvc
            .perform(
                patch("/api/v1/users/me")
                    .principal(createAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "avatarUrl": "https://"
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
                    Matchers.containsString("avatarUrl")
                )
            );
    }

    @Test
    void shouldReturn422_whenImmutableFieldAttempted() throws Exception {
        mockMvc
            .perform(
                patch("/api/v1/users/me")
                    .principal(createAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "email": "hacker@evil.com"
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
                    Matchers.containsString("email")
                )
            );
    }

    @Test
    void shouldReturn422_whenEmptyBody() throws Exception {
        mockMvc
            .perform(
                patch("/api/v1/users/me")
                    .principal(createAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {}
                        """
                    )
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            );
    }

    @Test
    void shouldReturn401_whenUnauthenticated() throws Exception {
        mockMvc
            .perform(
                patch("/api/v1/users/me")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "fullName": "Alice"
                        }
                        """
                    )
            )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:unauthorized")
            );
    }

    @Test
    void shouldReturn200_whenClearingAvatarUrl() throws Exception {
        User updatedUser = new User(
            USER_ID,
            ORG_ID,
            UserRole.STUDENT,
            "alice.nguyen@student.hcmut.edu.vn",
            "+84912345678",
            "argon2hash",
            "Alice Nguyen",
            null,
            true,
            false,
            null,
            null,
            Instant.now(),
            Instant.now(),
            null,
            USER_ID
        );

        when(
            updateUserProfileUseCase.updateProfile(
                eq(USER_ID),
                any(UpdateUserProfileCommand.class)
            )
        ).thenReturn(updatedUser);

        mockMvc
            .perform(
                patch("/api/v1/users/me")
                    .principal(createAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "avatarUrl": null
                        }
                        """
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.avatarUrl").doesNotExist());
    }

    @Test
    void shouldReturn200_whenUpdatingAllThreeFields() throws Exception {
        User updatedUser = new User(
            USER_ID,
            ORG_ID,
            UserRole.STUDENT,
            "alice.nguyen@student.hcmut.edu.vn",
            "+84987654321",
            "argon2hash",
            "Alice Thu Nguyen",
            "https://cdn.unihub.vn/avatars/new.jpg",
            true,
            false,
            null,
            null,
            Instant.now(),
            Instant.now(),
            null,
            USER_ID
        );

        when(
            updateUserProfileUseCase.updateProfile(
                eq(USER_ID),
                any(UpdateUserProfileCommand.class)
            )
        ).thenReturn(updatedUser);

        mockMvc
            .perform(
                patch("/api/v1/users/me")
                    .principal(createAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "fullName": "Alice Thu Nguyen",
                            "phoneNumber": "+84987654321",
                            "avatarUrl": "https://cdn.unihub.vn/avatars/new.jpg"
                        }
                        """
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.fullName").value("Alice Thu Nguyen"))
            .andExpect(jsonPath("$.data.phoneNumber").value("+84987654321"))
            .andExpect(
                jsonPath("$.data.avatarUrl").value(
                    "https://cdn.unihub.vn/avatars/new.jpg"
                )
            );
    }

    @Test
    void shouldReturn422_whenFullNameExceeds200Characters() throws Exception {
        String longName = "A".repeat(201);
        mockMvc
            .perform(
                patch("/api/v1/users/me")
                    .principal(createAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            """
                            {
                                "fullName": "%s"
                            }
                            """,
                            longName
                        )
                    )
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            )
            .andExpect(
                jsonPath("$.error.validationErrors[0]").value(
                    Matchers.containsString("fullName")
                )
            );
    }

    @Test
    void shouldReturn422_whenFullNameIsNull() throws Exception {
        mockMvc
            .perform(
                patch("/api/v1/users/me")
                    .principal(createAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "fullName": null
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
                    Matchers.containsString(
                        "fullName: Full name may not be set to null"
                    )
                )
            );
    }

    @Test
    void shouldReturn422_whenPhoneNumberExceeds20Characters() throws Exception {
        mockMvc
            .perform(
                patch("/api/v1/users/me")
                    .principal(createAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "phoneNumber": "+123456789012345678901"
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
                    Matchers.containsString("phoneNumber")
                )
            );
    }

    @Test
    void shouldReturn422_whenAvatarUrlExceeds2048Characters() throws Exception {
        String longUrl = "https://cdn.unihub.vn/" + "a".repeat(2030);
        mockMvc
            .perform(
                patch("/api/v1/users/me")
                    .principal(createAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            """
                            {
                                "avatarUrl": "%s"
                            }
                            """,
                            longUrl
                        )
                    )
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            )
            .andExpect(
                jsonPath("$.error.validationErrors[0]").value(
                    Matchers.containsString("avatarUrl")
                )
            );
    }

    @Test
    void shouldReturn400_whenRequestBodyIsMissing() throws Exception {
        mockMvc
            .perform(
                patch("/api/v1/users/me")
                    .principal(createAuth())
                    .contentType(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:bad-request")
            );
    }

    @Test
    void shouldReturn401_whenUserNotFoundInRepository() throws Exception {
        when(
            updateUserProfileUseCase.updateProfile(
                eq(USER_ID),
                any(UpdateUserProfileCommand.class)
            )
        ).thenThrow(new UnauthorizedException("User not found"));

        mockMvc
            .perform(
                patch("/api/v1/users/me")
                    .principal(createAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "fullName": "Alice"
                        }
                        """
                    )
            )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:unauthorized")
            );
    }

    @Test
    void shouldReturn401_whenAccountIsDeactivated() throws Exception {
        when(
            updateUserProfileUseCase.updateProfile(
                eq(USER_ID),
                any(UpdateUserProfileCommand.class)
            )
        ).thenThrow(new UnauthorizedException("Account is deactivated"));

        mockMvc
            .perform(
                patch("/api/v1/users/me")
                    .principal(createAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "fullName": "Alice"
                        }
                        """
                    )
            )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:unauthorized")
            );
    }

    @Test
    void shouldReturn422_whenBothImmutableAndInvalidMutableFieldsPresent()
        throws Exception {
        mockMvc
            .perform(
                patch("/api/v1/users/me")
                    .principal(createAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "email": "hacker@evil.com",
                            "phoneNumber": "not-a-phone"
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
                jsonPath("$.error.validationErrors", Matchers.hasSize(2))
            )
            .andExpect(
                jsonPath(
                    "$.error.validationErrors",
                    Matchers.hasItem(Matchers.containsString("email"))
                )
            )
            .andExpect(
                jsonPath(
                    "$.error.validationErrors",
                    Matchers.hasItem(Matchers.containsString("phoneNumber"))
                )
            );
    }
}
