package com.unihub.workshop.iam.presentation.controllers;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unihub.workshop.iam.application.ports.in.GetUserProfileUseCase;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserRole;
import com.unihub.workshop.shared.security.JwtAuthenticatedPrincipal;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class UserControllerGetProfileTest {

    private MockMvc mockMvc;
    private GetUserProfileUseCase getUserProfileUseCase;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CURRENT_SESSION_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        getUserProfileUseCase = mock(GetUserProfileUseCase.class);
        UserController controller = UserControllerTestBuilder.builder()
            .withGetUserProfileUseCase(getUserProfileUseCase)
            .build();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(
                new GlobalExceptionHandler(null),
                new GlobalResponseBodyAdvice()
            )
            .build();
    }

    private TestingAuthenticationToken createAuth(UUID userId, String role) {
        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
            userId.toString(),
            null,
            "jti-123",
            role,
            CURRENT_SESSION_ID.toString(),
            Instant.now(),
            Instant.now().plusSeconds(3600)
        );
        return new TestingAuthenticationToken(principal, null, "ROLE_" + role);
    }

    @Test
    void shouldReturn200WithProfile_whenStudentUser() throws Exception {
        UUID studentRecordId = UUID.randomUUID();
        User student = new User(
            USER_ID,
            null,
            UserRole.STUDENT,
            "alice.nguyen@student.hcmut.edu.vn",
            "+84912345678",
            "argon2hash",
            "Alice Nguyen",
            "https://cdn.unihub.vn/avatars/alice.jpg",
            true,
            false,
            Instant.parse("2026-09-07T14:30:00Z"),
            studentRecordId,
            Instant.now(),
            Instant.now(),
            null,
            null
        );

        when(getUserProfileUseCase.getProfile(USER_ID)).thenReturn(student);

        mockMvc
            .perform(
                get("/api/v1/users/me").principal(
                    createAuth(USER_ID, "STUDENT")
                )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(
                jsonPath("$.message").value("Profile retrieved successfully.")
            )
            .andExpect(jsonPath("$.data.id").value(USER_ID.toString()))
            .andExpect(
                jsonPath("$.data.email").value(
                    "alice.nguyen@student.hcmut.edu.vn"
                )
            )
            .andExpect(jsonPath("$.data.fullName").value("Alice Nguyen"))
            .andExpect(jsonPath("$.data.role").value("STUDENT"))
            .andExpect(jsonPath("$.data.phoneNumber").value("+84912345678"))
            .andExpect(
                jsonPath("$.data.avatarUrl").value(
                    "https://cdn.unihub.vn/avatars/alice.jpg"
                )
            )
            .andExpect(
                jsonPath("$.data.lastLoginAt").value("2026-09-07T14:30:00Z")
            )
            .andExpect(
                jsonPath("$.data.studentRecordId").value(
                    studentRecordId.toString()
                )
            )
            .andExpect(jsonPath("$.data.organizationId").doesNotExist());
    }

    @Test
    void shouldReturn200WithProfile_whenOrganizerUser() throws Exception {
        UUID orgId = UUID.randomUUID();
        User organizer = new User(
            USER_ID,
            orgId,
            UserRole.ORGANIZER,
            "organizer@org.unihub.vn",
            null,
            "argon2hash",
            "Org Lead",
            null,
            true,
            false,
            null,
            null,
            Instant.now(),
            Instant.now(),
            null,
            null
        );

        when(getUserProfileUseCase.getProfile(USER_ID)).thenReturn(organizer);

        mockMvc
            .perform(
                get("/api/v1/users/me").principal(
                    createAuth(USER_ID, "ORGANIZER")
                )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(USER_ID.toString()))
            .andExpect(
                jsonPath("$.data.email").value("organizer@org.unihub.vn")
            )
            .andExpect(jsonPath("$.data.role").value("ORGANIZER"))
            .andExpect(
                jsonPath("$.data.organizationId").value(orgId.toString())
            )
            .andExpect(jsonPath("$.data.studentRecordId").doesNotExist())
            .andExpect(jsonPath("$.data.phoneNumber").doesNotExist())
            .andExpect(jsonPath("$.data.avatarUrl").doesNotExist())
            .andExpect(jsonPath("$.data.lastLoginAt").doesNotExist());
    }

    @Test
    void shouldReturn200WithProfile_whenStaffUser() throws Exception {
        User staff = new User(
            USER_ID,
            null,
            UserRole.STAFF,
            "staff@event.unihub.vn",
            "+84987654321",
            "argon2hash",
            "Staff Member",
            null,
            true,
            false,
            null,
            null,
            Instant.now(),
            Instant.now(),
            null,
            null
        );

        when(getUserProfileUseCase.getProfile(USER_ID)).thenReturn(staff);

        mockMvc
            .perform(
                get("/api/v1/users/me").principal(createAuth(USER_ID, "STAFF"))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(USER_ID.toString()))
            .andExpect(jsonPath("$.data.role").value("STAFF"))
            .andExpect(jsonPath("$.data.organizationId").doesNotExist())
            .andExpect(jsonPath("$.data.studentRecordId").doesNotExist());
    }

    @Test
    void shouldReturn401_whenUnauthenticated() throws Exception {
        mockMvc
            .perform(get("/api/v1/users/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:unauthorized")
            );
    }
}
