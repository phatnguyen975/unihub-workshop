package com.unihub.workshop.iam.presentation.controllers;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.unihub.workshop.iam.application.ports.in.GetOrganizationMemberUseCase;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserRole;
import com.unihub.workshop.shared.exception.NotFoundException;
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

class OrganizationControllerDetailTest {

    private MockMvc mockMvc;
    private GetOrganizationMemberUseCase getOrganizationMemberUseCase;

    private static final UUID CALLER_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID TARGET_USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        getOrganizationMemberUseCase = mock(GetOrganizationMemberUseCase.class);
        OrganizationController controller =
            OrganizationControllerTestBuilder.builder()
                .withGetOrganizationMemberUseCase(getOrganizationMemberUseCase)
                .build();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(
                new GlobalExceptionHandler(null),
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
    void shouldReturn200WithMemberDetail_whenMemberExistsInOrganization()
        throws Exception {
        Instant created = Instant.parse("2026-08-01T07:00:00Z");
        Instant updated = Instant.parse("2026-09-08T10:15:00Z");
        Instant login = Instant.parse("2026-09-08T10:15:00Z");

        User member = new User(
            TARGET_USER_ID,
            ORG_ID,
            UserRole.STAFF,
            "alice.staff@techclub.org",
            "+84901234567",
            "hash",
            "Alice Nguyen",
            null,
            true,
            false,
            login,
            null,
            created,
            updated,
            null,
            null
        );

        when(
            getOrganizationMemberUseCase.execute(ORG_ID, TARGET_USER_ID)
        ).thenReturn(member);

        mockMvc
            .perform(
                get(
                    "/api/v1/admin/organizations/users/{userId}",
                    TARGET_USER_ID
                ).principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER"))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Member details retrieved"))
            .andExpect(jsonPath("$.data.id").value(TARGET_USER_ID.toString()))
            .andExpect(
                jsonPath("$.data.organizationId").value(ORG_ID.toString())
            )
            .andExpect(
                jsonPath("$.data.email").value("alice.staff@techclub.org")
            )
            .andExpect(jsonPath("$.data.fullName").value("Alice Nguyen"))
            .andExpect(jsonPath("$.data.role").value("STAFF"))
            .andExpect(jsonPath("$.data.phoneNumber").value("+84901234567"))
            .andExpect(jsonPath("$.data.isActive").value(true))
            .andExpect(jsonPath("$.data.isTemporaryPassword").value(false))
            .andExpect(
                jsonPath("$.data.lastLoginAt").value("2026-09-08T10:15:00Z")
            )
            .andExpect(
                jsonPath("$.data.createdAt").value("2026-08-01T07:00:00Z")
            )
            .andExpect(
                jsonPath("$.data.updatedAt").value("2026-09-08T10:15:00Z")
            );

        verify(getOrganizationMemberUseCase).execute(ORG_ID, TARGET_USER_ID);
    }

    @Test
    void shouldReturn404_whenMemberNotFoundInOrganization() throws Exception {
        when(
            getOrganizationMemberUseCase.execute(ORG_ID, TARGET_USER_ID)
        ).thenThrow(
            new NotFoundException("Member not found in your organization.")
        );

        mockMvc
            .perform(
                get(
                    "/api/v1/admin/organizations/users/{userId}",
                    TARGET_USER_ID
                ).principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER"))
            )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:not-found")
            )
            .andExpect(
                jsonPath("$.error.detail").value(
                    "Member not found in your organization."
                )
            );
    }

    @Test
    void shouldReturn400_whenUserIdIsNotValidUuid() throws Exception {
        mockMvc
            .perform(
                get(
                    "/api/v1/admin/organizations/users/{userId}",
                    "not-a-uuid"
                ).principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER"))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:bad-request")
            );
    }

    @Test
    void shouldReturn401_whenUnauthenticated() throws Exception {
        mockMvc
            .perform(
                get(
                    "/api/v1/admin/organizations/users/{userId}",
                    TARGET_USER_ID
                )
            )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:unauthorized")
            );
    }

    @Test
    void shouldReturn403_whenRoleIsNotOrganizer() throws Exception {
        mockMvc
            .perform(
                get(
                    "/api/v1/admin/organizations/users/{userId}",
                    TARGET_USER_ID
                ).principal(createAuth(CALLER_ID, ORG_ID, "STAFF"))
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:forbidden")
            );
    }

    @Test
    void shouldReturn403_whenOrgClaimIsMissing() throws Exception {
        mockMvc
            .perform(
                get(
                    "/api/v1/admin/organizations/users/{userId}",
                    TARGET_USER_ID
                ).principal(createAuth(CALLER_ID, null, "ORGANIZER"))
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:forbidden")
            );
    }
}
