# Implementation Plan: Workshop Publishing and Update

> **Spec:** `specs/workshop-publishing-and-update/SPEC.md`  
> **API Contract:** `specs/workshop-publishing-and-update/API_CONTRACT.md`  
> **Status:** APPROVED

## Overview

This plan delivers Feature 2.2 in two vertical slices, each representing one independently testable capability that a real caller can exercise end-to-end:

1. **T1 — Publish Workshop** introduces the `DRAFT → PUBLISHED` lifecycle transition. It adds the `Workshop.publish()` domain method, the `PublishWorkshopUseCase` port and implementation, the `WorkshopPublishedEvent` outbox event, the missing `publishedAt` field in `WorkshopResponse`, and the `PATCH .../publish` controller handler — all in one slice.
2. **T2 — Update Published Workshop** introduces partial mutation of presentation and capacity fields on a `PUBLISHED` workshop. It adds the `WorkshopRegistrationCountPort` SPI port in `workshops/api/` and its implementation `WorkshopRegistrationCountAdapter` in the `registrations` module (which queries its own `registrations` table within its bounded context), the `Workshop.updatePublished()` domain method, the `UpdateWorkshopUseCase` port and implementation, the `UpdateWorkshopRequest` DTO and `WorkshopUpdatedEvent` event, MinIO cover-image cleanup, and the `PUT .../{id}` controller handler — all in one slice.

T1 must complete before T2 because T2's use case validates that the workshop is in `PUBLISHED` status, and its test suite needs workshops that can actually be published.

## Architecture Decisions for This Plan

- **`WorkshopRegistrationCountPort` goes into `workshops/api/` as a public Service Provider Interface (SPI)**, and its implementation goes into `com.unihub.workshop.registrations.infrastructure.adapters.WorkshopRegistrationCountAdapter` inside the `registrations` module — this enforces NFR-06 (modular purity & acyclic dependency), upholds data ownership, and eliminates database backdoor coupling and cyclic module dependencies (`workshops <-> registrations`).
- **`WorkshopPublishedEvent` and `WorkshopUpdatedEvent` go into `shared/events/`** following the established pattern of all other `@Externalized` Transactional Outbox events (e.g., `StaffAssignedToWorkshopEvent`).
- **No new Flyway migration is required for either task** — the `workshops` table already has `published_at`, `updated_at`, `updated_by`, and `version` columns in `V1__init_schema.sql`.
- **`publishedAt` addition to `WorkshopResponse`** is part of T1 (not a separate task) because the publish endpoint returns a `WorkshopResponse`, and returning it without `publishedAt` would produce a spec-incomplete response even in a "minimally working" T1.

## Task List

### Phase 1: Core Publish Flow

- [x] **T1 — ORGANIZER can publish a DRAFT workshop and receive the full `PUBLISHED` response with `publishedAt`**
  - **Size:** M
  - **Depends on:** — (no prior task in this feature)
  - **Layers touched:** Domain, Application, Infrastructure (Events), Presentation
  - **Files:**
    - `workshops/domain/model/Workshop.java` — add `publish(UUID updatedBy, Instant now)` method
    - `workshops/application/ports/in/PublishWorkshopCommand.java` — new command record (`workshopId`, `organizationId`, `callerId`, `version`)
    - `workshops/application/ports/in/PublishWorkshopUseCase.java` — new use case interface
    - `workshops/application/usecases/PublishWorkshopUseCaseImpl.java` — new implementation (loads workshop, validates status + mandatory fields + future start + speaker collisions, calls `Workshop.publish()`, saves, publishes event)
    - `shared/events/WorkshopPublishedEvent.java` — new `@Externalized("unihub.events::search")` record
    - `workshops/presentation/dto/WorkshopResponse.java` — add `publishedAt` field
    - `workshops/presentation/mapper/WorkshopPresentationMapper.java` — map `publishedAt`
    - `workshops/presentation/controllers/AdminWorkshopController.java` — add `PATCH /{id}/publish` handler
    - `src/test/java/.../workshops/application/usecases/PublishWorkshopUseCaseImplTest.java` — new unit test
    - `src/test/java/.../workshops/domain/model/WorkshopTest.java` — add `publish()` tests (or create if absent)
  - **Acceptance Criteria:**
    - `PATCH /api/v1/admin/workshops/{id}/publish` with `{"version": 0}` on a complete, future-scheduled `DRAFT` workshop returns `200 OK` with `data.status = "PUBLISHED"`, non-null `data.publishedAt`, and `data.version = 1`. (→ FR-11, FR-13, Scenario 1)
    - Same request on a workshop with missing `location` returns `422 Unprocessable Content` with `validationErrors` identifying `location`. (→ FR-08, Scenario 3)
    - Same request on a workshop whose `startsAt <= now()` returns `422 Unprocessable Content`. (→ FR-09, Scenario 4)
    - Same request with speaker whose email overlaps another `PUBLISHED` workshop returns `422 Unprocessable Content` with the conflicting email identified. (→ FR-10, Scenario 5)
    - Same request on an already `PUBLISHED` workshop returns `422 Unprocessable Content`. (→ FR-07, Scenario 6)
    - Same request with a stale `version` returns `409 Conflict`. (→ FR-06, Scenario 2)
    - Request without a JWT returns `401 Unauthorized`. (→ FR-01)
    - Request from an ORGANIZER whose `org` claim differs from the workshop's `organization_id` returns `403 Forbidden`. (→ FR-03, Scenario 11)
    - Request targeting a non-existent `id` returns `404 Not Found`. (→ FR-04)
    - A row exists in the `event_publication` table for the published workshop after a successful call. (→ FR-12)

#### Checkpoint: Publish Flow

- [x] T1 unit tests pass (`./gradlew test --tests "com.unihub.workshop.workshops.application.usecases.PublishWorkshopUseCaseImplTest"` and `WorkshopTest`).
- [x] `ModulithArchitectureTest` passes.
- [x] No compilation errors or warnings in the `workshops` module.

### Phase 2: Update Published Workshop Flow

- [x] **T2 — ORGANIZER can partially update a PUBLISHED workshop's presentation and capacity fields and receive the full updated response**
  - **Size:** M
  - **Depends on:** T1
  - **Layers touched:** Domain, Application, Infrastructure (Persistence Adapter, Events), Presentation
  - **Files:**
    - `workshops/domain/model/Workshop.java` — add `updatePublished(...)` method accepting partial-update nullable parameters
    - `workshops/api/package-info.java` — declares `@NamedInterface("api")` exposing the public SPI of `workshops`
    - `workshops/api/WorkshopRegistrationCountPort.java` — public SPI port (`long countActiveRegistrations(UUID workshopId)`)
    - `registrations/package-info.java` — declares `@ApplicationModule(allowedDependencies = { "shared", "workshops::api" })`
    - `registrations/infrastructure/adapters/WorkshopRegistrationCountAdapter.java` — SPI implementation in `registrations` module querying its own `registrations` table
    - `workshops/application/ports/in/UpdateWorkshopCommand.java` — new command record (`workshopId`, `organizationId`, `callerId`, `version`, nullable `title`, `description`, `coverImageUrl`, `capacity`)
    - `workshops/application/ports/in/UpdateWorkshopUseCase.java` — new use case interface
    - `workshops/application/usecases/UpdateWorkshopUseCaseImpl.java` — new implementation (validates status=PUBLISHED, version, prohibited fields, capacity floor via SPI port, slug regeneration, MinIO cleanup, `Workshop.updatePublished()`, saves, publishes event)
    - `shared/events/WorkshopUpdatedEvent.java` — new `@Externalized("unihub.events::search")` record
    - `workshops/presentation/dto/UpdateWorkshopRequest.java` — new DTO (`version` required; all other fields optional)
    - `workshops/presentation/controllers/AdminWorkshopController.java` — add `PUT /{id}` handler
    - `src/test/java/.../workshops/application/usecases/UpdateWorkshopUseCaseImplTest.java` — new unit test
    - `src/test/java/.../workshops/domain/model/WorkshopTest.java` — add `updatePublished()` tests
    - `src/test/java/.../registrations/infrastructure/adapters/WorkshopRegistrationCountAdapterTest.java` — unit test for the registrations SPI adapter
  - **Acceptance Criteria:**
    - `PUT /api/v1/admin/workshops/{id}` with `{"version":1, "title":"New Title", "capacity":150}` on a `PUBLISHED` workshop returns `200 OK` with `data.title = "New Title"`, `data.slug` synchronized to the new title, `data.capacity = 150`, and `data.version = 2`. (→ FR-17, FR-18, FR-22, FR-25, Scenario 7)
    - Same request with only `{"version":1, "capacity":200}` (no other fields) returns `200 OK` with the existing `title` and `slug` unchanged and `data.capacity = 200`. (→ FR-17 partial semantics, Scenario 13)
    - Same request with `"capacity": 50` when active registrations = 70 returns `422 Unprocessable Content` identifying that capacity cannot be reduced below 70. (→ FR-20, Scenario 8)
    - Same request including `"price": 25.00` returns `422 Unprocessable Content` identifying `price` as a prohibited field. (→ FR-21, Scenario 9)
    - Same request on a `DRAFT` workshop returns `422 Unprocessable Content` directing the caller to `POST /api/v1/admin/workshops/drafts`. (→ FR-16, Scenario 10)
    - Same request with a stale `version` returns `409 Conflict`. (→ FR-15)
    - Same request where `coverImageUrl` is changed from an existing MinIO-hosted URL results in deletion of the previous object from MinIO storage. (→ FR-19, Scenario 12)
    - A row exists in the `event_publication` table for the updated workshop after a successful call. (→ FR-23)
    - No student notification event is emitted on a successful update. (→ FR-24)
    - Request from an ORGANIZER whose `org` claim differs from the workshop's `organization_id` returns `403 Forbidden`. (→ FR-03, Scenario 11)

#### Checkpoint: Feature Complete

- [x] All tasks above are checked off.
- [x] Every acceptance criterion across all tasks has been verified end-to-end.
- [x] `ModulithArchitectureTest` passes.
- [x] Ready for `/review`.

## Risks and Mitigations

| Risk                                                                                                                                                                                                                                                                                                                         | Impact | Mitigation                                                                                                                                                                                                                                                                                                                                                              |
| :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :----- | :---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Module autonomy and dependency cycles: if `workshops` queried `registrations` or imported its classes directly, it would introduce database backdoor access or cyclic dependencies (`workshops <-> registrations`).                                                                                                          | High   | Place `WorkshopRegistrationCountPort` in `workshops/api/` as a Service Provider Interface (SPI). `registrations` implements this port in its own infrastructure layer (`WorkshopRegistrationCountAdapter`), querying its own `registrations` table natively. Spring Modulith verifies this acyclic DAG (`registrations -> workshops::api`) with zero circular coupling. |
| `Workshop.publish()` and `Workshop.updatePublished()` are new domain methods on an immutable `record`. The `toBuilder()` pattern must be used correctly or a new `builder()` factory method must be added.                                                                                                                   | Medium | Follow the existing `updateDraft(...)` method pattern at line 121–214 of `Workshop.java` which already uses `toBuilder()`. Verify the builder output compiles before writing the use case.                                                                                                                                                                              |
| Slug collision on `title` update: if the new title generates a slug already owned by another workshop, the existing retry logic in `SlugGeneratorPort` / `SaveDraftUseCaseImpl` must be reused, not reimplemented.                                                                                                           | Medium | Inject and reuse `SlugGeneratorPort` (already in `application/ports/out/`) in `UpdateWorkshopUseCaseImpl`, identical to `SaveDraftUseCaseImpl`.                                                                                                                                                                                                                         |
| Optimistic locking: `@Version` on `WorkshopJpaEntity` increments automatically on `saveAndFlush`. If `PublishWorkshopUseCaseImpl` manually sets `version` in the domain object before saving, it could conflict with JPA's own version management.                                                                           | Medium | Inspect `WorkshopRepositoryAdapter.save()` and `WorkshopPersistenceMapper.toEntity()` carefully. The domain `version` field should be passed through to the JPA entity so JPA's `@Version` check fires correctly on `saveAndFlush`. Do not double-increment.                                                                                                            |
| MinIO cleanup in `UpdateWorkshopUseCaseImpl`: if the DB transaction commits but the MinIO delete call fails, the old object remains. Cleanup must not be inside the `@Transactional` boundary and must be a best-effort, non-blocking call (matching the existing pattern in `SaveDraftUseCaseImpl.cleanupOrphanedMedia()`). | Low    | Mirror the `cleanupOrphanedMedia()` approach in `SaveDraftUseCaseImpl` (line ~444): call `mediaStoragePort.deleteByUrl(oldUrl)` after `repository.save()` returns, outside the main transaction.                                                                                                                                                                        |

## Open Questions

None.
