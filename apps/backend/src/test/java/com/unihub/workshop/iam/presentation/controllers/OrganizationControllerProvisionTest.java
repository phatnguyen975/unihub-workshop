package com.unihub.workshop.iam.presentation.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unihub.workshop.iam.application.ports.in.ProvisionUserCommand;
import com.unihub.workshop.iam.application.ports.in.ProvisionUserUseCase;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserRole;
import com.unihub.workshop.iam.presentation.dto.ProvisionUserRequest;
import com.unihub.workshop.shared.exception.ConflictException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.shared.security.JwtAuthenticatedPrincipal;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class OrganizationControllerProvisionTest {

    private MockMvc mockMvc;
    private ProvisionUserUseCase provisionUserUseCase;
    private ObjectMapper objectMapper;

    private static final UUID CALLER_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        provisionUserUseCase = mock(ProvisionUserUseCase.class);
        objectMapper = new ObjectMapper();

        OrganizationController controller =
            OrganizationControllerTestBuilder.builder()
                .withProvisionUserUseCase(provisionUserUseCase)
                .build();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(
                new GlobalExceptionHandler(),
                new GlobalResponseBodyAdvice()
            )
            .build();
    }

    private TestingAuthenticationToken createAuth(
        UUID userId,
        UUID orgId,
        String role
    ) {
        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
            userId != null ? userId.toString() : null,
            orgId != null ? orgId.toString() : null,
            "jti-123",
            role,
            UUID.randomUUID().toString(),
            Instant.now(),
            Instant.now().plusSeconds(3600)
        );
        return new TestingAuthenticationToken(principal, null, "ROLE_" + role);
    }

    @Test
    void shouldReturn201WithProvisionedMember_whenPayloadIsValid()
        throws Exception {
        UUID newUserId = UUID.randomUUID();
        ProvisionUserRequest request = new ProvisionUserRequest(
            "carol.staff@techclub.org",
            "Carol Danvers",
            "+84912345678",
            "STAFF"
        );

        User createdUser = new User(
            newUserId,
            ORG_ID,
            UserRole.STAFF,
            "carol.staff@techclub.org",
            "+84912345678",
            "argon2hash",
            "Carol Danvers",
            null,
            true,
            true,
            null,
            null,
            Instant.now(),
            Instant.now(),
            CALLER_ID,
            CALLER_ID
        );

        when(
            provisionUserUseCase.execute(any(ProvisionUserCommand.class))
        ).thenReturn(createdUser);

        mockMvc
            .perform(
                post("/api/v1/admin/organizations/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER"))
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(
                jsonPath("$.message").value(
                    "User provisioned successfully. Temporary credentials sent via email."
                )
            )
            .andExpect(jsonPath("$.data.userId").value(newUserId.toString()))
            .andExpect(
                jsonPath("$.data.email").value("carol.staff@techclub.org")
            )
            .andExpect(jsonPath("$.data.fullName").value("Carol Danvers"))
            .andExpect(jsonPath("$.data.role").value("STAFF"))
            .andExpect(
                jsonPath("$.data.organizationId").value(ORG_ID.toString())
            );

        verify(provisionUserUseCase).execute(
            new ProvisionUserCommand(
                ORG_ID,
                CALLER_ID,
                "carol.staff@techclub.org",
                "Carol Danvers",
                "+84912345678",
                UserRole.STAFF
            )
        );
    }

    @Test
    void shouldReturn422_whenRoleIsStudent() throws Exception {
        ProvisionUserRequest request = new ProvisionUserRequest(
            "student@techclub.org",
            "Student User",
            null,
            "STUDENT"
        );

        when(
            provisionUserUseCase.execute(any(ProvisionUserCommand.class))
        ).thenThrow(
            new ValidationException(
                "urn:unihub:error:student-provisioning-forbidden",
                "The STUDENT role cannot be provisioned by an organization. Students must self-register.",
                List.of("role: STUDENT role cannot be provisioned")
            )
        );

        mockMvc
            .perform(
                post("/api/v1/admin/organizations/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER"))
            )
            .andExpect(status().is(422))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value(
                    "urn:unihub:error:student-provisioning-forbidden"
                )
            )
            .andExpect(
                jsonPath("$.error.detail").value(
                    "The STUDENT role cannot be provisioned by an organization. Students must self-register."
                )
            );
    }

    @Test
    void shouldReturn409_whenEmailAlreadyExists() throws Exception {
        ProvisionUserRequest request = new ProvisionUserRequest(
            "existing@techclub.org",
            "Existing User",
            null,
            "STAFF"
        );

        when(
            provisionUserUseCase.execute(any(ProvisionUserCommand.class))
        ).thenThrow(
            new ConflictException("An account with this email already exists.")
        );

        mockMvc
            .perform(
                post("/api/v1/admin/organizations/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER"))
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:conflict")
            )
            .andExpect(
                jsonPath("$.error.detail").value(
                    "An account with this email already exists."
                )
            );
    }

    @Test
    void shouldReturn422_whenEmailIsInvalid() throws Exception {
        ProvisionUserRequest request = new ProvisionUserRequest(
            "not-an-email",
            "Valid Name",
            null,
            "STAFF"
        );

        mockMvc
            .perform(
                post("/api/v1/admin/organizations/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER"))
            )
            .andExpect(status().is(422))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            );
    }

    @Test
    void shouldReturn422_whenFullNameIsBlank() throws Exception {
        ProvisionUserRequest request = new ProvisionUserRequest(
            "user@techclub.org",
            "   ",
            null,
            "STAFF"
        );

        mockMvc
            .perform(
                post("/api/v1/admin/organizations/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER"))
            )
            .andExpect(status().is(422))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            );
    }

    @Test
    void shouldReturn422_whenRoleIsInvalidEnum() throws Exception {
        String payload = """
        {
            "email": "user@techclub.org",
            "fullName": "Test User",
            "role": "INVALID_ROLE"
        }
        """;

        mockMvc
            .perform(
                post("/api/v1/admin/organizations/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
                    .principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER"))
            )
            .andExpect(status().is(422))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            );
    }

    @Test
    void shouldReturn401_whenUnauthenticated() throws Exception {
        ProvisionUserRequest request = new ProvisionUserRequest(
            "user@techclub.org",
            "Test User",
            null,
            "STAFF"
        );

        mockMvc
            .perform(
                post("/api/v1/admin/organizations/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:unauthorized")
            );
    }

    @Test
    void shouldReturn403_whenRoleIsNotOrganizer() throws Exception {
        ProvisionUserRequest request = new ProvisionUserRequest(
            "user@techclub.org",
            "Test User",
            null,
            "STAFF"
        );

        mockMvc
            .perform(
                post("/api/v1/admin/organizations/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .principal(createAuth(CALLER_ID, ORG_ID, "STAFF"))
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:forbidden")
            );
    }

    @Test
    void shouldReturn403_whenOrgClaimIsMissing() throws Exception {
        ProvisionUserRequest request = new ProvisionUserRequest(
            "user@techclub.org",
            "Test User",
            null,
            "STAFF"
        );

        mockMvc
            .perform(
                post("/api/v1/admin/organizations/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .principal(createAuth(CALLER_ID, null, "ORGANIZER"))
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:forbidden")
            );
    }
}
