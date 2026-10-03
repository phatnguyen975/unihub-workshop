package com.unihub.workshop.workshops.presentation.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
import com.unihub.workshop.workshops.application.ports.in.GetPublicWorkshopQuery;
import com.unihub.workshop.workshops.application.ports.in.GetPublicWorkshopUseCase;
import com.unihub.workshop.workshops.domain.model.PublicWorkshopDetail;
import com.unihub.workshop.workshops.domain.model.Speaker;
import com.unihub.workshop.workshops.domain.model.WorkshopStatus;
import com.unihub.workshop.workshops.presentation.mapper.PublicWorkshopDetailPresentationMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class PublicWorkshopControllerTest {

    private MockMvc mockMvc;

    @Mock
    private GetPublicWorkshopUseCase getPublicWorkshopUseCase;

    private PublicWorkshopDetailPresentationMapper mapper;

    private final UUID workshopId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();
    private final UUID speakerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mapper = Mappers.getMapper(
            PublicWorkshopDetailPresentationMapper.class
        );
        PublicWorkshopController controller = new PublicWorkshopController(
            getPublicWorkshopUseCase,
            mapper
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(
                new GlobalExceptionHandler(),
                new GlobalResponseBodyAdvice()
            )
            .build();
    }

    private PublicWorkshopDetail createDetail(UUID id, String slug) {
        Speaker speaker = Speaker.builder()
            .id(speakerId)
            .workshopId(id)
            .name("Jane Doe")
            .bio("Senior Architect")
            .avatarUrl("https://cdn.example.com/jane.jpg")
            .linkedinUrl("https://linkedin.com/in/janedoe")
            .email("jane.doe@example.com")
            .sortOrder(0)
            .build();

        return PublicWorkshopDetail.builder()
            .id(id)
            .organizationId(orgId)
            .title("Microservices Mastery")
            .slug(slug)
            .description("Deep dive into microservices")
            .coverImageUrl("https://cdn.example.com/cover.jpg")
            .location("Room 101, Faculty of IT")
            .timezone("Asia/Ho_Chi_Minh")
            .startsAt(Instant.parse("2026-11-20T09:00:00Z"))
            .endsAt(Instant.parse("2026-11-20T12:00:00Z"))
            .capacity(80)
            .availableSeats(42)
            .price(BigDecimal.ZERO)
            .currency("USD")
            .priceType("FREE")
            .status(WorkshopStatus.PUBLISHED)
            .publishedAt(Instant.parse("2026-10-01T09:00:00Z"))
            .speakers(List.of(speaker))
            .build();
    }

    @Test
    @DisplayName(
        "GET /api/v1/workshops/{uuid} returns 200 OK with full workshop details and standard envelope"
    )
    void shouldReturnWorkshopDetailByUuid() throws Exception {
        PublicWorkshopDetail detail = createDetail(
            workshopId,
            "microservices-mastery"
        );
        when(
            getPublicWorkshopUseCase.execute(any(GetPublicWorkshopQuery.class))
        ).thenReturn(detail);

        mockMvc
            .perform(
                get("/api/v1/workshops/{identifier}", workshopId.toString())
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Workshop retrieved"))
            .andExpect(jsonPath("$.data.id").value(workshopId.toString()))
            .andExpect(jsonPath("$.data.slug").value("microservices-mastery"))
            .andExpect(jsonPath("$.data.title").value("Microservices Mastery"))
            .andExpect(
                jsonPath("$.data.description").value(
                    "Deep dive into microservices"
                )
            )
            .andExpect(
                jsonPath("$.data.location").value("Room 101, Faculty of IT")
            )
            .andExpect(jsonPath("$.data.price").value("0.00"))
            .andExpect(jsonPath("$.data.priceType").value("FREE"))
            .andExpect(jsonPath("$.data.capacity").value(80))
            .andExpect(jsonPath("$.data.availableSeats").value(42))
            .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
            .andExpect(
                jsonPath("$.data.speakers[0].id").value(speakerId.toString())
            )
            .andExpect(jsonPath("$.data.speakers[0].name").value("Jane Doe"))
            .andExpect(jsonPath("$.data.speakers[0].sortOrder").value(0));

        ArgumentCaptor<GetPublicWorkshopQuery> captor = ArgumentCaptor.forClass(
            GetPublicWorkshopQuery.class
        );
        verify(getPublicWorkshopUseCase).execute(captor.capture());
        assertThat(captor.getValue().identifier()).isEqualTo(
            workshopId.toString()
        );
    }

    @Test
    @DisplayName(
        "GET /api/v1/workshops/{slug} returns 200 OK with identical shape"
    )
    void shouldReturnWorkshopDetailBySlug() throws Exception {
        PublicWorkshopDetail detail = createDetail(
            workshopId,
            "microservices-mastery"
        );
        when(
            getPublicWorkshopUseCase.execute(any(GetPublicWorkshopQuery.class))
        ).thenReturn(detail);

        mockMvc
            .perform(
                get("/api/v1/workshops/{identifier}", "microservices-mastery")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.slug").value("microservices-mastery"));

        ArgumentCaptor<GetPublicWorkshopQuery> captor = ArgumentCaptor.forClass(
            GetPublicWorkshopQuery.class
        );
        verify(getPublicWorkshopUseCase).execute(captor.capture());
        assertThat(captor.getValue().identifier()).isEqualTo(
            "microservices-mastery"
        );
    }

    @Test
    @DisplayName("GET /api/v1/workshops/nonexistent-slug returns 404 Not Found")
    void shouldReturn404WhenWorkshopDoesNotExist() throws Exception {
        when(
            getPublicWorkshopUseCase.execute(any(GetPublicWorkshopQuery.class))
        ).thenThrow(new NotFoundException("Workshop not found."));

        mockMvc
            .perform(get("/api/v1/workshops/{identifier}", "nonexistent-slug"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:not-found")
            )
            .andExpect(jsonPath("$.error.detail").value("Workshop not found."));
    }

    @Test
    @DisplayName(
        "GET /api/v1/workshops/{draftId} returns 404 Not Found for non-published workshop"
    )
    void shouldReturn404WhenWorkshopIsNotPublished() throws Exception {
        UUID draftId = UUID.randomUUID();
        when(
            getPublicWorkshopUseCase.execute(any(GetPublicWorkshopQuery.class))
        ).thenThrow(new NotFoundException("Workshop not found."));

        mockMvc
            .perform(get("/api/v1/workshops/{identifier}", draftId.toString()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:not-found")
            );
    }
}
