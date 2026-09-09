package com.unihub.workshop.iam.presentation.controllers;

import com.unihub.workshop.iam.application.ports.in.GetOrganizationMemberUseCase;
import com.unihub.workshop.iam.application.ports.in.ListMembersQuery;
import com.unihub.workshop.iam.application.ports.in.ListOrganizationMembersUseCase;
import com.unihub.workshop.iam.domain.model.CursorPage;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserRole;
import com.unihub.workshop.shared.security.JwtAuthenticatedPrincipal;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OrganizationUserControllerListTest {

    private MockMvc mockMvc;
    private ListOrganizationMembersUseCase listOrganizationMembersUseCase;

    private static final UUID CALLER_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        listOrganizationMembersUseCase = mock(ListOrganizationMembersUseCase.class);
        OrganizationUserController controller = new OrganizationUserController(
                listOrganizationMembersUseCase, mock(GetOrganizationMemberUseCase.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(), new GlobalResponseBodyAdvice())
                .build();
    }

    private TestingAuthenticationToken createAuth(UUID userId, UUID orgId, String role) {
        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
                userId != null ? userId.toString() : null,
                orgId != null ? orgId.toString() : null,
                "jti-123",
                role,
                UUID.randomUUID().toString(),
                Instant.now(),
                Instant.now().plusSeconds(3600));
        return new TestingAuthenticationToken(principal, null, "ROLE_" + role);
    }

    @Test
    void shouldReturn200WithMembers_whenNoFilters() throws Exception {
        UUID member1Id = UUID.randomUUID();
        UUID member2Id = UUID.randomUUID();
        Instant t1 = Instant.parse("2026-08-01T07:00:00Z");
        Instant t2 = Instant.parse("2026-08-02T08:30:00Z");
        Instant loginTime = Instant.parse("2026-09-08T10:15:00Z");

        User member1 = new User(
                member1Id, ORG_ID, UserRole.STAFF, "alice.staff@techclub.org", "+84901234567",
                "hash", "Alice Nguyen", null, true, false, loginTime, null, t1, t1, null, null);
        User member2 = new User(
                member2Id, ORG_ID, UserRole.STUDENT, "charlie.student@techclub.org", "+84909876543",
                "hash", "Charlie Student", null, true, false, null, null, t2, t2, null, null);

        CursorPage<User> page = new CursorPage<>(
                List.of(member1, member2),
                "cursor-token-123",
                true);

        when(listOrganizationMembersUseCase.execute(any(ListMembersQuery.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/organizations/users")
                .principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Organization members retrieved"))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].id").value(member1Id.toString()))
                .andExpect(jsonPath("$.data.items[0].email").value("alice.staff@techclub.org"))
                .andExpect(jsonPath("$.data.items[0].fullName").value("Alice Nguyen"))
                .andExpect(jsonPath("$.data.items[0].role").value("STAFF"))
                .andExpect(jsonPath("$.data.items[0].phoneNumber").value("+84901234567"))
                .andExpect(jsonPath("$.data.items[0].isActive").value(true))
                .andExpect(jsonPath("$.data.items[0].isTemporaryPassword").value(false))
                .andExpect(jsonPath("$.data.items[0].lastLoginAt").value("2026-09-08T10:15:00Z"))
                .andExpect(jsonPath("$.data.items[0].createdAt").value("2026-08-01T07:00:00Z"))
                .andExpect(jsonPath("$.data.items[1].id").value(member2Id.toString()))
                .andExpect(jsonPath("$.data.items[1].role").value("STUDENT"))
                .andExpect(jsonPath("$.data.cursor.nextCursor").value("cursor-token-123"))
                .andExpect(jsonPath("$.data.cursor.hasMore").value(true));

        verify(listOrganizationMembersUseCase).execute(new ListMembersQuery(
                ORG_ID, CALLER_ID, null, null, null, null, 20));
    }

    @Test
    void shouldReturn200WithFilteredMembers_whenRoleAndIsActiveProvided() throws Exception {
        CursorPage<User> page = new CursorPage<>(List.of(), null, false);
        when(listOrganizationMembersUseCase.execute(any(ListMembersQuery.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/organizations/users?role=STUDENT&isActive=true")
                .principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.cursor.hasMore").value(false))
                .andExpect(jsonPath("$.data.cursor.nextCursor").doesNotExist());

        verify(listOrganizationMembersUseCase).execute(new ListMembersQuery(
                ORG_ID, CALLER_ID, UserRole.STUDENT, true, null, null, 20));
    }

    @Test
    void shouldReturn200WithSearch_whenSearchProvided() throws Exception {
        CursorPage<User> page = new CursorPage<>(List.of(), null, false);
        when(listOrganizationMembersUseCase.execute(any(ListMembersQuery.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/organizations/users?search=alice&cursor=prevCursor&limit=10")
                .principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(listOrganizationMembersUseCase).execute(new ListMembersQuery(
                ORG_ID, CALLER_ID, null, null, "alice", "prevCursor", 10));
    }

    @Test
    void shouldReturn400_whenLimitIsGreaterThan100() throws Exception {
        mockMvc.perform(get("/api/v1/organizations/users?limit=101")
                .principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.type").value("urn:unihub:error:bad-request"));
    }

    @Test
    void shouldReturn400_whenLimitIsLessThan1() throws Exception {
        mockMvc.perform(get("/api/v1/organizations/users?limit=0")
                .principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.type").value("urn:unihub:error:bad-request"));
    }

    @Test
    void shouldReturn400_whenRoleIsInvalid() throws Exception {
        mockMvc.perform(get("/api/v1/organizations/users?role=INVALID_ROLE")
                .principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.type").value("urn:unihub:error:bad-request"));
    }

    @Test
    void shouldReturn400_whenSearchExceeds200Characters() throws Exception {
        String longSearch = "a".repeat(201);
        mockMvc.perform(get("/api/v1/organizations/users?search=" + longSearch)
                .principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.type").value("urn:unihub:error:bad-request"));
    }

    @Test
    void shouldReturn401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/organizations/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.type").value("urn:unihub:error:unauthorized"));
    }

    @Test
    void shouldReturn403_whenRoleIsNotOrganizer() throws Exception {
        mockMvc.perform(get("/api/v1/organizations/users")
                .principal(createAuth(CALLER_ID, ORG_ID, "STAFF")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.type").value("urn:unihub:error:forbidden"));

        mockMvc.perform(get("/api/v1/organizations/users")
                .principal(createAuth(CALLER_ID, ORG_ID, "STUDENT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.type").value("urn:unihub:error:forbidden"));
    }

    @Test
    void shouldReturn403_whenOrgClaimIsMissing() throws Exception {
        mockMvc.perform(get("/api/v1/organizations/users")
                .principal(createAuth(CALLER_ID, null, "ORGANIZER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.type").value("urn:unihub:error:forbidden"));
    }
}
