package com.unihub.workshop.search.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unihub.workshop.search.application.ports.in.SearchWorkshopsQuery;
import com.unihub.workshop.search.application.ports.out.SearchCachePort;
import com.unihub.workshop.search.application.ports.out.WorkshopAvailabilityBatchPort;
import com.unihub.workshop.search.application.ports.out.WorkshopSearchRepository;
import com.unihub.workshop.search.domain.model.SwrCacheEntry;
import com.unihub.workshop.search.domain.model.WorkshopCard;
import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.shared.pagination.KeysetCursor;
import com.unihub.workshop.shared.pagination.KeysetCursorCodec;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SearchWorkshopsUseCaseImplTest {

    @Mock
    private WorkshopSearchRepository searchRepository;

    @Mock
    private WorkshopAvailabilityBatchPort availabilityBatchPort;

    @Mock
    private SearchCachePort cachePort;

    private CircuitBreaker circuitBreaker;
    private SearchWorkshopsUseCaseImpl useCase;

    private final UUID workshopId1 = UUID.randomUUID();
    private final UUID workshopId2 = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        circuitBreaker = CircuitBreaker.of(
            "searchCircuitBreaker",
            CircuitBreakerConfig.ofDefaults()
        );
        useCase = new SearchWorkshopsUseCaseImpl(
            searchRepository,
            availabilityBatchPort,
            cachePort,
            circuitBreaker,
            Runnable::run,
            300L,
            86400L,
            10L
        );
    }

    private WorkshopCard createCard(UUID id, int capacity) {
        return new WorkshopCard(
            id,
            "card-slug-" + id,
            "Card Title",
            "https://cdn.example.com/cover.jpg",
            "Hall A",
            Instant.parse("2026-11-20T09:00:00Z"),
            Instant.parse("2026-11-20T13:00:00Z"),
            "Asia/Ho_Chi_Minh",
            BigDecimal.ZERO,
            "USD",
            capacity,
            null,
            "FREE",
            orgId
        );
    }

    @Test
    @DisplayName(
        "search: cache miss triggers ES search, enriches availableSeats, and stores in SWR cache"
    )
    void shouldPerformSearchAndEnrichAvailabilityOnCacheMiss() {
        SearchWorkshopsQuery query = new SearchWorkshopsQuery(
            null,
            null,
            null,
            null,
            "startsAt,asc",
            null,
            20
        );
        WorkshopCard card1 = createCard(workshopId1, 100);
        WorkshopCard card2 = createCard(workshopId2, 50);
        CursorPage<WorkshopCard> esResult = new CursorPage<>(
            List.of(card1, card2),
            "next-cursor",
            true
        );

        when(cachePort.get(anyString())).thenReturn(Optional.empty());
        when(searchRepository.search(eq(query), any())).thenReturn(esResult);
        when(availabilityBatchPort.resolveAvailableSeats(any())).thenReturn(
            Map.of(workshopId1, 85, workshopId2, 10)
        );

        CursorPage<WorkshopCard> result = useCase.search(query);

        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(0).availableSeats()).isEqualTo(85);
        assertThat(result.items().get(1).availableSeats()).isEqualTo(10);
        assertThat(result.hasMore()).isTrue();
        assertThat(result.nextCursor()).isEqualTo("next-cursor");

        verify(cachePort).put(
            anyString(),
            any(SwrCacheEntry.class),
            any(Duration.class)
        );
    }

    @Test
    @DisplayName(
        "search: fresh cache hit returns cached result immediately without querying ES"
    )
    void shouldReturnFromCacheImmediatelyOnFreshHit() {
        SearchWorkshopsQuery query = new SearchWorkshopsQuery(
            null,
            null,
            null,
            null,
            "startsAt,asc",
            null,
            20
        );
        WorkshopCard card = createCard(workshopId1, 100);
        CursorPage<WorkshopCard> cachedResult = new CursorPage<>(
            List.of(card),
            null,
            false
        );
        SwrCacheEntry freshEntry = new SwrCacheEntry(
            Instant.now(),
            cachedResult
        );

        when(cachePort.get(anyString())).thenReturn(Optional.of(freshEntry));

        CursorPage<WorkshopCard> result = useCase.search(query);

        assertThat(result.items()).hasSize(1);
        verify(searchRepository, never()).search(any(), any());
    }

    @Test
    @DisplayName(
        "search: stale cache hit returns stale cached result and attempts non-blocking tryLock"
    )
    void shouldReturnStaleResultAndAttemptLockOnStaleHit() {
        SearchWorkshopsQuery query = new SearchWorkshopsQuery(
            null,
            null,
            null,
            null,
            "startsAt,asc",
            null,
            20
        );
        WorkshopCard card = createCard(workshopId1, 100);
        CursorPage<WorkshopCard> cachedResult = new CursorPage<>(
            List.of(card),
            null,
            false
        );
        // Stale entry: cached 10 minutes ago, soft TTL is 5 minutes
        SwrCacheEntry staleEntry = new SwrCacheEntry(
            Instant.now().minus(Duration.ofMinutes(10)),
            cachedResult
        );

        when(cachePort.get(anyString())).thenReturn(Optional.of(staleEntry));
        when(cachePort.tryLock(anyString(), any(Duration.class))).thenReturn(
            true
        );

        CursorPage<WorkshopCard> result = useCase.search(query);

        assertThat(result.items()).hasSize(1);
        verify(cachePort).tryLock(anyString(), any(Duration.class));
    }

    @Test
    @DisplayName(
        "search: circuit breaker OPEN or ES failure returns graceful degradation (200 OK empty list)"
    )
    void shouldReturnEmptyListWhenCircuitBreakerIsOpen() {
        SearchWorkshopsQuery query = new SearchWorkshopsQuery(
            null,
            null,
            null,
            null,
            "startsAt,asc",
            null,
            20
        );

        when(cachePort.get(anyString())).thenReturn(Optional.empty());
        when(searchRepository.search(eq(query), any())).thenThrow(
            CallNotPermittedException.createCallNotPermittedException(
                circuitBreaker
            )
        );

        CursorPage<WorkshopCard> result = useCase.search(query);

        assertThat(result.items()).isEmpty();
        assertThat(result.hasMore()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    @DisplayName(
        "search: cursor sort field mismatch throws BadRequestException"
    )
    void shouldRejectMismatchedCursorSortField() {
        KeysetCursor staleCursor = new KeysetCursor(
            UUID.randomUUID(),
            "startsAt",
            "2026-11-15T08:00:00Z",
            null,
            null
        );
        String encodedCursor = KeysetCursorCodec.encode(staleCursor);
        SearchWorkshopsQuery query = new SearchWorkshopsQuery(
            null,
            null,
            null,
            null,
            "price,asc",
            encodedCursor,
            20
        );

        assertThatThrownBy(() -> useCase.search(query))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("startsAt")
            .hasMessageContaining("price");

        verify(searchRepository, never()).search(any(), any());
    }

    @Test
    @DisplayName(
        "search: cursor bound to a different filter set throws BadRequestException"
    )
    void shouldRejectCursorBoundToAnotherFilterSet() {
        String encodedCursor = KeysetCursorCodec.encode(
            new KeysetCursor(
                UUID.randomUUID(),
                "startsAt",
                "2026-11-15T08:00:00Z",
                null,
                "q=alice&priceType=*&from=*&to=*"
            )
        );
        SearchWorkshopsQuery query = new SearchWorkshopsQuery(
            "bob",
            null,
            null,
            null,
            "startsAt,asc",
            encodedCursor,
            20
        );

        assertThatThrownBy(() -> useCase.search(query))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("different filter set");

        verify(searchRepository, never()).search(any(), any());
    }

    @Test
    @DisplayName("search: honors configured soft and hard TTL parameters")
    void shouldHonorConfiguredTtlParameters() {
        SearchWorkshopsUseCaseImpl customTtlUseCase =
            new SearchWorkshopsUseCaseImpl(
                searchRepository,
                availabilityBatchPort,
                cachePort,
                circuitBreaker,
                Runnable::run,
                60L,
                3600L,
                5L
            );

        SearchWorkshopsQuery query = new SearchWorkshopsQuery(
            null,
            null,
            null,
            null,
            "startsAt,asc",
            null,
            20
        );
        WorkshopCard card = createCard(workshopId1, 100);
        CursorPage<WorkshopCard> cachedResult = new CursorPage<>(
            List.of(card),
            null,
            false
        );
        // 90 seconds old -> stale for 60s soft TTL, fresh for 300s default
        SwrCacheEntry entry = new SwrCacheEntry(
            Instant.now().minus(Duration.ofSeconds(90)),
            cachedResult
        );

        when(cachePort.get(anyString())).thenReturn(Optional.of(entry));
        when(cachePort.tryLock(anyString(), any(Duration.class))).thenReturn(
            true
        );

        CursorPage<WorkshopCard> result = customTtlUseCase.search(query);

        assertThat(result.items()).hasSize(1);
        verify(cachePort).tryLock(anyString(), eq(Duration.ofSeconds(5)));
    }
}
