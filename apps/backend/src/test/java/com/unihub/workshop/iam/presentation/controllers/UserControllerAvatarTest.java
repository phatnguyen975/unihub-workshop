package com.unihub.workshop.iam.presentation.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unihub.workshop.iam.application.ports.in.UploadUserAvatarUseCase;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserRole;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.shared.security.JwtAuthenticatedPrincipal;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class UserControllerAvatarTest {

    private MockMvc mockMvc;
    private UploadUserAvatarUseCase uploadUserAvatarUseCase;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID STUDENT_RECORD_ID = UUID.randomUUID();

    private static final byte[] VALID_PNG_BYTES = new byte[] {
        (byte) 0x89,
        0x50,
        0x4E,
        0x47,
        0x0D,
        0x0A,
        0x1A,
        0x0A,
        0x00,
        0x00,
        0x00,
        0x0D,
    };

    @BeforeEach
    void setUp() {
        uploadUserAvatarUseCase = mock(UploadUserAvatarUseCase.class);
        UserController controller = UserControllerTestBuilder.builder()
            .withUploadUserAvatarUseCase(uploadUserAvatarUseCase)
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
            "sid-123",
            Instant.now(),
            Instant.now().plusSeconds(3600)
        );
        return new TestingAuthenticationToken(principal, null, "ROLE_STUDENT");
    }

    @Test
    @DisplayName(
        "Returns 200 OK with full updated profile on valid avatar upload"
    )
    void shouldReturn200_whenAvatarUploadedSuccessfully() throws Exception {
        String avatarUrl =
            "http://localhost:9000/unihub-media/users/avatars/avatar-123.png";
        User updated = new User(
            USER_ID,
            ORG_ID,
            UserRole.STUDENT,
            "student@university.edu",
            "+84912345678",
            "hash",
            "Alice Smith",
            avatarUrl,
            true,
            false,
            Instant.now(),
            STUDENT_RECORD_ID,
            Instant.now().minusSeconds(3600),
            Instant.now(),
            null,
            USER_ID
        );

        when(
            uploadUserAvatarUseCase.uploadAvatar(eq(USER_ID), any())
        ).thenReturn(updated);

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "avatar.png",
            "image/png",
            VALID_PNG_BYTES
        );

        mockMvc
            .perform(
                multipart("/api/v1/users/me/avatar")
                    .file(file)
                    .principal(createAuth())
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(
                jsonPath("$.message").value("Avatar updated successfully.")
            )
            .andExpect(jsonPath("$.data.id").value(USER_ID.toString()))
            .andExpect(jsonPath("$.data.avatarUrl").value(avatarUrl))
            .andExpect(jsonPath("$.data.fullName").value("Alice Smith"));
    }

    @Test
    @DisplayName("Returns 422 Unprocessable Content when file validation fails")
    void shouldReturn422_whenFileValidationFails() throws Exception {
        when(
            uploadUserAvatarUseCase.uploadAvatar(eq(USER_ID), any())
        ).thenThrow(
            new ValidationException(
                "file",
                "File size exceeds the 5MB maximum limit."
            )
        );

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "huge.png",
            "image/png",
            VALID_PNG_BYTES
        );

        mockMvc
            .perform(
                multipart("/api/v1/users/me/avatar")
                    .file(file)
                    .principal(createAuth())
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            );
    }

    @Test
    @DisplayName(
        "Returns 401 Unauthorized when no authentication token is present"
    )
    void shouldReturn401_whenUnauthenticated() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "avatar.png",
            "image/png",
            VALID_PNG_BYTES
        );

        mockMvc
            .perform(multipart("/api/v1/users/me/avatar").file(file))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:unauthorized")
            );
    }
}
