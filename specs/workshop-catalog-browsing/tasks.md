# Implementation Plan: Workshop Catalog Browsing & Full-Text Search

> **Spec:** `specs/workshop-catalog-browsing/SPEC.md`  
> **API Contract:** `specs/workshop-catalog-browsing/API_CONTRACT.md`  
> **Status:** APPROVED

## Overview

This plan builds the public-facing workshop discovery surface as three interconnected capabilities across two deployable apps:

1. The `search_sync` background worker (`apps/workers/`) that maintains the Elasticsearch index and Redis availability counters from workshop lifecycle events — this is the data producer that makes the two REST endpoints below meaningful.
2. The public catalog listing and full-text search endpoint `GET /api/v1/workshops` served by the `search` Spring Modulith module with SWR Redis cache and Elasticsearch.
3. The public individual workshop detail endpoint `GET /api/v1/workshops/{identifier}` served by the `workshops` Spring Modulith module from the PostgreSQL Read Replica.

Tasks are sequenced: the worker (T1) is built first because the ES index it maintains is the data source for the catalog endpoint (T2), and Redis availability counters it writes are consumed by both endpoints. T3 (detail endpoint) can be built in parallel with T2 in principle but is ordered after T2 to reuse any Redis `availableSeats` infrastructure established in T2.

## Architecture Decisions for This Plan

- **Targeted Test Execution Rule (User Directive):** During `/build`, strictly run unit and component tests for the specific classes and files touched within the active task (e.g., `./gradlew :apps:workers:test --tests "com.unihub.workshop.workers.search_sync.*"` or `./gradlew :apps:backend:test --tests "com.unihub.workshop.search.*"`). Never run full test suites across whole modules or the entire repository; the human reviewer runs full end-to-end and regression tests independently.
- **Worker-first sequencing:** The `search_sync` worker (T1) is built before the catalog endpoint (T2) because T2's Elasticsearch data source and Redis availability counters originate from T1. Building T2 first would require mocking the entire ES data layer; building T1 first means T2 can be tested with a real ES document lifecycle in mind.
- **`WorkshopPublishedEvent` additive extension with speakers in T1:** Per human confirmation, `WorkshopPublishedEvent` is extended with `List<SpeakerPayload> speakers` in `shared/events/`, and `PublishWorkshopUseCaseImpl` is updated to supply it, providing full speaker data for the ES indexer.
- **`WorkshopCompletedEvent` handled in T1 despite no publisher yet:** Per confirmed architecture, the worker consumer for `WorkshopCompletedEvent` is implemented now; the event publisher (Feature 2.5) is a future concern. The consumer is covered by a unit test that exercises it with a mock payload.
- **`search` module scaffolded in T2 as shared foundation:** The `search` Spring Modulith module does not yet exist. Its module scaffold (`package-info.java`, `allowedDependencies`) is part of T2 — the first task that needs it — per the skill's "assign shared groundwork to the first capability that needs it" rule.
- **Redis SWR cache and Elasticsearch client config treated as part of T2's infrastructure layer:** No standalone "infrastructure setup" task exists; all config that T2 needs is introduced as part of T2's vertical slice. T3 reuses the Redis client already wired in T2.
- **`availableSeats` Redis batch MGET in T2, single GET in T3:** Both tasks add Redis-backed availability resolution. T2 introduces the batch port and adapter (for paged results); T3 reuses the same port for a single-key lookup. Redis key `workshop:{id}:capacity` directly holds remaining seats (no subtraction needed).
- **No Flyway migration needed:** This feature adds no new PostgreSQL tables or columns. No migration task is required.

## Task List

### Phase 1: search_sync Worker — Elasticsearch & Redis Maintenance

> **Rationale:** T1 is the data producer for both public endpoints. Building it first establishes all five event-to-index lifecycle paths and the Redis capacity counter write, so T2 and T3 have a clear, verified upstream.

- [x] **T1 — Elasticsearch index is kept in sync with the workshop lifecycle via the `search_sync` RabbitMQ consumer worker**
  - **Size:** M
  - **Depends on:** — (no prior task; existing `WorkshopPublishedEvent`, `WorkshopUpdatedEvent`, `WorkshopRescheduledEvent`, `WorkshopCanceledEvent` in `shared/events/` are already present; `WorkshopCompletedEvent` must be added to `shared/events/`)
  - **Layers touched:** Inbound (RabbitMQ listener), Core (sync use case, port interfaces, domain models), Outbound (Elasticsearch client adapter, Redis cache invalidation adapter, worker config)
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/shared/events/WorkshopCompletedEvent.java` _(new)_
    - `apps/backend/src/main/java/com/unihub/workshop/shared/events/WorkshopPublishedEvent.java` _(modify — add additive `List<SpeakerPayload> speakers` record)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/usecases/PublishWorkshopUseCaseImpl.java` _(modify — populate speaker list in published event)_
    - `apps/backend/src/test/java/com/unihub/workshop/workshops/application/usecases/PublishWorkshopUseCaseImplTest.java` _(modify — verify speakers in published event)_
    - `apps/workers/src/main/java/com/unihub/workshop/workers/search_sync/inbound/SearchSyncListener.java` _(new)_
    - `apps/workers/src/main/java/com/unihub/workshop/workers/search_sync/inbound/mapper/SearchSyncEventMapper.java` _(new)_
    - `apps/workers/src/main/java/com/unihub/workshop/workers/search_sync/core/models/WorkshopDocument.java` _(new)_
    - `apps/workers/src/main/java/com/unihub/workshop/workers/search_sync/core/ports/in/SyncWorkshopIndex.java` _(new)_
    - `apps/workers/src/main/java/com/unihub/workshop/workers/search_sync/core/ports/out/WorkshopIndexStore.java` _(new)_
    - `apps/workers/src/main/java/com/unihub/workshop/workers/search_sync/core/ports/out/SearchCacheInvalidator.java` _(new)_
    - `apps/workers/src/main/java/com/unihub/workshop/workers/search_sync/core/ports/out/WorkshopCapacityStore.java` _(new)_
    - `apps/workers/src/main/java/com/unihub/workshop/workers/search_sync/core/usecases/SearchSyncService.java` _(new)_
    - `apps/workers/src/main/java/com/unihub/workshop/workers/search_sync/outbound/elasticsearch/ElasticsearchWorkshopIndexAdapter.java` _(new)_
    - `apps/workers/src/main/java/com/unihub/workshop/workers/search_sync/outbound/elasticsearch/WorkshopDocumentMapper.java` _(new)_
    - `apps/workers/src/main/java/com/unihub/workshop/workers/search_sync/outbound/cache/RedisSearchCacheInvalidator.java` _(new)_
    - `apps/workers/src/main/java/com/unihub/workshop/workers/search_sync/outbound/cache/RedisWorkshopCapacityAdapter.java` _(new)_
    - `apps/workers/src/main/java/com/unihub/workshop/workers/search_sync/outbound/config/SearchSyncConfig.java` _(new — RabbitMQ queue binding, ES client bean, Redis properties)_
    - `apps/workers/src/test/java/com/unihub/workshop/workers/search_sync/core/usecases/SearchSyncServiceTest.java` _(new)_
    - `apps/workers/src/test/java/com/unihub/workshop/workers/search_sync/inbound/SearchSyncListenerTest.java` _(new)_
  - **Acceptance Criteria:**
    - Given a `WorkshopPublishedEvent` is delivered to the worker via the mocked RabbitMQ channel, when `SearchSyncService` processes it, then `WorkshopIndexStore.upsert()` is called with a `WorkshopDocument` whose `id`, `title`, `slug`, `capacity`, and `speakers` match the event payload, AND `WorkshopCapacityStore.set()` is called with `workshopId` and `capacity`. _(FR-24, FR-26)_
    - Given a `WorkshopUpdatedEvent` is delivered, when `SearchSyncService` processes it, then `WorkshopIndexStore.upsert()` is called with the updated fields AND `SearchCacheInvalidator.invalidateAll()` is called. _(FR-24, FR-27)_
    - Given a `WorkshopRescheduledEvent` is delivered, when `SearchSyncService` processes it, then `WorkshopIndexStore.upsert()` is called with the new `startsAt`/`endsAt`/`timezone` AND `SearchCacheInvalidator.invalidateAll()` is called. _(FR-24, FR-27)_
    - Given a `WorkshopCompletedEvent` is delivered, when `SearchSyncService` processes it, then `WorkshopIndexStore.delete()` is called with the `workshopId` AND `SearchCacheInvalidator.invalidateAll()` is called. _(FR-25, FR-27)_
    - Given a `WorkshopCanceledEvent` is delivered, when `SearchSyncService` processes it, then `WorkshopIndexStore.delete()` is called with the `workshopId` AND `SearchCacheInvalidator.invalidateAll()` is called. _(FR-25, FR-27)_
    - Given the same event is delivered twice (duplicate delivery), when `SearchSyncService` processes both, then both calls complete without error (idempotent upsert / delete no-op). _(FR-25)_
    - Given a malformed or unknown-type message arrives at the listener, the listener NACKs without requeue and does not propagate the exception to crash the worker process. _(API_CONTRACT.md Worker Error Handling)_
    - `HexagonalArchitectureTest` passes after T1 is merged (`apps/workers/`).

#### Checkpoint: Phase 1

- [x] All acceptance criteria for T1 pass; unit tests for `SearchSyncService` and `SearchSyncListener` are green.
- [x] `WorkshopCompletedEvent` is present in `apps/backend/src/main/java/com/unihub/workshop/shared/events/` and correctly annotated with `@Externalized`.
- [x] `HexagonalArchitectureTest` passes with no violations in the new `search_sync` worker package.
- [x] Build is clean; no compilation errors in either `apps/backend/` or `apps/workers/`.

### Phase 2: Public Catalog Listing & Full-Text Search Endpoint

> **Rationale:** T2 introduces the new `search` Spring Modulith module from scratch, so it carries the most new infrastructure (ES query client, SWR cache port/adapter, `search` module scaffold, `ModulithArchitectureTest` enforcement). It is the highest-risk capability and is built early in its own phase to surface any integration problems before T3 builds on top of it.

- [ ] **T2 — Callers can browse and search the catalog of PUBLISHED workshops via `GET /api/v1/workshops`, served from Elasticsearch with SWR Redis cache**
  - **Size:** M
  - **Depends on:** T1 (the ES index shape and Redis capacity key format established by T1 are consumed here)
  - **Layers touched:** Domain (query model, cursor, filter/sort value objects), Application (use case port, `availableSeats` batch port), Infrastructure (ES query adapter + SWR Redis cache adapter + config), Presentation (`WorkshopSearchController`, request/response DTOs, mappers), Config (`search` module `package-info.java`, `application.yml` public-paths + ES properties)
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/search/package-info.java` _(new — `@ApplicationModule(allowedDependencies = {"shared", "workshops::api"})`)_
    - `apps/backend/src/main/java/com/unihub/workshop/search/domain/model/WorkshopSearchResult.java` _(new — catalog card projection)_
    - `apps/backend/src/main/java/com/unihub/workshop/search/domain/model/CatalogQuery.java` _(new — value object: q, priceType, from, to, sort, cursor, size)_
    - `apps/backend/src/main/java/com/unihub/workshop/search/application/ports/in/SearchWorkshopsUseCase.java` _(new)_
    - `apps/backend/src/main/java/com/unihub/workshop/search/application/ports/out/WorkshopSearchRepository.java` _(new — ES query port)_
    - `apps/backend/src/main/java/com/unihub/workshop/search/application/ports/out/WorkshopAvailabilityBatchPort.java` _(new — Redis MGET port)_
    - `apps/backend/src/main/java/com/unihub/workshop/search/application/usecases/SearchWorkshopsUseCaseImpl.java` _(new — SWR logic: cache hit/stale/miss, tryLock, circuit breaker fallback)_
    - `apps/backend/src/main/java/com/unihub/workshop/search/infrastructure/elasticsearch/ElasticsearchWorkshopSearchAdapter.java` _(new — ES Bool Query builder + search_after cursor)_
    - `apps/backend/src/main/java/com/unihub/workshop/search/infrastructure/cache/RedisSwrCacheAdapter.java` _(new — SWR GET/SET/tryLock with soft+hard TTL)_
    - `apps/backend/src/main/java/com/unihub/workshop/search/infrastructure/cache/RedisWorkshopAvailabilityBatchAdapter.java` _(new — MGET `workshop:{id}:capacity` + DB fallback port call)_
    - `apps/backend/src/main/java/com/unihub/workshop/search/infrastructure/config/SearchModuleConfig.java` _(new — ES client bean, Resilience4j CB instance, SWR TTL properties)_
    - `apps/backend/src/main/java/com/unihub/workshop/search/presentation/controllers/WorkshopSearchController.java` _(new — `GET /api/v1/workshops`)_
    - `apps/backend/src/main/java/com/unihub/workshop/search/presentation/dto/WorkshopCatalogRequest.java` _(new — query param binding + validation)_
    - `apps/backend/src/main/java/com/unihub/workshop/search/presentation/dto/WorkshopCardResponse.java` _(new — `PublicWorkshopCard` JSON shape)_
    - `apps/backend/src/main/java/com/unihub/workshop/search/presentation/mapper/WorkshopSearchPresentationMapper.java` _(new — MapStruct)_
    - `apps/backend/src/main/resources/application.yml` _(modify — add `/api/v1/workshops` and `/api/v1/workshops/**` to `app.security.public-paths`; add ES host/port/index-name config keys)_
    - `apps/backend/src/test/java/com/unihub/workshop/search/application/usecases/SearchWorkshopsUseCaseImplTest.java` _(new)_
    - `apps/backend/src/test/java/com/unihub/workshop/search/presentation/controllers/WorkshopSearchControllerTest.java` _(new)_
    - `apps/backend/src/test/java/com/unihub/workshop/ModulithArchitectureTest.java` _(modify — assert `search` module in declared module set; `writeModulesMustNotDependOnCqrsSearch()` rule)_
  - **Acceptance Criteria:**
    - `GET /api/v1/workshops` with no query parameters returns `200 OK` with the standard success envelope; `data.items` is a JSON array and `data.cursor` contains `nextCursor` and `hasMore`. _(FR-01, FR-02, FR-04, FR-13 — Scenario 1)_
    - `GET /api/v1/workshops?q=machine+learning` returns `200 OK`; the use case invokes `WorkshopSearchRepository.search()` with a `CatalogQuery` that has `q = "machine learning"` (verified via mock). _(FR-03 — Scenario 2)_
    - `GET /api/v1/workshops?priceType=FREE` returns `200 OK`; the query passed to ES contains the `priceType` filter (verified via mock on the adapter). _(FR-05 — Scenario 3)_
    - `GET /api/v1/workshops?from=2026-11-01T00:00:00Z&to=2026-11-30T23:59:59Z` returns `200 OK` (from < to). `GET /api/v1/workshops?from=2026-11-01T00:00:00Z` alone returns `200 OK` (one-sided bound). _(FR-05, BR-15 — Scenarios 4, 21)_
    - `GET /api/v1/workshops?from=2026-12-01T00:00:00Z&to=2026-11-01T00:00:00Z` (from >= to) returns `422 Unprocessable Content` with `urn:unihub:error:validation`. _(BR-15 — Scenario 22)_
    - `GET /api/v1/workshops?from=not-a-date` returns `422 Unprocessable Content` with `urn:unihub:error:validation`. _(BR-15 — Scenario 23)_
    - `GET /api/v1/workshops?size=200` returns `422 Unprocessable Content` with `urn:unihub:error:validation`. _(BR-13)_
    - `GET /api/v1/workshops?cursor=<stale-cursor>&sort=price,asc` where `cursor.sortField != price` returns `400 Bad Request`. _(BR-09 — Scenario 9)_
    - `GET /api/v1/workshops?cursor=!!invalid-base64!!` returns `400 Bad Request`. _(BR-10 — Scenario 10)_
    - When the SWR cache returns a stale hit (mocked), the use case returns the cached result immediately and submits an async background refresh without blocking the response. _(FR-10 — Scenario 7)_
    - When Resilience4j Circuit Breaker is forced OPEN (mocked), `GET /api/v1/workshops` returns `200 OK` with `data.items = []` and `hasMore = false`. _(FR-12, BR-05 — Scenario 8)_
    - `GET /api/v1/workshops` is accessible without an `Authorization` header (no `401` or `403`). _(FR-01, BR-02)_
    - `ModulithArchitectureTest` passes with the new `search` module declared and the `writeModulesMustNotDependOnCqrsSearch()` rule enforced.

#### Checkpoint: Phase 2

- [ ] All acceptance criteria for T2 pass; unit tests for `SearchWorkshopsUseCaseImpl` and `WorkshopSearchController` are green.
- [ ] `GET /api/v1/workshops` is reachable as a public path (no auth error with no `Authorization` header).
- [ ] `ModulithArchitectureTest` passes with no architecture violations in the new `search` module.
- [ ] Build is clean in `apps/backend/`.

### Phase 3: Public Workshop Detail Endpoint

> **Rationale:** T3 adds a single read endpoint to the existing `workshops` module. It is lower risk than T2 (the module already exists with full infrastructure, JPA entities, and repo) and has no novel infrastructure beyond reusing the Redis availability key pattern from T1.

- [ ] **T3 — Callers can retrieve the full detail of a single PUBLISHED workshop by UUID or slug via `GET /api/v1/workshops/{identifier}`**
  - **Size:** M
  - **Depends on:** T1 (Redis capacity key `workshop:{id}:capacity` written by `search_sync`; its format is consumed here for `availableSeats`)
  - **Layers touched:** Domain (no new domain model; reuses existing `Workshop` + `WorkshopStatus`), Application (new use case port + query, availability single-key port), Infrastructure (new persistence read adapter via replica routing; Redis single-key adapter), Presentation (`PublicWorkshopDetailController`, response DTOs, mapper)
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/ports/in/GetPublicWorkshopQuery.java` _(new — `identifier: String`)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/ports/in/GetPublicWorkshopUseCase.java` _(new)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/ports/out/WorkshopAvailabilityPort.java` _(new — single-key Redis GET + DB fallback port)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/usecases/GetPublicWorkshopUseCaseImpl.java` _(new — UUID-vs-slug detection, PUBLISHED-only gate, `availableSeats` resolution)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/infrastructure/cache/RedisWorkshopAvailabilityAdapter.java` _(new — single-key GET `workshop:{id}:capacity` + DB fallback)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/presentation/controllers/PublicWorkshopDetailController.java` _(new — `GET /api/v1/workshops/{identifier}`)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/presentation/dto/PublicWorkshopDetailResponse.java` _(new — full detail shape including speakers array)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/presentation/dto/PublicSpeakerResponse.java` _(new)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/presentation/mapper/PublicWorkshopDetailPresentationMapper.java` _(new — MapStruct)_
    - `apps/backend/src/test/java/com/unihub/workshop/workshops/application/usecases/GetPublicWorkshopUseCaseImplTest.java` _(new)_
    - `apps/backend/src/test/java/com/unihub/workshop/workshops/presentation/controllers/PublicWorkshopDetailControllerTest.java` _(new)_
  - **Acceptance Criteria:**
    - `GET /api/v1/workshops/{uuid}` for an existing PUBLISHED workshop returns `200 OK` with the standard success envelope; `data` contains all `PublicWorkshopDetail` fields including `status = "PUBLISHED"`, `speakers` array ordered by `sortOrder ASC`, and a non-negative integer `availableSeats`. _(FR-16, FR-17, FR-20, FR-22 — Scenario 11)_
    - `GET /api/v1/workshops/{slug}` for the same workshop returns the identical response as the UUID lookup (identifier auto-detection). _(FR-17 — Scenario 12)_
    - `GET /api/v1/workshops/nonexistent-slug` returns `404 Not Found` with `urn:unihub:error:not-found`. _(FR-18 — Scenario 13)_
    - `GET /api/v1/workshops/{id-of-draft-workshop}` returns `404 Not Found` with `urn:unihub:error:not-found` — same shape as non-existent (prevents enumeration). _(FR-19 — Scenario 14)_
    - When Redis returns a value for `workshop:{id}:capacity`, `availableSeats` is read directly from the Redis counter (which stores remaining capacity, clamped to `≥ 0`), without any DB query for registrations. _(FR-14, FR-22, BR-06)_
    - When Redis returns a miss for `workshop:{id}:capacity`, `availableSeats` falls back to `capacity - COUNT(CONFIRMED + PENDING)` from DB and is clamped to `≥ 0`. _(FR-14, FR-22, BR-06, BR-10)_
    - `GET /api/v1/workshops/{identifier}` is accessible without an `Authorization` header (no `401` or `403`). _(FR-16, BR-02)_
    - `ModulithArchitectureTest` passes with no architecture violations after T3 (no new module; existing `workshops` module boundaries unchanged).

#### Checkpoint: Feature Complete

- [ ] All tasks T1, T2, T3 are checked off.
- [ ] Every acceptance criterion across all tasks has been verified with passing targeted unit tests.
- [ ] `ModulithArchitectureTest` passes clean in `apps/backend/`.
- [ ] `HexagonalArchitectureTest` passes clean in `apps/workers/`.
- [ ] Targeted test suites for touched classes in both apps pass (`SearchSyncServiceTest`, `SearchSyncListenerTest`, `SearchWorkshopsUseCaseImplTest`, `WorkshopSearchControllerTest`, `GetPublicWorkshopUseCaseImplTest`, `PublicWorkshopDetailControllerTest`, `PublishWorkshopUseCaseImplTest`).
- [ ] No compilation errors or warnings in either app.
- [ ] Ready for `/review workshop-catalog-browsing`.

## Risks and Mitigations

| Risk                                                                                                                                              | Impact                                                                                      | Mitigation                                                                                                                                                                                                                                                                     |
| :------------------------------------------------------------------------------------------------------------------------------------------------ | :------------------------------------------------------------------------------------------ | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| ES client library not yet in `apps/backend/` `build.gradle` dependencies                                                                          | High — T2 cannot compile without it                                                         | Verify and add `co.elastic.clients:elasticsearch-java` dependency in T2 as the first step of its implementation                                                                                                                                                                |
| ES client library not yet in `apps/workers/` `build.gradle` dependencies                                                                          | High — T1 cannot compile without it                                                         | Verify and add the ES client dependency in T1 as the first step of its implementation                                                                                                                                                                                          |
| `WorkshopPublishedEvent` payload lacks `speakers` field — the existing event record does not carry speakers                                       | Medium — T1's ES document would be missing speakers on publish; test data is incomplete     | Confirmed by reviewer: during T1, extend `WorkshopPublishedEvent` with an additive `List<SpeakerPayload>` record and update `PublishWorkshopUseCaseImpl` to populate it.                                                                                                       |
| SWR `tryLock` requires a Redis `SET NX PX` pattern — the existing `RedisConfig` may not expose the necessary `RedisTemplate<String, String>` bean | Medium — T2's non-blocking lock logic cannot be built without a correctly typed template    | Inspect `shared/config/RedisConfig.java` in T2 and add a dedicated `StringRedisTemplate` or `ReactiveStringRedisTemplate` bean if not already present                                                                                                                          |
| `search` module `allowedDependencies` conflict with existing `ModulithArchitectureTest` enforcement rules                                         | Medium — Adding `search` to the declared module set may expose hidden violations            | Run `ModulithArchitectureTest` immediately after the `search` module scaffold is created (as the very first step of T2) to surface any violations early, before more code is layered on top                                                                                    |
| `WorkshopCompletedEvent` routing key to `q.search` not yet configured in RabbitMQ exchange bindings                                               | Medium — The `search_sync` worker will not receive the event even if the record is declared | During T1 config, verify that the exchange binding in `SearchSyncConfig.java` includes a queue binding for the `workshop.completed` routing key alongside the existing `search` routing key                                                                                    |
| Redis availability key (`workshop:{id}:capacity`) semantics: the key stores remaining seats, not total capacity                                   | Low-Medium — T2 and T3 may misinterpret the Redis value                                     | Confirmed: the key directly stores the **remaining seat count** decremented by registrations (per ADR-013); the `SET` on `WorkshopPublishedEvent` seeds it with `capacity` (full availability). T2 and T3 read the value directly as `availableSeats` (no subtraction needed). |

## Open Questions

None. All questions have been resolved and confirmed by the human reviewer.
