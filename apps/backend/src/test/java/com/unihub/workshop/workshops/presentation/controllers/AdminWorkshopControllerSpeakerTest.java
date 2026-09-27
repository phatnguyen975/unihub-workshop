package com.unihub.workshop.workshops.presentation.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.shared.security.JwtAuthenticatedPrincipal;
import com.unihub.workshop.shared.storage.MediaStoragePort;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
import com.unihub.workshop.workshops.application.ports.in.GetWorkshopUseCase;
import com.unihub.workshop.workshops.application.ports.in.SaveDraftCommand;
import com.unihub.workshop.workshops.application.ports.in.SaveDraftUseCase;
import com.unihub.workshop.workshops.domain.model.Speaker;
import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.domain.model.WorkshopStatus;
import com.unihub.workshop.workshops.presentation.dto.SaveDraftRequest;
import com.unihub.workshop.workshops.presentation.dto.SpeakerRequest;
import com.unihub.workshop.workshops.presentation.mapper.WorkshopPresentationMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminWorkshopControllerSpeakerTest {

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
        "Returns 201 with speakers when draft created with valid speakers"
    )
    void shouldReturn201WithSpeakersWhenDraftCreated() throws Exception {
        UUID workshopId = UUID.randomUUID();
        UUID speakerId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant startsAt = now.plusSeconds(3600);
        Instant endsAt = now.plusSeconds(7200);

        Speaker domainSpeaker = Speaker.builder()
            .id(speakerId)
            .name("Alice")
            .email("alice@example.com")
            .bio("Keynote Speaker")
            .avatarUrl("https://example.com/alice.jpg")
            .linkedinUrl("https://linkedin.com/in/alice")
            .sortOrder(0)
            .build();

        Workshop workshop = Workshop.builder()
            .id(workshopId)
            .organizationId(ORG_ID)
            .title("AI Workshop")
            .slug("ai-workshop-123")
            .timezone("Asia/Ho_Chi_Minh")
            .startsAt(startsAt)
            .endsAt(endsAt)
            .price(BigDecimal.ZERO)
            .currency("USD")
            .status(WorkshopStatus.DRAFT)
            .version(0)
            .createdAt(now)
            .updatedAt(now)
            .createdBy(CALLER_ID)
            .updatedBy(CALLER_ID)
            .speakers(List.of(domainSpeaker))
            .build();

        when(saveDraftUseCase.execute(any(SaveDraftCommand.class))).thenReturn(
            workshop
        );

        SaveDraftRequest request = new SaveDraftRequest(
            null,
            null,
            "AI Workshop",
            null,
            null,
            null,
            null,
            startsAt,
            endsAt,
            null,
            null,
            null,
            List.of(
                SpeakerRequest.builder()
                    .name("Alice")
                    .email("alice@example.com")
                    .bio("Keynote Speaker")
                    .avatarUrl("https://example.com/alice.jpg")
                    .linkedinUrl("https://linkedin.com/in/alice")
                    .sortOrder(1)
                    .build()
            )
        );

        mockMvc
            .perform(
                post("/api/v1/admin/workshops/drafts")
                    .principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.speakers").isArray())
            .andExpect(jsonPath("$.data.speakers[0].name").value("Alice"))
            .andExpect(
                jsonPath("$.data.speakers[0].email").value("alice@example.com")
            )
            .andExpect(
                jsonPath("$.data.speakers[0].bio").value("Keynote Speaker")
            )
            .andExpect(
                jsonPath("$.data.speakers[0].avatarUrl").value(
                    "https://example.com/alice.jpg"
                )
            )
            .andExpect(
                jsonPath("$.data.speakers[0].linkedinUrl").value(
                    "https://linkedin.com/in/alice"
                )
            )
            .andExpect(jsonPath("$.data.speakers[0].sortOrder").value(0));
    }

    @Test
    @DisplayName("Speaker with negative sortOrder returns 422")
    void speakerWithNegativeSortOrder_Returns422() throws Exception {
        SaveDraftRequest request = new SaveDraftRequest(
            null,
            null,
            "Valid Title",
            null,
            null,
            null,
            "Asia/Ho_Chi_Minh",
            Instant.now().plusSeconds(3600),
            Instant.now().plusSeconds(7200),
            null,
            null,
            null,
            List.of(
                SpeakerRequest.builder()
                    .name("Alice")
                    .email("alice@example.com")
                    .sortOrder(-1)
                    .build()
            )
        );

        mockMvc
            .perform(
                post("/api/v1/admin/workshops/drafts")
                    .principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
            .andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("Returns 422 when speaker name is blank")
    void shouldReturn422WhenSpeakerNameIsBlank() throws Exception {
        SaveDraftRequest request = new SaveDraftRequest(
            null,
            null,
            "AI Workshop",
            null,
            null,
            null,
            null,
            Instant.now().plusSeconds(3600),
            Instant.now().plusSeconds(7200),
            null,
            null,
            null,
            List.of(
                SpeakerRequest.builder()
                    .name("   ")
                    .email("alice@example.com")
                    .build()
            )
        );

        mockMvc
            .perform(
                post("/api/v1/admin/workshops/drafts")
                    .principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
            .andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("Returns 422 when speaker email is invalid")
    void shouldReturn422WhenSpeakerEmailIsInvalid() throws Exception {
        SaveDraftRequest request = new SaveDraftRequest(
            null,
            null,
            "AI Workshop",
            null,
            null,
            null,
            null,
            Instant.now().plusSeconds(3600),
            Instant.now().plusSeconds(7200),
            null,
            null,
            null,
            List.of(
                SpeakerRequest.builder()
                    .name("Alice")
                    .email("not-an-email")
                    .build()
            )
        );

        mockMvc
            .perform(
                post("/api/v1/admin/workshops/drafts")
                    .principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
            .andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("Returns 422 when more than 10 speakers are provided")
    void shouldReturn422WhenMoreThan10Speakers() throws Exception {
        List<SpeakerRequest> speakers = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            speakers.add(
                SpeakerRequest.builder()
                    .name("Speaker " + i)
                    .email("speaker" + i + "@example.com")
                    .build()
            );
        }

        SaveDraftRequest request = new SaveDraftRequest(
            null,
            null,
            "AI Workshop",
            null,
            null,
            null,
            null,
            Instant.now().plusSeconds(3600),
            Instant.now().plusSeconds(7200),
            null,
            null,
            null,
            speakers
        );

        mockMvc
            .perform(
                post("/api/v1/admin/workshops/drafts")
                    .principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
            .andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName(
        "Returns 422 when speaker schedule conflict is detected by use case"
    )
    void shouldReturn422WhenScheduleConflictDetected() throws Exception {
        when(saveDraftUseCase.execute(any(SaveDraftCommand.class))).thenThrow(
            new ValidationException(
                "speakers",
                "Speaker schedule conflict detected for email(s): bob@example.com"
            )
        );

        SaveDraftRequest request = new SaveDraftRequest(
            null,
            null,
            "AI Workshop",
            null,
            null,
            null,
            null,
            Instant.now().plusSeconds(3600),
            Instant.now().plusSeconds(7200),
            null,
            null,
            null,
            List.of(
                SpeakerRequest.builder()
                    .name("Bob")
                    .email("bob@example.com")
                    .build()
            )
        );

        mockMvc
            .perform(
                post("/api/v1/admin/workshops/drafts")
                    .principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.detail").value(
                    Matchers.containsString(
                        "Speaker schedule conflict detected"
                    )
                )
            );
    }

    @Test
    @DisplayName(
        "Returns 200 with replaced speakers when draft updated with new speakers"
    )
    void shouldReturn200WithReplacedSpeakersOnUpdate() throws Exception {
        UUID workshopId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant startsAt = now.plusSeconds(3600);
        Instant endsAt = now.plusSeconds(7200);

        Speaker newSpeaker = Speaker.builder()
            .id(UUID.randomUUID())
            .name("Updated Speaker")
            .email("updated@example.com")
            .sortOrder(0)
            .build();

        Workshop updatedWorkshop = Workshop.builder()
            .id(workshopId)
            .organizationId(ORG_ID)
            .title("Updated Title")
            .slug("updated-title-123")
            .timezone("Asia/Ho_Chi_Minh")
            .startsAt(startsAt)
            .endsAt(endsAt)
            .price(BigDecimal.ZERO)
            .currency("USD")
            .status(WorkshopStatus.DRAFT)
            .version(1)
            .createdAt(now)
            .updatedAt(now)
            .createdBy(CALLER_ID)
            .updatedBy(CALLER_ID)
            .speakers(List.of(newSpeaker))
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
            startsAt,
            endsAt,
            null,
            null,
            null,
            List.of(
                SpeakerRequest.builder()
                    .name("Updated Speaker")
                    .email("updated@example.com")
                    .build()
            )
        );

        mockMvc
            .perform(
                post("/api/v1/admin/workshops/drafts")
                    .principal(createAuth(CALLER_ID, ORG_ID, "ORGANIZER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.speakers").isArray())
            .andExpect(
                jsonPath("$.data.speakers[0].name").value("Updated Speaker")
            )
            .andExpect(
                jsonPath("$.data.speakers[0].email").value(
                    "updated@example.com"
                )
            );
    }
}
