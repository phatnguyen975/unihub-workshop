package com.unihub.workshop.workshops.presentation.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.unihub.workshop.shared.security.JwtAuthenticatedPrincipal;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
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

class AdminWorkshopControllerCreateTest {

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
            mapper
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
        "Returns 201 Created when valid draft request is sent by ORGANIZER"
    )
    void shouldReturn201WhenDraftCreatedSuccessfully() throws Exception {
        UUID workshopId = UUID.randomUUID();
        Instant now = Instant.now();

        Workshop createdWorkshop = Workshop.builder()
            .id(workshopId)
            .organizationId(ORG_ID)
            .title("Introduction to AI")
            .slug("introduction-to-ai-a1b2c3d4")
            .timezone("Asia/Ho_Chi_Minh")
            .price(BigDecimal.ZERO)
            .currency("USD")
            .status(WorkshopStatus.DRAFT)
            .version(0)
            .createdAt(now)
            .updatedAt(now)
            .createdBy(CALLER_ID)
            .updatedBy(CALLER_ID)
            .build();

        when(saveDraftUseCase.execute(any(SaveDraftCommand.class))).thenReturn(
            createdWorkshop
        );

        SaveDraftRequest request = new SaveDraftRequest(
            null,
            null,
            "Introduction to AI",
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
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(
                jsonPath("$.message").value(
                    "Workshop draft created successfully."
                )
            )
            .andExpect(jsonPath("$.data.id").value(workshopId.toString()))
            .andExpect(
                jsonPath("$.data.organizationId").value(ORG_ID.toString())
            )
            .andExpect(jsonPath("$.data.title").value("Introduction to AI"))
            .andExpect(
                jsonPath("$.data.slug").value("introduction-to-ai-a1b2c3d4")
            )
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.version").value(0))
            .andExpect(jsonPath("$.data.timezone").value("Asia/Ho_Chi_Minh"))
            .andExpect(jsonPath("$.data.price").value(0))
            .andExpect(jsonPath("$.data.currency").value("USD"))
            .andExpect(jsonPath("$.data.speakers").isArray());

        verify(saveDraftUseCase).execute(any(SaveDraftCommand.class));
    }

    @Test
    @DisplayName("Returns 401 Unauthorized when unauthenticated")
    void shouldReturn401WhenUnauthenticated() throws Exception {
        SaveDraftRequest request = new SaveDraftRequest(
            null,
            null,
            "Title",
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
            )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:unauthorized")
            );
    }

    @Test
    @DisplayName("Returns 403 Forbidden when caller role is STUDENT")
    void shouldReturn403WhenRoleIsStudent() throws Exception {
        SaveDraftRequest request = new SaveDraftRequest(
            null,
            null,
            "Title",
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
                    .principal(createAuth(CALLER_ID, ORG_ID, "STUDENT"))
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:forbidden")
            );
    }

    @Test
    @DisplayName("Returns 403 Forbidden when org claim is missing from JWT")
    void shouldReturn403WhenOrgClaimIsMissing() throws Exception {
        SaveDraftRequest request = new SaveDraftRequest(
            null,
            null,
            "Title",
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
                    .principal(createAuth(CALLER_ID, null, "ORGANIZER"))
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:forbidden")
            );
    }

    @Test
    @DisplayName("Returns 422 Unprocessable Entity when title is blank")
    void shouldReturn422WhenTitleIsBlank() throws Exception {
        SaveDraftRequest request = new SaveDraftRequest(
            null,
            null,
            "   ",
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
    @DisplayName("Returns 422 Unprocessable Entity when capacity is 0")
    void shouldReturn422WhenCapacityIsZero() throws Exception {
        SaveDraftRequest request = new SaveDraftRequest(
            null,
            null,
            "Valid Title",
            null,
            null,
            null,
            null,
            null,
            null,
            0,
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
    @DisplayName("Returns 422 Unprocessable Entity when price is negative")
    void shouldReturn422WhenPriceIsNegative() throws Exception {
        SaveDraftRequest request = new SaveDraftRequest(
            null,
            null,
            "Valid Title",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            new BigDecimal("-1.00"),
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
        "Returns 422 Unprocessable Entity when currency is not 3 uppercase letters"
    )
    void shouldReturn422WhenCurrencyIsInvalid() throws Exception {
        SaveDraftRequest request = new SaveDraftRequest(
            null,
            null,
            "Valid Title",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "us",
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
        "Returns 422 Unprocessable Entity when location exceeds 255 characters"
    )
    void shouldReturn422WhenLocationExceeds255Chars() throws Exception {
        SaveDraftRequest request = new SaveDraftRequest(
            null,
            null,
            "Valid Title",
            null,
            null,
            "L".repeat(256),
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
        "Returns 422 Unprocessable Entity when capacity exceeds 1,000,000"
    )
    void shouldReturn422WhenCapacityExceedsMax() throws Exception {
        SaveDraftRequest request = new SaveDraftRequest(
            null,
            null,
            "Valid Title",
            null,
            null,
            null,
            null,
            null,
            null,
            1_000_001,
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
        "Returns 422 Unprocessable Entity when coverImageUrl does not start with http:// or https://"
    )
    void shouldReturn422WhenCoverImageUrlInvalid() throws Exception {
        SaveDraftRequest request = new SaveDraftRequest(
            null,
            null,
            "Valid Title",
            null,
            "ftp://invalid-url.com/image.png",
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
}
