package com.unihub.workshop.iam.presentation.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unihub.workshop.iam.application.ports.in.UpdateMemberStatusCommand;
import com.unihub.workshop.iam.application.ports.in.UpdateMemberStatusUseCase;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserRole;
import com.unihub.workshop.iam.presentation.dto.UpdateMemberStatusRequest;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.exception.ServiceUnavailableException;
import com.unihub.workshop.shared.security.JwtAuthenticatedPrincipal;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class OrganizationControllerStatusTest {

    private MockMvc mockMvc;
    private UpdateMemberStatusUseCase updateMemberStatusUseCase;
    private ObjectMapper objectMapper;

    private static final UUID CALLER_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID TARGET_USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        updateMemberStatusUseCase = mock(UpdateMemberStatusUseCase.class);
        objectMapper = new ObjectMapper();

        OrganizationController controller =
            OrganizationControllerTestBuilder.builder()
                .withUpdateMemberStatusUseCase(updateMemberStatusUseCase)
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

    private User createSampleUser(UUID id, UUID orgId, boolean isActive) {
        Instant now = Instant.now();
        return new User(
            id,
            orgId,
            UserRole.STAFF,
            "staff@example.com",
            "+84901234567",
            "hash123",
            "Staff Member",
            null,
            isActive,
            false,
            null,
            null,
            now,
            now,
            CALLER_ID,
            CALLER_ID
        );
    }

    @Test
    @DisplayName(
        "200 OK: Ban member updates is_active=false and returns updated status response"
    )
    void shouldReturn200AndUpdatedStatus_whenBanRequestValid()
        throws Exception {
        User updated = createSampleUser(TARGET_USER_ID, ORG_ID, false);
        when(
            updateMemberStatusUseCase.execute(
                any(UpdateMemberStatusCommand.class)
            )
        ).thenReturn(updated);

        UpdateMemberStatusRequest request = new UpdateMemberStatusRequest(
            false
        );

        mockMvc
            .perform(
                patch(
                    "/api/v1/admin/organizations/users/{userId}/status",
                    TARGET_USER_ID
                )
                    .principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(
                jsonPath("$.message").value(
                    "Member account status updated successfully."
                )
            )
            .andExpect(
                jsonPath("$.data.userId").value(TARGET_USER_ID.toString())
            )
            .andExpect(jsonPath("$.data.isActive").value(false))
            .andExpect(jsonPath("$.data.updatedAt").isNotEmpty());
    }

    @Test
    @DisplayName(
        "200 OK: Unban member updates is_active=true and returns updated status response"
    )
    void shouldReturn200AndUpdatedStatus_whenUnbanRequestValid()
        throws Exception {
        User updated = createSampleUser(TARGET_USER_ID, ORG_ID, true);
        when(
            updateMemberStatusUseCase.execute(
                any(UpdateMemberStatusCommand.class)
            )
        ).thenReturn(updated);

        UpdateMemberStatusRequest request = new UpdateMemberStatusRequest(true);

        mockMvc
            .perform(
                patch(
                    "/api/v1/admin/organizations/users/{userId}/status",
                    TARGET_USER_ID
                )
                    .principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(
                jsonPath("$.message").value(
                    "Member account status updated successfully."
                )
            )
            .andExpect(
                jsonPath("$.data.userId").value(TARGET_USER_ID.toString())
            )
            .andExpect(jsonPath("$.data.isActive").value(true));
    }

    @Test
    @DisplayName(
        "422 Unprocessable Content: missing or null isActive triggers validation error"
    )
    void shouldReturn422_whenIsActiveMissingOrNull() throws Exception {
        mockMvc
            .perform(
                patch(
                    "/api/v1/admin/organizations/users/{userId}/status",
                    TARGET_USER_ID
                )
                    .principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
            )
            .andExpect(status().is(422))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            );
    }

    @Test
    @DisplayName("400 Bad Request: invalid UUID in path parameter")
    void shouldReturn400_whenUserIdIsNotAValidUUID() throws Exception {
        UpdateMemberStatusRequest request = new UpdateMemberStatusRequest(
            false
        );

        mockMvc
            .perform(
                patch(
                    "/api/v1/admin/organizations/users/{userId}/status",
                    "not-a-valid-uuid"
                )
                    .principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName(
        "403 Forbidden: self status modification returns 403 with self-status-modification-forbidden error type"
    )
    void shouldReturn403_whenSelfStatusModificationForbidden()
        throws Exception {
        when(
            updateMemberStatusUseCase.execute(
                any(UpdateMemberStatusCommand.class)
            )
        ).thenThrow(
            new ForbiddenException(
                "urn:unihub:error:self-status-modification-forbidden",
                "Organizers cannot modify their own active status."
            )
        );

        UpdateMemberStatusRequest request = new UpdateMemberStatusRequest(
            false
        );

        mockMvc
            .perform(
                patch(
                    "/api/v1/admin/organizations/users/{userId}/status",
                    CALLER_ID
                )
                    .principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value(
                    "urn:unihub:error:self-status-modification-forbidden"
                )
            )
            .andExpect(
                jsonPath("$.error.detail").value(
                    "Organizers cannot modify their own active status."
                )
            );
    }

    @Test
    @DisplayName("404 Not Found: target user not found in caller organization")
    void shouldReturn404_whenMemberNotFoundInOrganization() throws Exception {
        when(
            updateMemberStatusUseCase.execute(
                any(UpdateMemberStatusCommand.class)
            )
        ).thenThrow(
            new NotFoundException("Member not found in your organization.")
        );

        UpdateMemberStatusRequest request = new UpdateMemberStatusRequest(
            false
        );

        mockMvc
            .perform(
                patch(
                    "/api/v1/admin/organizations/users/{userId}/status",
                    TARGET_USER_ID
                )
                    .principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
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
    @DisplayName(
        "503 Service Unavailable: Redis failure returns 503 and Retry-After: 30 header"
    )
    void shouldReturn503WithRetryAfterHeader_whenRedisFailsDuringBan()
        throws Exception {
        when(
            updateMemberStatusUseCase.execute(
                any(UpdateMemberStatusCommand.class)
            )
        ).thenThrow(
            new ServiceUnavailableException(
                "urn:unihub:error:service-unavailable",
                "Session revocation service is temporarily unavailable. Member status was not changed.",
                30
            )
        );

        UpdateMemberStatusRequest request = new UpdateMemberStatusRequest(
            false
        );

        mockMvc
            .perform(
                patch(
                    "/api/v1/admin/organizations/users/{userId}/status",
                    TARGET_USER_ID
                )
                    .principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
            .andExpect(status().isServiceUnavailable())
            .andExpect(header().string(HttpHeaders.RETRY_AFTER, "30"))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value(
                    "urn:unihub:error:service-unavailable"
                )
            )
            .andExpect(
                jsonPath("$.error.detail").value(
                    "Session revocation service is temporarily unavailable. Member status was not changed."
                )
            );
    }

    @Test
    @DisplayName("403 Forbidden: caller with non-ORGANIZER role returns 403")
    void shouldReturn403_whenCallerIsNotOrganizer() throws Exception {
        UpdateMemberStatusRequest request = new UpdateMemberStatusRequest(
            false
        );

        mockMvc
            .perform(
                patch(
                    "/api/v1/admin/organizations/users/{userId}/status",
                    TARGET_USER_ID
                )
                    .principal(createAuth(CALLER_ID, ORG_ID, "STAFF"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:forbidden")
            );
    }

    @Test
    @DisplayName("401 Unauthorized: unauthenticated request returns 401")
    void shouldReturn401_whenUnauthenticated() throws Exception {
        UpdateMemberStatusRequest request = new UpdateMemberStatusRequest(
            false
        );

        mockMvc
            .perform(
                patch(
                    "/api/v1/admin/organizations/users/{userId}/status",
                    TARGET_USER_ID
                )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:unauthorized")
            );
    }
}
