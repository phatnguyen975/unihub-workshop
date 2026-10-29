package com.unihub.workshop.workshops.presentation.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.shared.pagination.PageResponse;
import com.unihub.workshop.shared.security.JwtAuthenticatedPrincipal;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
import com.unihub.workshop.workshops.application.ports.in.ListStaffAssignedWorkshopsQuery;
import com.unihub.workshop.workshops.application.ports.in.ListStaffAssignedWorkshopsUseCase;
import com.unihub.workshop.workshops.domain.model.StaffAssignedWorkshop;
import com.unihub.workshop.workshops.domain.model.WorkshopStatus;
import com.unihub.workshop.workshops.presentation.dto.StaffAssignedWorkshopResponse;
import com.unihub.workshop.workshops.presentation.mapper.StaffWorkshopPresentationMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StaffWorkshopControllerTest {

    private MockMvc mockMvc;
    private ListStaffAssignedWorkshopsUseCase useCase;
    private StaffWorkshopPresentationMapper presentationMapper;

    private static final UUID STAFF_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = mock(ListStaffAssignedWorkshopsUseCase.class);
        presentationMapper = mock(StaffWorkshopPresentationMapper.class);

        StaffWorkshopController controller = new StaffWorkshopController(
            useCase,
            presentationMapper
        );

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(
                new GlobalExceptionHandler(),
                new GlobalResponseBodyAdvice()
            )
            .build();
    }

    private TestingAuthenticationToken staffAuth(UUID userId) {
        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
            userId.toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            "STAFF",
            UUID.randomUUID().toString(),
            Instant.now(),
            Instant.now().plusSeconds(3600)
        );
        return new TestingAuthenticationToken(
            principal,
            "mock-jwt",
            "ROLE_STAFF"
        );
    }

    private TestingAuthenticationToken nonStaffAuth(UUID userId, String role) {
        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
            userId.toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            role,
            UUID.randomUUID().toString(),
            Instant.now(),
            Instant.now().plusSeconds(3600)
        );
        return new TestingAuthenticationToken(
            principal,
            "mock-jwt",
            "ROLE_" + role
        );
    }

    @Test
    @DisplayName(
        "Should return 200 with paginated assigned workshops for authenticated STAFF"
    )
    void shouldReturnAssignedWorkshops_whenStaffRoleAndValidQuery()
        throws Exception {
        UUID workshopId = UUID.randomUUID();
        StaffAssignedWorkshop domainWorkshop = new StaffAssignedWorkshop(
            workshopId,
            "Python for Data Science",
            "python-for-data-science-2026",
            "Description",
            "https://storage.unihub.io/covers/python.jpg",
            "Building A, Room 301",
            Instant.parse("2026-11-15T09:00:00Z"),
            Instant.parse("2026-11-15T12:00:00Z"),
            WorkshopStatus.PUBLISHED,
            "Faculty of CS",
            Instant.parse("2026-11-01T14:22:10Z")
        );
        CursorPage<StaffAssignedWorkshop> page = new CursorPage<>(
            List.of(domainWorkshop),
            "next-cursor-val",
            true
        );

        StaffAssignedWorkshopResponse dto = new StaffAssignedWorkshopResponse(
            workshopId,
            "Python for Data Science",
            "python-for-data-science-2026",
            "Description",
            "https://storage.unihub.io/covers/python.jpg",
            "Building A, Room 301",
            Instant.parse("2026-11-15T09:00:00Z"),
            Instant.parse("2026-11-15T12:00:00Z"),
            "PUBLISHED",
            "Faculty of CS",
            Instant.parse("2026-11-01T14:22:10Z")
        );
        PageResponse<StaffAssignedWorkshopResponse> pageResponse =
            PageResponse.of(List.of(dto), "next-cursor-val", true);

        when(
            useCase.execute(any(ListStaffAssignedWorkshopsQuery.class))
        ).thenReturn(page);
        when(presentationMapper.toPageResponse(page)).thenReturn(pageResponse);

        mockMvc
            .perform(
                get("/api/v1/workshops/assigned")
                    .principal(staffAuth(STAFF_ID))
                    .param("size", "20")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(
                jsonPath("$.message").value(
                    "Assigned workshops retrieved successfully"
                )
            )
            .andExpect(
                jsonPath("$.data.items[0].id").value(workshopId.toString())
            )
            .andExpect(
                jsonPath("$.data.items[0].title").value(
                    "Python for Data Science"
                )
            )
            .andExpect(
                jsonPath("$.data.items[0].slug").value(
                    "python-for-data-science-2026"
                )
            )
            .andExpect(
                jsonPath("$.data.items[0].organizationName").value(
                    "Faculty of CS"
                )
            )
            .andExpect(
                jsonPath("$.data.cursor.nextCursor").value("next-cursor-val")
            )
            .andExpect(jsonPath("$.data.cursor.hasMore").value(true))
            .andExpect(jsonPath("$.metadata.processedBy").isNotEmpty())
            .andExpect(jsonPath("$.metadata.traceId").isNotEmpty());

        ArgumentCaptor<ListStaffAssignedWorkshopsQuery> captor =
            ArgumentCaptor.forClass(ListStaffAssignedWorkshopsQuery.class);
        verify(useCase).execute(captor.capture());
        assertThat(captor.getValue().staffUserId()).isEqualTo(STAFF_ID);
        assertThat(captor.getValue().size()).isEqualTo(20);
    }

    @Test
    @DisplayName(
        "Should return 200 with empty items list when staff has zero assignments"
    )
    void shouldReturnEmptyPage_whenStaffHasNoAssignments() throws Exception {
        CursorPage<StaffAssignedWorkshop> emptyPage = new CursorPage<>(
            List.of(),
            null,
            false
        );
        PageResponse<StaffAssignedWorkshopResponse> emptyPageResponse =
            PageResponse.of(List.of(), null, false);

        when(
            useCase.execute(any(ListStaffAssignedWorkshopsQuery.class))
        ).thenReturn(emptyPage);
        when(presentationMapper.toPageResponse(emptyPage)).thenReturn(
            emptyPageResponse
        );

        mockMvc
            .perform(
                get("/api/v1/workshops/assigned").principal(staffAuth(STAFF_ID))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items").isEmpty())
            .andExpect(jsonPath("$.data.cursor.hasMore").value(false));
    }

    @Test
    @DisplayName(
        "Should return 401 Unauthorized when no authentication token is provided"
    )
    void shouldReturnUnauthorized_whenNoAuthenticationProvided()
        throws Exception {
        mockMvc
            .perform(get("/api/v1/workshops/assigned"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:unauthorized")
            );
    }

    @Test
    @DisplayName(
        "Should return 403 Forbidden when caller does not have STAFF role"
    )
    void shouldReturnForbidden_whenRoleIsNotStaff() throws Exception {
        mockMvc
            .perform(
                get("/api/v1/workshops/assigned").principal(
                    nonStaffAuth(STAFF_ID, "STUDENT")
                )
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:forbidden")
            );
    }

    @Test
    @DisplayName("Should return 400 Bad Request when size is out of range")
    void shouldReturnBadRequest_whenSizeIsOutOfRange() throws Exception {
        when(
            useCase.execute(any(ListStaffAssignedWorkshopsQuery.class))
        ).thenThrow(
            new BadRequestException(
                "The 'size' parameter must be between 1 and 100."
            )
        );

        mockMvc
            .perform(
                get("/api/v1/workshops/assigned")
                    .principal(staffAuth(STAFF_ID))
                    .param("size", "200")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:bad-request")
            )
            .andExpect(
                jsonPath("$.error.detail").value(
                    "The 'size' parameter must be between 1 and 100."
                )
            );
    }
}
