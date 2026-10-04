package com.unihub.workshop.search.application.usecases;

import com.unihub.workshop.search.application.ports.in.SearchWorkshopsQuery;
import com.unihub.workshop.search.application.ports.in.SearchWorkshopsUseCase;
import com.unihub.workshop.search.application.ports.out.SearchCachePort;
import com.unihub.workshop.search.application.ports.out.WorkshopAvailabilityBatchPort;
import com.unihub.workshop.search.application.ports.out.WorkshopSearchRepository;
import com.unihub.workshop.search.domain.model.SwrCacheEntry;
import com.unihub.workshop.search.domain.model.WorkshopCard;
import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.shared.pagination.KeysetCursor;
import com.unihub.workshop.shared.pagination.KeysetCursorCodec;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SearchWorkshopsUseCaseImpl implements SearchWorkshopsUseCase {

    private final WorkshopSearchRepository searchRepository;
    private final WorkshopAvailabilityBatchPort availabilityBatchPort;
    private final SearchCachePort cachePort;
    private final CircuitBreaker circuitBreaker;
    private final Executor taskExecutor;

    private final Duration softTtl;
    private final Duration hardTtl;
    private final Duration lockTimeout;

    public SearchWorkshopsUseCaseImpl(
        WorkshopSearchRepository searchRepository,
        WorkshopAvailabilityBatchPort availabilityBatchPort,
        SearchCachePort cachePort,
        @Qualifier("elasticsearchCircuitBreaker") CircuitBreaker circuitBreaker,
        @Qualifier("searchTaskExecutor") Executor taskExecutor,
        @Value("${app.search.swr.soft-ttl-seconds:300}") long softTtlSeconds,
        @Value("${app.search.swr.hard-ttl-seconds:86400}") long hardTtlSeconds,
        @Value(
            "${app.search.swr.lock-timeout-seconds:10}"
        ) long lockTimeoutSeconds
    ) {
        this.searchRepository = searchRepository;
        this.availabilityBatchPort = availabilityBatchPort;
        this.cachePort = cachePort;
        this.circuitBreaker = circuitBreaker;
        this.taskExecutor = taskExecutor;
        this.softTtl = Duration.ofSeconds(softTtlSeconds);
        this.hardTtl = Duration.ofSeconds(hardTtlSeconds);
        this.lockTimeout = Duration.ofSeconds(lockTimeoutSeconds);
    }

    @Override
    public CursorPage<WorkshopCard> search(SearchWorkshopsQuery query) {
        String sortField =
            query.sort() != null && !query.sort().isBlank()
                ? query.sort().split(",")[0].trim()
                : "startsAt";
        KeysetCursor decodedCursor = KeysetCursorCodec.decode(
            query.cursor(),
            sortField
        );

        String cacheKey = buildCacheKey(query);
        Optional<SwrCacheEntry> cacheOpt = cachePort.get(cacheKey);

        if (cacheOpt.isPresent()) {
            SwrCacheEntry entry = cacheOpt.get();
            Duration age = Duration.between(entry.cachedAt(), Instant.now());
            if (age.compareTo(softTtl) <= 0) {
                log.debug("SWR cache fresh hit for key: {}", cacheKey);
                return entry.result();
            }

            log.debug("SWR cache stale hit for key: {}", cacheKey);
            String lockKey = "lock:" + cacheKey;
            boolean acquired = cachePort.tryLock(lockKey, lockTimeout);
            if (acquired) {
                CompletableFuture.runAsync(() -> {
                    try {
                        fetchAndCache(query, decodedCursor, cacheKey);
                    } catch (Exception e) {
                        log.warn(
                            "Background SWR refresh failed for key {}: {}",
                            cacheKey,
                            e.getMessage()
                        );
                    } finally {
                        cachePort.releaseLock(lockKey);
                    }
                }, taskExecutor);
            }
            return entry.result();
        }

        return fetchAndCache(query, decodedCursor, cacheKey);
    }

    private CursorPage<WorkshopCard> fetchAndCache(
        SearchWorkshopsQuery query,
        KeysetCursor decodedCursor,
        String cacheKey
    ) {
        try {
            CursorPage<WorkshopCard> esResult = circuitBreaker.executeSupplier(
                () -> searchRepository.search(query, decodedCursor)
            );

            Map<UUID, Integer> seatsMap =
                availabilityBatchPort.resolveAvailableSeats(esResult.items());
            List<WorkshopCard> enrichedItems = esResult
                .items()
                .stream()
                .map(card ->
                    new WorkshopCard(
                        card.id(),
                        card.slug(),
                        card.title(),
                        card.coverImageUrl(),
                        card.location(),
                        card.startsAt(),
                        card.endsAt(),
                        card.timezone(),
                        card.price(),
                        card.currency(),
                        card.capacity(),
                        Math.max(
                            0,
                            seatsMap.getOrDefault(
                                card.id(),
                                card.capacity() != null ? card.capacity() : 0
                            )
                        ),
                        card.priceType(),
                        card.organizationId()
                    )
                )
                .toList();

            CursorPage<WorkshopCard> enrichedResult = new CursorPage<>(
                enrichedItems,
                esResult.nextCursor(),
                esResult.hasMore()
            );

            cachePort.put(
                cacheKey,
                new SwrCacheEntry(Instant.now(), enrichedResult),
                hardTtl
            );
            return enrichedResult;
        } catch (CallNotPermittedException e) {
            log.warn(
                "Elasticsearch circuit breaker is OPEN: {}",
                e.getMessage()
            );
            return CursorPage.empty();
        } catch (Exception e) {
            log.error(
                "Elasticsearch query failed, returning graceful degraded empty response: {}",
                e.getMessage(),
                e
            );
            return CursorPage.empty();
        }
    }

    private String buildCacheKey(SearchWorkshopsQuery query) {
        String qPart =
            query.q() != null && !query.q().isBlank()
                ? query.q().trim()
                : "all";
        String filterPart = String.format(
            "%s_%s_%s",
            query.priceType() != null ? query.priceType() : "*",
            query.from() != null ? query.from().toString() : "*",
            query.to() != null ? query.to().toString() : "*"
        );
        String sortPart = query.sort();
        String cursorPart = query.cursor() != null ? query.cursor() : "none";
        int sizePart = query.size();
        return String.format(
            "search:q:%s:%s:%s:%s:%d",
            qPart,
            filterPart,
            sortPart,
            cursorPart,
            sizePart
        );
    }
}
