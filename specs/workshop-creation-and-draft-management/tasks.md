# Implementation Plan: Workshop Creation and Draft Management

> **Spec:** `specs/workshop-creation-and-draft-management/SPEC.md`  
> **API Contract:** `specs/workshop-creation-and-draft-management/API_CONTRACT.md`  
> **Status:** APPROVED

## Overview

This plan delivers Feature 2.1 across six tasks (T1 to T6). Each task is a complete vertical slice ending in a caller-verifiable capability: T1 establishes the entire `workshops` module skeleton and the Create Draft capability; T2 adds the Update Draft flow with optimistic locking and slug re-generation (no speakers yet); T3 adds Speaker validation and cross-workshop schedule overlap detection; T4 delivers shared media storage and workshop media upload & cleanup; T5 delivers workshop details retrieval for organizers; and T6 delivers draft workshop deletion with media purge. The module's `package-info.java` boundary declaration and shared foundations are owned by T1 as foundations every later task depends on.

## Architecture Decisions for This Plan

- **T1 owns all shared foundations** (domain models for `Workshop` and `Speaker`, JPA entities, `WorkshopRepository` outbound port, `WorkshopRepositoryAdapter`, `package-info.java`, `SlugService`, `AdminWorkshopController`) — subsequent tasks build on these without duplicating them.
- **Speaker logic is isolated in T3** rather than merged into T1/T2, because the overlap detection query is the highest-risk piece (index-dependent SQL, CITEXT semantics, intra-request deduplication), and keeping it isolated lets T2 pass cleanly before T3 adds that risk.
- **T2 and T3 share the same `POST /api/v1/admin/workshops/drafts` endpoint** — T2 brings the controller and wires the update use case, T3 extends the use case with speaker handling. The controller is not re-written between tasks; T3 adds to the existing input DTO and use case.
- **No new Flyway migration is created** — the `workshops` and `workshop_speakers` tables already exist in `V1__init_schema.sql` (modified during context-priming). The `CITEXT` extension and all required indexes (`idx_workshop_speakers_email`) are already in place.
- **`SlugService` is a plain Spring `@Service`**, not a domain concept, because it depends on the third-party `com.github.slugify:slugify:3.0.7` library and `NanoId` for uniqueness — infrastructure dependency, not domain logic.
- **T4 reuses the `WorkshopRepository` outbound port** established in T1; it needs no new persistence infrastructure beyond one additional query method.
- **No module facade (`workshops/api/`)** is created in this feature — the `workshops` module has no synchronous callers from other modules in F-2.1. The facade will be created in a future feature when a real caller materializes (YAGNI per `docs/project-structure.md`).
- **Reuses standard `shared.exception.*`** (`NotFoundException`, `ConflictException`, `ValidationException`, `ForbiddenException`) directly in application use cases, exactly like the IAM module, ensuring strict adherence to DRY, YAGNI, and the RFC 7807 error format defined in `docs/api-standards.md`.

## Task List

### Phase 1: Core Create Flow (Foundation)

- [x] **T1 — ORGANIZER can save a new workshop as a DRAFT (no speakers)**
  - **Size:** M
  - **Depends on:** —
  - **Layers touched:** Domain, Application, Infrastructure, Presentation
  - **Files:**
    - `workshops/package-info.java`
    - `workshops/domain/model/Workshop.java`
    - `workshops/domain/model/WorkshopStatus.java`
    - `workshops/application/ports/in/SaveDraftUseCase.java`
    - `workshops/application/ports/out/WorkshopRepository.java`
    - `workshops/application/usecases/SaveDraftUseCaseImpl.java`
    - `workshops/infrastructure/persistence/WorkshopJpaEntity.java`
    - `workshops/infrastructure/persistence/WorkshopJpaRepository.java`
    - `workshops/infrastructure/persistence/WorkshopRepositoryAdapter.java`
    - `workshops/infrastructure/persistence/mapper/WorkshopPersistenceMapper.java`
    - `workshops/infrastructure/config/SlugService.java`
    - `workshops/presentation/controllers/AdminWorkshopController.java`
    - `workshops/presentation/dto/SaveDraftRequest.java`
    - `workshops/presentation/dto/WorkshopResponse.java`
    - `workshops/presentation/mapper/WorkshopPresentationMapper.java`
    - `src/test/java/.../workshops/application/usecases/SaveDraftUseCaseImplTest.java`
    - `src/test/java/.../workshops/infrastructure/config/SlugServiceTest.java`
    - `src/test/java/.../workshops/presentation/controllers/AdminWorkshopControllerCreateTest.java`
  - **Acceptance Criteria:**
    - `POST /api/v1/admin/workshops/drafts` with `{ "title": "My Workshop" }` and no `id` field, from a valid ORGANIZER JWT, returns `201 Created` with `status = "DRAFT"`, a non-null `id`, a non-empty ASCII kebab-case `slug`, `version = 0`, and `organizationId` matching the JWT `org` claim. _(FR-03, FR-04, FR-11, FR-20)_
    - The same request sent with a Vietnamese title (e.g. `"Hội thảo AI"`) returns `201` with a slug containing only ASCII kebab-case characters and a random suffix (e.g. `"hoi-thao-ai-<suffix>"`). _(FR-20, FR-22)_
    - A request with no `Authorization` header returns `401`. _(FR-01)_
    - A request with a JWT whose `role = STUDENT` returns `403`. _(FR-02)_
    - A request with a blank or missing `title` returns `422`. _(FR-11)_
    - `ModulithArchitectureTest` passes (the `workshops` module declares `allowedDependencies = {"shared"}` and the build is clean).

#### Checkpoint: Phase 1

- [x] T1 acceptance criteria are all verified end-to-end against a running application.
- [x] `ModulithArchitectureTest` passes.
- [x] Unit test suite for T1 is green with no skips.

### Phase 2: Update Draft + Slug Re-Generation

- [x] **T2 — ORGANIZER can update an existing DRAFT workshop's core fields (title, metadata, no speakers)**
  - **Size:** M
  - **Depends on:** T1
  - **Layers touched:** Domain, Application, Infrastructure, Presentation
  - **Files:**
    - `workshops/application/usecases/SaveDraftUseCaseImpl.java` _(extended — update branch)_
    - `workshops/application/ports/out/WorkshopRepository.java` _(extended — `findById` + `save`)_
    - `workshops/infrastructure/persistence/WorkshopRepositoryAdapter.java` _(extended)_
    - `workshops/presentation/dto/SaveDraftRequest.java` _(extended — `id`, `version` fields)_
    - `workshops/presentation/mapper/WorkshopPresentationMapper.java` _(extended)_
    - `src/test/java/.../workshops/application/usecases/SaveDraftUseCaseImplTest.java` _(extended — update scenarios)_
    - `src/test/java/.../workshops/presentation/controllers/AdminWorkshopControllerUpdateTest.java` _(new)_
  - **Acceptance Criteria:**
    - `POST /api/v1/admin/workshops/drafts` with `{ "id": "<existing-draft-id>", "version": 0, "title": "Updated Title" }` returns `200 OK` with the updated `title`, a regenerated `slug` derived from `"Updated Title"`, and `version = 1`. _(FR-05, FR-21, FR-22)_
    - The same update request with `version = 5` (stale) against a workshop at `version = 6` returns `409 Conflict` with `urn:unihub:error:conflict`. _(FR-08)_
    - An update request that omits `version` entirely (while `id` is present) returns `422`. _(FR-09)_
    - An update request targeting a `PUBLISHED` workshop returns `409 Conflict` with `urn:unihub:error:conflict`. _(FR-10)_
    - An update request where `id` refers to a workshop belonging to a different organization returns `403`. _(FR-06)_
    - An update request where `id` does not exist returns `404`. _(FR-07)_
    - `ModulithArchitectureTest` passes.

#### Checkpoint: Phase 2

- [x] T2 acceptance criteria are all verified end-to-end.
- [x] Slug is consistently regenerated when `title` changes and left unchanged when only other fields change.
- [x] `ModulithArchitectureTest` passes.
- [x] Full unit test suite (T1 + T2) is green.

### Phase 3: Speaker Validation and Schedule Conflict Detection

- [x] **T3 — ORGANIZER can attach speakers to a draft; conflict with existing booked speakers is rejected**
  - **Size:** M
  - **Depends on:** T2
  - **Layers touched:** Domain, Application, Infrastructure, Presentation
  - **Files:**
    - `workshops/domain/model/Speaker.java` _(new)_
    - `workshops/application/ports/out/WorkshopRepository.java` _(extended — `findConflictingSpeakers` batch query)_
    - `workshops/application/usecases/SaveDraftUseCaseImpl.java` _(extended — speaker replace + overlap check)_
    - `workshops/infrastructure/persistence/WorkshopSpeakerJpaEntity.java` _(new)_
    - `workshops/infrastructure/persistence/WorkshopSpeakerJpaRepository.java` _(new)_
    - `workshops/infrastructure/persistence/WorkshopRepositoryAdapter.java` _(extended — bulk speaker insert + overlap query)_
    - `workshops/infrastructure/persistence/mapper/WorkshopPersistenceMapper.java` _(extended — Speaker mapping)_
    - `workshops/presentation/dto/SaveDraftRequest.java` _(extended — `speakers` array)_
    - `workshops/presentation/dto/SpeakerRequest.java` _(new)_
    - `workshops/presentation/dto/WorkshopResponse.java` _(extended — `speakers` in response)_
    - `workshops/presentation/dto/SpeakerResponse.java` _(new)_
    - `workshops/presentation/mapper/WorkshopPresentationMapper.java` _(extended)_
    - `src/test/java/.../workshops/application/usecases/SaveDraftUseCaseImplSpeakerTest.java` _(new)_
    - `src/test/java/.../workshops/infrastructure/persistence/WorkshopRepositoryAdapterSpeakerTest.java` _(new)_
    - `src/test/java/.../workshops/presentation/controllers/AdminWorkshopControllerSpeakerTest.java` _(new)_
  - **Acceptance Criteria:**
    - `POST /api/v1/admin/workshops/drafts` with `speakers = [{name: "Alice", email: "Alice@Example.com"}]` and valid `startsAt`/`endsAt`, where Alice is not booked at any PUBLISHED/COMPLETED workshop in that interval, returns `201` with `speakers[0].email = "alice@example.com"` (normalized). _(FR-23, FR-28)_
    - The same request where `"alice@example.com"` is already booked at a `PUBLISHED` workshop overlapping the proposed interval returns `422` with `urn:unihub:error:validation` and a detail identifying Alice's email as the conflict. _(FR-27)_
    - A request with `speakers` present but `startsAt` or `endsAt` absent returns `422` with a message indicating schedule is required when speakers are provided. _(FR-26)_
    - A request with two speaker entries both having `email = "bob@example.com"` returns `422` (`urn:unihub:error:validation` — intra-request duplicate). _(FR-25)_
    - A speaker entry with a blank `name` returns `422`. _(FR-29)_
    - A speaker entry with `email = "not-an-email"` returns `422`. _(FR-30)_
    - Each speaker accepts optional `bio`, `avatarUrl`, `linkedinUrl` (valid HTTP/HTTPS URL, max 2048 chars), and `sortOrder` (default 0), and returns them in `SpeakerResponse`. _(FR-47)_
    - On update (with `id` present), the stored speaker list is fully replaced: if the prior draft had 3 speakers and the update sends 1 speaker, only 1 speaker exists after the call. _(FR-24)_
    - If `speakers` is omitted entirely on update, the existing speaker list is unchanged. _(API_CONTRACT Cross-Endpoint Note 4)_
    - A booked speaker at a `DRAFT`-status workshop does NOT trigger a conflict — only `PUBLISHED` and `COMPLETED` workshops are checked. _(BR-06)_
    - `ModulithArchitectureTest` passes.

#### Checkpoint: Phase 3

- [x] T3 acceptance criteria are all verified end-to-end.
- [x] The overlap detection query uses a single batched query (no per-speaker N+1 loop) — verifiable by inspecting Hibernate SQL logs in test output.
- [x] `ModulithArchitectureTest` passes.
- [x] Full unit test suite (T1 + T2 + T3) is green.

### Phase 4: Media Upload & Storage Integration

- [ ] **T4 — Shared Media Storage Port & Workshop Media Upload & Cleanup Integration**
  - **Size:** M
  - **Depends on:** T3
  - **Layers touched:** Shared Kernel (`shared/storage`), Application (`workshops`), Presentation (`workshops`)
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/shared/storage/MediaStoragePort.java` _(new)_
    - `apps/backend/src/main/java/com/unihub/workshop/shared/storage/MinioMediaStorageAdapter.java` _(new)_
    - `apps/backend/src/main/java/com/unihub/workshop/shared/storage/MediaValidationUtils.java` _(new: validate file size <= 5MB, MIME types: image/jpeg, image/jpg, image/png, image/webp, extensions: .jpg, .jpeg, .png, .webp)_
    - `apps/backend/src/main/java/com/unihub/workshop/shared/storage/MediaType.java` _(new)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/presentation/controllers/AdminWorkshopController.java` _(extended — media upload handler)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/presentation/dto/MediaUploadResponse.java` _(new)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/usecases/SaveDraftUseCaseImpl.java` _(extended — media cleanup on update)_
    - `apps/backend/src/test/java/com/unihub/workshop/shared/storage/MinioMediaStorageAdapterTest.java` _(new)_
    - `apps/backend/src/test/java/com/unihub/workshop/workshops/presentation/controllers/AdminWorkshopControllerMediaTest.java` _(new)_
    - `apps/backend/src/test/java/com/unihub/workshop/workshops/application/usecases/SaveDraftUseCaseImplMediaCleanupTest.java` _(new)_
  - **Acceptance Criteria:**
    - `POST /api/v1/admin/workshops/media/upload` with a valid 2MB image file (JPEG/JPG, PNG, WebP) and `type = COVER_IMAGE` returns `201 Created` with a public URL in `unihub-media` under `workshops/covers/` and does not mutate the database. _(FR-31, FR-32, FR-33, FR-34, BR-15, BR-16)_
    - `POST /api/v1/admin/workshops/media/upload` with `type = SPEAKER_AVATAR` stores under `speakers/avatars/`. _(FR-33)_
    - File upload exceeding 5MB or invalid MIME format (supported: `image/jpeg`, `image/jpg`, `image/png`, `image/webp`) returns `422 Unprocessable Content`. _(FR-32, FR-35)_
    - Updating a draft with a new `coverImageUrl` deletes the old cover image from MinIO. _(FR-36, BR-16)_
    - Replacing a draft's speakers with an array that omits an existing speaker deletes that speaker's avatar from MinIO. _(FR-37, BR-16)_
    - `ModulithArchitectureTest` passes.

#### Checkpoint: Phase 4

- [ ] T4 acceptance criteria are all verified end-to-end.
- [ ] MinIO key partitioning matches `workshops/covers/` and `speakers/avatars/`.
- [ ] `ModulithArchitectureTest` passes.
- [ ] Full unit test suite (T1 + T2 + T3 + T4) is green.

### Phase 5: Workshop Details for Organizer

- [ ] **T5 — ORGANIZER can retrieve full workshop details via `GET /api/v1/admin/workshops/{id}`**
  - **Size:** S
  - **Depends on:** T4
  - **Layers touched:** Application, Infrastructure, Presentation
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/ports/in/GetWorkshopUseCase.java` _(new)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/usecases/GetWorkshopUseCaseImpl.java` _(new)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/ports/out/WorkshopRepository.java` _(extended — `findByIdWithSpeakers`)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/infrastructure/persistence/WorkshopRepositoryAdapter.java` _(extended)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/presentation/controllers/AdminWorkshopController.java` _(extended — GET handler)_
    - `apps/backend/src/test/java/com/unihub/workshop/workshops/application/usecases/GetWorkshopUseCaseImplTest.java` _(new)_
    - `apps/backend/src/test/java/com/unihub/workshop/workshops/presentation/controllers/AdminWorkshopControllerGetTest.java` _(new)_
  - **Acceptance Criteria:**
    - `GET /api/v1/admin/workshops/{id}` for a workshop owned by the caller's organization returns `200 OK` with full workshop details and speaker records. _(FR-39, FR-42)_
    - `GET /api/v1/admin/workshops/{id}` for a workshop belonging to a different organization returns `403 Forbidden`. _(FR-40)_
    - `GET /api/v1/admin/workshops/{id}` for a non-existent identifier returns `404 Not Found`. _(FR-41)_
    - `ModulithArchitectureTest` passes.

#### Checkpoint: Phase 5

- [ ] T5 acceptance criteria are all verified end-to-end.
- [ ] Query uses eager join fetch to prevent N+1 queries when loading speakers.
- [ ] `ModulithArchitectureTest` passes.
- [ ] Full unit test suite (T1 through T5) is green.

### Phase 6: Delete Draft Workshop

- [ ] **T6 — ORGANIZER can permanently delete a DRAFT workshop via `DELETE /api/v1/admin/workshops/{id}` with media purge**
  - **Size:** S
  - **Depends on:** T5
  - **Layers touched:** Application, Infrastructure, Presentation
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/ports/in/DeleteDraftUseCase.java` _(new)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/usecases/DeleteDraftUseCaseImpl.java` _(new)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/ports/out/WorkshopRepository.java` _(extended — `deleteById`)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/infrastructure/persistence/WorkshopRepositoryAdapter.java` _(extended)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/presentation/controllers/AdminWorkshopController.java` _(extended — DELETE handler)_
    - `apps/backend/src/test/java/com/unihub/workshop/workshops/application/usecases/DeleteDraftUseCaseImplTest.java` _(new)_
    - `apps/backend/src/test/java/com/unihub/workshop/workshops/presentation/controllers/AdminWorkshopControllerDeleteTest.java` _(new)_
  - **Acceptance Criteria:**
    - `DELETE /api/v1/admin/workshops/{id}` for a DRAFT workshop owned by caller returns `204 No Content`, cascades speaker rows in DB, and purges cover image and speaker avatars from MinIO. _(FR-43, BR-09, BR-16)_
    - `DELETE /api/v1/admin/workshops/{id}` for a PUBLISHED/COMPLETED/CANCELED workshop returns `409 Conflict`. _(FR-46)_
    - `DELETE /api/v1/admin/workshops/{id}` for another organization's workshop returns `403 Forbidden`. _(FR-44)_
    - `DELETE /api/v1/admin/workshops/{id}` for a non-existent identifier returns `404 Not Found`. _(FR-45)_
    - `ModulithArchitectureTest` passes.

#### Checkpoint: Feature Complete

- [ ] All tasks (T1–T6) are checked off.
- [ ] Every acceptance criterion across all tasks has been verified end-to-end against a running application.
- [ ] Full unit test suite (T1 through T6) passes with no skips.
- [ ] `ModulithArchitectureTest` passes.
- [ ] Ready for `/review workshop-creation-and-draft-management`.

## Risks and Mitigations

| Risk                                                                                                                                                                                                                                                                                                                                                                   | Impact | Mitigation                                                                                                                                                                                                                                                                                                            |
| :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :----: | :-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Slug uniqueness collision under concurrent creates** — two simultaneous draft creates for titles that slugify to the same base string could both pass the unique check before either commits.                                                                                                                                                                        | Medium | Retry the slug generation (with a new random suffix) on `DataIntegrityViolationException` for the `slug` unique constraint, up to 3 attempts inside `SlugService` before propagating `500`. This is implemented in T1 so the fix is in place before any later task depends on slug behavior.                          |
| **Speaker overlap query correctness with CITEXT** — the `workshop_speakers.email` column is `CITEXT` (case-insensitive); the normalization (lowercase + trim) in the application layer must be consistent with how the DB stores and compares values, or the index may miss matches if case differs in existing data.                                                  |  High  | In T3, normalize every email to lowercase+trimmed in the use case before the query AND before persistence. Add a dedicated unit test that verifies the adapter's overlap query fires with already-normalized email strings, and a test that confirms a stored mixed-case email is still found by the lowercase query. |
| **JPA `@OneToMany` speakers cascade — N+1 on load** — loading `Workshop` with its `speakers` via a lazy `@OneToMany` in `GET /api/v1/admin/workshops/{id}` will produce N+1 queries if not fetched eagerly or with a `JOIN FETCH`.                                                                                                                                     | Medium | In T5, use `JOIN FETCH ws.speakers` in the JPQL query for `findByIdWithSpeakers`. Add a test that asserts only 1 DB query is issued when fetching a workshop with 3 speakers (use Hibernate statistics or SQL counter).                                                                                               |
| **Optimistic locking `@Version` interaction with speaker replace** — if `WorkshopJpaEntity` uses `@Version`, any modification to the entity (including the speaker collection, depending on cascade config) will increment the version. A speaker-only update that the caller does not expect to increment the version could cause unexpected `409`s on the next call. | Medium | In T2 and T3, document clearly that any successful save (regardless of which fields changed) increments `version` by 1. Ensure the response always returns the new `version`. Add a test that confirms version increments even when only speakers are updated.                                                        |
| **`package-info.java` module declaration missing** — if T1 is committed without `@ApplicationModule(allowedDependencies = {"shared"})`, `ModulithArchitectureTest` will fail for all downstream tasks.                                                                                                                                                                 |  Low   | T1's acceptance criteria explicitly include `ModulithArchitectureTest` passing, making this self-catching.                                                                                                                                                                                                            |

## Open Questions

None. All ambiguities from context-priming were resolved by the user on 2026-09-25 and are fully reflected in the approved `SPEC.md` and `API_CONTRACT.md`.
