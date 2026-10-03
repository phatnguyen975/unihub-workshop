package com.unihub.workshop.search.presentation.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unihub.workshop.search.application.ports.in.SearchWorkshopsQuery;
import com.unihub.workshop.search.application.ports.in.SearchWorkshopsUseCase;
import com.unihub.workshop.search.domain.model.WorkshopCard;
import com.unihub.workshop.search.presentation.mapper.WorkshopSearchPresentationMapper;
import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
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
class WorkshopSearchControllerTest {

    private MockMvc mockMvc;

    @Mock
    private SearchWorkshopsUseCase searchWorkshopsUseCase;

    private WorkshopSearchPresentationMapper mapper;

    private final UUID workshopId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mapper = Mappers.getMapper(WorkshopSearchPresentationMapper.class);
        WorkshopSearchController controller = new WorkshopSearchController(
            searchWorkshopsUseCase,
            mapper
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(
                new GlobalExceptionHandler(),
                new GlobalResponseBodyAdvice()
            )
            .build();
    }

    private WorkshopCard createCard(
        UUID id,
        BigDecimal price,
        int availableSeats
    ) {
        return new WorkshopCard(
            id,
            "cloud-native-deep-dive",
            "Cloud Native Deep Dive",
            "https://cdn.example.com/cover.jpg",
            "Building B, Room 204",
            Instant.parse("2026-11-20T09:00:00Z"),
            Instant.parse("2026-11-20T13:00:00Z"),
            "Asia/Ho_Chi_Minh",
            price,
            "USD",
            100,
            availableSeats,
            WorkshopCard.derivePriceType(price),
            orgId
        );
    }

    @Test
    @DisplayName(
        "GET /api/v1/workshops with no parameters returns 200 OK and standard envelope"
    )
    void shouldReturnWorkshopsWithDefaultParams() throws Exception {
        WorkshopCard card = createCard(workshopId, BigDecimal.ZERO, 45);
        CursorPage<WorkshopCard> page = new CursorPage<>(
            List.of(card),
            "cursor-token-123",
            true
        );

        when(
            searchWorkshopsUseCase.search(any(SearchWorkshopsQuery.class))
        ).thenReturn(page);

        mockMvc
            .perform(get("/api/v1/workshops"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Workshops retrieved"))
            .andExpect(
                jsonPath("$.data.items[0].id").value(workshopId.toString())
            )
            .andExpect(
                jsonPath("$.data.items[0].title").value(
                    "Cloud Native Deep Dive"
                )
            )
            .andExpect(jsonPath("$.data.items[0].price").value("0.00"))
            .andExpect(jsonPath("$.data.items[0].priceType").value("FREE"))
            .andExpect(jsonPath("$.data.items[0].availableSeats").value(45))
            .andExpect(
                jsonPath("$.data.cursor.nextCursor").value("cursor-token-123")
            )
            .andExpect(jsonPath("$.data.cursor.hasMore").value(true));
    }

    @Test
    @DisplayName(
        "GET /api/v1/workshops?q=machine+learning returns 200 OK and passes query keyword"
    )
    void shouldPassKeywordToUseCase() throws Exception {
        CursorPage<WorkshopCard> emptyPage = new CursorPage<>(
            List.of(),
            null,
            false
        );
        when(
            searchWorkshopsUseCase.search(any(SearchWorkshopsQuery.class))
        ).thenReturn(emptyPage);

        mockMvc
            .perform(get("/api/v1/workshops").param("q", "machine learning"))
            .andExpect(status().isOk());

        ArgumentCaptor<SearchWorkshopsQuery> captor = ArgumentCaptor.forClass(
            SearchWorkshopsQuery.class
        );
        verify(searchWorkshopsUseCase).search(captor.capture());
        SearchWorkshopsQuery query = captor.getValue();
        assertThat(query.q()).isEqualTo("machine learning");
    }

    @Test
    @DisplayName("GET /api/v1/workshops?priceType=FREE returns 200 OK")
    void shouldPassPriceTypeToUseCase() throws Exception {
        CursorPage<WorkshopCard> emptyPage = new CursorPage<>(
            List.of(),
            null,
            false
        );
        when(
            searchWorkshopsUseCase.search(any(SearchWorkshopsQuery.class))
        ).thenReturn(emptyPage);

        mockMvc
            .perform(get("/api/v1/workshops").param("priceType", "FREE"))
            .andExpect(status().isOk());

        ArgumentCaptor<SearchWorkshopsQuery> captor = ArgumentCaptor.forClass(
            SearchWorkshopsQuery.class
        );
        verify(searchWorkshopsUseCase).search(captor.capture());
        assertThat(captor.getValue().priceType()).isEqualTo("FREE");
    }

    @Test
    @DisplayName(
        "GET /api/v1/workshops?from=...&to=... returns 200 OK when from < to"
    )
    void shouldAcceptValidDateRange() throws Exception {
        CursorPage<WorkshopCard> emptyPage = new CursorPage<>(
            List.of(),
            null,
            false
        );
        when(
            searchWorkshopsUseCase.search(any(SearchWorkshopsQuery.class))
        ).thenReturn(emptyPage);

        mockMvc
            .perform(
                get("/api/v1/workshops")
                    .param("from", "2026-11-01T00:00:00Z")
                    .param("to", "2026-11-30T23:59:59Z")
            )
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName(
        "GET /api/v1/workshops?from=... returns 200 OK for one-sided temporal bound"
    )
    void shouldAcceptOneSidedFromBound() throws Exception {
        CursorPage<WorkshopCard> emptyPage = new CursorPage<>(
            List.of(),
            null,
            false
        );
        when(
            searchWorkshopsUseCase.search(any(SearchWorkshopsQuery.class))
        ).thenReturn(emptyPage);

        mockMvc
            .perform(
                get("/api/v1/workshops").param("from", "2026-11-01T00:00:00Z")
            )
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName(
        "GET /api/v1/workshops?from=...&to=... returns 422 Unprocessable Content when from >= to"
    )
    void shouldRejectInvalidDateRangeWhereFromAfterTo() throws Exception {
        mockMvc
            .perform(
                get("/api/v1/workshops")
                    .param("from", "2026-12-01T00:00:00Z")
                    .param("to", "2026-11-01T00:00:00Z")
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            );
    }

    @Test
    @DisplayName(
        "GET /api/v1/workshops?from=not-a-date returns 422 Unprocessable Content"
    )
    void shouldRejectUnparseableDate() throws Exception {
        mockMvc
            .perform(get("/api/v1/workshops").param("from", "not-a-date"))
            .andExpect(status().isUnprocessableContent())
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            );
    }

    @Test
    @DisplayName(
        "GET /api/v1/workshops?size=200 returns 422 Unprocessable Content"
    )
    void shouldRejectSizeExceeding100() throws Exception {
        mockMvc
            .perform(get("/api/v1/workshops").param("size", "200"))
            .andExpect(status().isUnprocessableContent())
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            );
    }

    @Test
    @DisplayName(
        "GET /api/v1/workshops?priceType=INVALID returns 422 Unprocessable Content"
    )
    void shouldRejectInvalidPriceType() throws Exception {
        mockMvc
            .perform(get("/api/v1/workshops").param("priceType", "INVALID"))
            .andExpect(status().isUnprocessableContent())
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            );
    }

    @Test
    @DisplayName(
        "GET /api/v1/workshops?sort=invalid returns 422 Unprocessable Content"
    )
    void shouldRejectInvalidSort() throws Exception {
        mockMvc
            .perform(get("/api/v1/workshops").param("sort", "invalid"))
            .andExpect(status().isUnprocessableContent())
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            );
    }

    @Test
    @DisplayName(
        "GET /api/v1/workshops returns 400 Bad Request when cursor sort field mismatches"
    )
    void shouldReturnBadRequestOnCursorSortMismatch() throws Exception {
        when(searchWorkshopsUseCase.search(any())).thenThrow(
            new BadRequestException(
                "Cursor was issued for sort='startsAt' but current request uses sort='price'. Discard the cursor and start from page 1."
            )
        );

        mockMvc
            .perform(
                get("/api/v1/workshops")
                    .param("sort", "price,asc")
                    .param("cursor", "some-cursor")
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:bad-request")
            );
    }
}
