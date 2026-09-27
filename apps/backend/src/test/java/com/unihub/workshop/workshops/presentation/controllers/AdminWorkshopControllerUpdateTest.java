package com.unihub.workshop.workshops.presentation.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.unihub.workshop.shared.exception.ConflictException;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.shared.security.JwtAuthenticatedPrincipal;
import com.unihub.workshop.shared.storage.MediaStoragePort;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
import com.unihub.workshop.workshops.application.ports.in.GetWorkshopUseCase;
import com.unihub.workshop.workshops.application.ports.in.SaveDraftCommand;
import com.unihub.workshop.workshops.application.ports.in.SaveDraftUseCase;
import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.domain.model.WorkshopStatus;
import com.unihub.workshop.workshops.presentation.dto.SaveDraftRequest;
import com.unihub.workshop.workshops.presentation.mapper.WorkshopPresentationMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminWorkshopControllerUpdateTest {

    private MockMvc mockMvc;
    private SaveDraftUseCase saveDraftUseCase;
    private ObjectMapper objectMapper;

    private static final UUID CALLER_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        saveDraftUseCase = mock(SaveDraftUseCase.class);
        WorkshopPresentationMapper mapper = Mappers.getMapper(
            WorkshopPresentationMapper.class
        );
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        AdminWorkshopController controller = new AdminWorkshopController(
            saveDraftUseCase,
            mock(GetWorkshopUseCase.class),
            mapper,
            mock(MediaStoragePort.class)
        );

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
    @DisplayName(
        "Returns 200 OK with updated title, regenerated slug, and incremented version"
    )
    void shouldReturn200WhenDraftUpdatedSuccessfully() throws Exception {
        UUID workshopId = UUID.randomUUID();
        Instant now = Instant.now();

        Workshop updatedWorkshop = Workshop.builder()
            .id(workshopId)
            .organizationId(ORG_ID)
            .title("Updated Title")
            .slug("updated-title-a1b2c3d4")
            .timezone("Asia/Ho_Chi_Minh")
            .price(BigDecimal.ZERO)
            .currency("USD")
            .status(WorkshopStatus.DRAFT)
            .version(1)
            .createdAt(now.minusSeconds(3600))
            .updatedAt(now)
            .createdBy(CALLER_ID)
            .updatedBy(CALLER_ID)
            .build();

        when(saveDraftUseCase.execute(any(SaveDraftCommand.class))).thenReturn(
            updatedWorkshop
        );

        SaveDraftRequest request = new SaveDraftRequest(
            workshopId,
            0,
            "Updated Title",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );

        mockMvc
            .perform(
                post("/api/v1/admin/workshops/drafts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER"))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(
                jsonPath("$.message").value(
                    "Workshop draft updated successfully."
                )
            )
            .andExpect(jsonPath("$.data.id").value(workshopId.toString()))
            .andExpect(
                jsonPath("$.data.organizationId").value(ORG_ID.toString())
            )
            .andExpect(jsonPath("$.data.title").value("Updated Title"))
            .andExpect(jsonPath("$.data.slug").value("updated-title-a1b2c3d4"))
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.version").value(1));

        verify(saveDraftUseCase).execute(any(SaveDraftCommand.class));
    }

    @Test
    @DisplayName(
        "Returns 409 Conflict with VERSION_CONFLICT when version is stale"
    )
    void shouldReturn409WhenVersionConflict() throws Exception {
        UUID workshopId = UUID.randomUUID();

        when(saveDraftUseCase.execute(any(SaveDraftCommand.class))).thenThrow(
            new ConflictException(
                "Workshop version conflict: expected 6, but received 5"
            )
        );

        SaveDraftRequest request = new SaveDraftRequest(
            workshopId,
            5,
            "Updated Title",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );

        mockMvc
            .perform(
                post("/api/v1/admin/workshops/drafts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER"))
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:conflict")
            );
    }

    @Test
    @DisplayName(
        "Returns 422 Unprocessable Entity when id is provided but version is omitted"
    )
    void shouldReturn422WhenVersionIsOmittedOnUpdate() throws Exception {
        UUID workshopId = UUID.randomUUID();

        when(saveDraftUseCase.execute(any(SaveDraftCommand.class))).thenThrow(
            new ValidationException(
                "version",
                "Version is required when updating an existing draft"
            )
        );

        SaveDraftRequest request = new SaveDraftRequest(
            workshopId,
            null,
            "Updated Title",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );

        mockMvc
            .perform(
                post("/api/v1/admin/workshops/drafts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER"))
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName(
        "Returns 409 Conflict with standard error type when workshop is not in DRAFT status"
    )
    void shouldReturn409WhenWorkshopNotDraft() throws Exception {
        UUID workshopId = UUID.randomUUID();

        when(saveDraftUseCase.execute(any(SaveDraftCommand.class))).thenThrow(
            new ConflictException(
                "Only workshops in DRAFT status can be updated as a draft"
            )
        );

        SaveDraftRequest request = new SaveDraftRequest(
            workshopId,
            1,
            "Updated Title",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );

        mockMvc
            .perform(
                post("/api/v1/admin/workshops/drafts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER"))
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:conflict")
            );
    }

    @Test
    @DisplayName(
        "Returns 403 Forbidden when workshop belongs to another organization"
    )
    void shouldReturn403WhenWorkshopBelongsToDifferentOrg() throws Exception {
        UUID workshopId = UUID.randomUUID();

        when(saveDraftUseCase.execute(any(SaveDraftCommand.class))).thenThrow(
            new ForbiddenException(
                "You do not have access to workshops in other organizations"
            )
        );

        SaveDraftRequest request = new SaveDraftRequest(
            workshopId,
            0,
            "Updated Title",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );

        mockMvc
            .perform(
                post("/api/v1/admin/workshops/drafts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER"))
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:forbidden")
            );
    }

    @Test
    @DisplayName("Returns 404 Not Found when workshop does not exist")
    void shouldReturn404WhenWorkshopNotFound() throws Exception {
        UUID workshopId = UUID.randomUUID();

        when(saveDraftUseCase.execute(any(SaveDraftCommand.class))).thenThrow(
            new NotFoundException("Workshop not found with id: " + workshopId)
        );

        SaveDraftRequest request = new SaveDraftRequest(
            workshopId,
            0,
            "Updated Title",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );

        mockMvc
            .perform(
                post("/api/v1/admin/workshops/drafts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER"))
            )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:not-found")
            );
    }
}
