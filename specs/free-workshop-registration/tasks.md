# Implementation Plan: Free Workshop Registration

> **Spec:** `specs/free-workshop-registration/SPEC.md`  
> **API Contract:** `specs/free-workshop-registration/API_CONTRACT.md`  
> **Status:** APPROVED

## Overview

This implementation plan delivers the complete free workshop registration capability for the UniHub platform across both the core backend modular monolith (`apps/backend`) and the notifications worker (`apps/workers`). The plan decomposes the approved specification into four capability-driven phases: establishing public cross-module facades and event definitions, building the core `POST /api/v1/registrations` mutation with high-concurrency Redis capacity control and two-tier idempotency, providing read-replica routed query endpoints for student registration history (`GET /me`) and detail view (`GET /{id}`) with conditional RSA-signed QR tickets, and implementing the asynchronous worker pipeline that consumes `WorkshopRegisteredEvent` to generate ZXing barcode images and deliver multi-channel notifications (email with inline QR, in-app notification, and real-time SSE stream).

## Architecture Decisions for This Plan

- **Two-Step Mutation Slicing (Core Flow before Concurrency Hardening):** The registration mutation is decomposed into T2 (pure domain validation, database transaction, RSA ticket signing, and event outbox) followed by T3 (Redis atomic capacity control and two-tier idempotency). This keeps diffs reviewable (≤ 10 files each) while ensuring both tasks remain vertically testable from the presentation layer down.
- **Extensible Strategy Dispatch Pattern:** The orchestration use case in `registrations` dispatches to `RegistrationStrategy`. T2 implements `FreeRegistrationStrategy` (which sets status directly to `CONFIRMED`), leaving the pipeline strictly open for Feature 4.2 to introduce `PaidRegistrationStrategy` without modifying the core orchestrator or controller.
- **Batch Workshop Resolution for Read Path:** To prevent N+1 cross-module lookups when returning the keyset cursor-paginated list in `GET /me`, `WorkshopApi` is designed with both single-item (`getWorkshopForRegistration`) and batch-resolution (`getWorkshopsForRegistrations`) methods so that `registrations` can enrich workshop display titles and schedules without breaking Modulith boundary rules.
- **Worker Zero-Callback Design via Fat Event:** `WorkshopRegisteredEvent` in `com.unihub.workshop.shared.events` carries complete snapshot data (student identity, workshop schedule and title, canonical QR payload, and Base64 RSA signature), allowing the notifications worker to process emails and QR generation without making any synchronous REST or database callbacks to the backend.
- **Strict Read-After-Write and Replica Routing:** Read endpoints (`GET /me` and `GET /{id}`) are explicitly marked with `@Transactional(readOnly = true)`, directing queries to the read replica through `TransactionAwareRoutingDataSource`, while all mutations and idempotency checks in T2 and T3 strictly target the primary database.

## Task List

### Phase 1: Module Integration Surface & Shared Contracts

- [x] **T1 — Expose cross-module query facades in `workshops` and `iam` modules and define the externalized `WorkshopRegisteredEvent`**
  - **Size:** M
  - **Depends on:** —
  - **Layers touched:** Module Public API (`api/`), Application (`facade/`), Shared Events (`shared/events/`)
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/api/dto/WorkshopRegistrationDetailsDto.java`
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/api/WorkshopApi.java`
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/facade/WorkshopFacade.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/api/dto/StudentProfileDto.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/api/IamApi.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/facade/IamFacade.java`
    - `apps/backend/src/main/java/com/unihub/workshop/shared/events/WorkshopRegisteredEvent.java`
    - `apps/backend/src/test/java/com/unihub/workshop/workshops/application/facade/WorkshopFacadeTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/iam/application/facade/IamFacadeTest.java`
  - **Acceptance Criteria:**
    - `WorkshopApi.getWorkshopForRegistration(UUID)` returns an `Optional<WorkshopRegistrationDetailsDto>` containing `id`, `organizationId`, `title`, `slug`, `status`, `startsAt`, `endsAt`, `price`, `capacity`, and `location`; returns `Optional.empty()` when the workshop does not exist (satisfies FR-06, BR-18).
    - `WorkshopApi.getWorkshopsForRegistrations(Collection<UUID>)` returns a `Map<UUID, WorkshopRegistrationDetailsDto>` resolving all requested existing workshops in a single batch query (satisfies BR-18).
    - `IamApi.getStudentProfile(UUID)` returns an `Optional<StudentProfileDto>` containing `userId`, `studentRecordId`, `fullName`, and `email` for active students; returns `Optional.empty()` if the user does not exist, is inactive, or is not a student (satisfies FR-02, BR-19).
    - `WorkshopRegisteredEvent` is annotated with `@Externalized("unihub.events::workshop.registered")` and encapsulates all student, workshop, and QR signature snapshot fields (satisfies FR-14, BR-20).
    - `ModulithArchitectureTest` passes without boundary violations across `workshops`, `iam`, and `shared`.

#### Checkpoint: Module Integration Surface

- [x] `WorkshopApi` and `IamApi` implementations are unit-tested and pass.
- [x] `WorkshopRegisteredEvent` compiles and satisfies Spring Modulith event publication conventions.
- [x] Backend build is clean and `ModulithArchitectureTest` succeeds.

### Phase 2: Core Registration Mutation & High-Concurrency Protection

- [x] **T2 — Implement core free workshop registration mutation with domain validation, RSA ticket signing, and transactional outbox persistence**
  - **Size:** M
  - **Depends on:** T1
  - **Layers touched:** Domain, Application (Use Case & Strategy), Infrastructure (Persistence & RSA Security), Presentation (Controller & DTOs)
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/domain/model/Registration.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/domain/model/RegistrationStatus.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/domain/model/QrTicket.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/application/ports/in/RegisterWorkshopUseCase.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/application/ports/out/RegistrationRepository.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/application/strategy/RegistrationStrategy.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/application/strategy/FreeRegistrationStrategy.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/application/usecases/RegisterWorkshopUseCaseImpl.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/infrastructure/persistence/RegistrationJpaEntity.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/infrastructure/persistence/RegistrationJpaRepository.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/infrastructure/persistence/RegistrationRepositoryAdapter.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/infrastructure/persistence/RegistrationPersistenceMapper.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/infrastructure/security/QrSignatureService.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/presentation/controllers/RegistrationController.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/presentation/dto/RegisterWorkshopRequest.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/presentation/dto/RegistrationResponse.java`
    - `apps/backend/src/test/java/com/unihub/workshop/registrations/application/usecases/RegisterWorkshopUseCaseImplTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/registrations/infrastructure/security/QrSignatureServiceTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/registrations/presentation/controllers/RegistrationControllerTest.java`
  - **Acceptance Criteria:**
    - Calling `POST /api/v1/registrations` with valid student credentials and an eligible free workshop (`price == 0`, `status == PUBLISHED`, future start date) creates a `CONFIRMED` registration row in primary DB and returns `201 Created` containing `id`, `workshopId`, `studentId`, `status: "CONFIRMED"`, `qrPayload`, `qrSignature`, and `qrIssuedAt` (satisfies FR-08, FR-12, FR-13, Scenario 1).
    - `qrPayload` is a canonical UTF-8 JSON object with `registrationId`, `workshopId`, `studentId`, and `issuedAt`; `qrSignature` is a Base64-encoded `SHA256withRSA` signature verifiable with the public key from `RsaKeyProperties` (satisfies FR-13, BR-10).
    - Committing a free registration publishes `WorkshopRegisteredEvent` to the transactional outbox (`event_publication` table) in the same database transaction (satisfies FR-14, BR-13).
    - Request returns `403 Forbidden` if `IamApi.getStudentProfile` returns empty or `studentRecordId == null` (satisfies FR-02, BR-02, Scenario 6).
    - Request returns `404 Not Found` if `WorkshopApi.getWorkshopForRegistration` returns empty (satisfies FR-06, Scenario 7).
    - Request returns `422 Unprocessable Entity` if the workshop is not `PUBLISHED`, or has already started (`starts_at <= now`), or is `CANCELED`/`COMPLETED` (satisfies FR-07, Scenarios 8, 9).
    - Request returns `422 Unprocessable Entity` if `price > 0` (satisfies FR-09).
    - Request returns `409 Conflict` if the student already holds an active (`PENDING` or `CONFIRMED`) registration for the target workshop (satisfies FR-10, BR-06, Scenario 11).
    - Request returns `422 Unprocessable Entity` if the workshop schedule overlaps with any active registration already held by the student (satisfies FR-10, BR-07, Scenario 12).

- [x] **T3 — Implement high-concurrency Redis capacity control, compensating rollback, and two-tier idempotency protection for registration mutation**
  - **Size:** M
  - **Depends on:** T2
  - **Layers touched:** Application (Ports & Use Case), Infrastructure (Redis Cache, DB Idempotency Persistence), Presentation (Header Validation & Error Mapping)
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/shared/idempotency/IdempotencyPort.java`
    - `apps/backend/src/main/java/com/unihub/workshop/shared/idempotency/ClaimResult.java`
    - `apps/backend/src/main/java/com/unihub/workshop/shared/idempotency/IdempotencyConflictException.java`
    - `apps/backend/src/main/java/com/unihub/workshop/shared/idempotency/IdempotencyKeyJpaEntity.java`
    - `apps/backend/src/main/java/com/unihub/workshop/shared/idempotency/IdempotencyKeyJpaRepository.java`
    - `apps/backend/src/main/java/com/unihub/workshop/shared/idempotency/RedisIdempotencyAdapter.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/application/ports/out/CapacityPort.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/infrastructure/cache/RedisCapacityAdapter.java`
    - `apps/backend/src/test/java/com/unihub/workshop/shared/idempotency/RedisIdempotencyAdapterTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/registrations/infrastructure/cache/RedisCapacityAdapterTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/registrations/presentation/controllers/RegistrationIdempotencyAndCapacityTest.java`
  - **Acceptance Criteria:**
    - Request without an `Idempotency-Key` header, or with a header not conforming to UUIDv4, returns `422 Unprocessable Entity` (satisfies FR-03, BR-03).
    - Concurrent requests with identical `(studentId, Idempotency-Key)` acquire an atomic Redis lock (`PENDING`); subsequent in-flight requests return `409 Conflict` with `Retry-After: 2` (satisfies FR-04, BR-03, Scenario 3).
    - Replaying a request with a completed `(studentId, Idempotency-Key)` returns `201 Created` with the exact cached response body without re-decrementing capacity or creating duplicate database records (satisfies FR-05, BR-04, Scenario 2).
    - Available workshop capacity in Redis (`workshop:{workshopId}:capacity`) is decremented atomically via `DECR`; if the result is `< 0`, an immediate compensating `INCR` is executed, the lock is released, and `409 Conflict` is returned (satisfies FR-11, BR-08, Scenarios 10, 19).
    - If the Redis capacity key is missing (cache miss), it self-heals by loading `(capacity - active_registrations)` from the primary database before executing the decrement (satisfies BR-08).
    - Rate limit filter enforces the 5 tokens / 10-second window on `/api/v1/registrations/**`, returning `429 Too Many Requests` when exhausted (satisfies FR-01, BR-01).

#### Checkpoint: Registration Mutation Complete

- [x] `POST /api/v1/registrations` passes all unit tests for validation, business rules, idempotency replay, and capacity exhaustion.
- [x] Concurrency edge cases (simultaneous duplicate keys, last-seat race conditions) are verified with unit tests using mocked Redis and repositories.
- [x] All code strictly adheres to `registrations` package structure and Clean Architecture layer dependencies.

### Phase 3: Registration Query & Detail Retrieval (Read Path)

- [x] **T4 — Implement keyset cursor-paginated registration history for authenticated students (`GET /api/v1/registrations/me`)**
  - **Size:** S
  - **Depends on:** T2
  - **Layers touched:** Application (Query Use Case), Infrastructure (JPA Keyset Criteria & Replica Routing), Presentation (Controller & DTOs)
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/application/ports/in/GetMyRegistrationsUseCase.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/application/ports/in/GetMyRegistrationsQuery.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/application/usecases/GetMyRegistrationsUseCaseImpl.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/presentation/dto/RegistrationSummaryItemResponse.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/presentation/dto/RegistrationListResponse.java`
    - `apps/backend/src/test/java/com/unihub/workshop/registrations/application/usecases/GetMyRegistrationsUseCaseImplTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/registrations/presentation/controllers/GetMyRegistrationsControllerTest.java`
  - **Acceptance Criteria:**
    - Calling `GET /api/v1/registrations/me` returns `200 OK` with a keyset cursor-paginated list of registrations owned by `jwt.sub`, ordered by `createdAt DESC, id DESC` (satisfies FR-16, FR-18, Scenario 13).
    - Query supports optional `status` filter (`PENDING`, `CONFIRMED`, `CANCELED`, `FAILED`) and `limit` (default 20, max 100) (satisfies FR-17, FR-19).
    - Response items contain `id`, `workshopId`, `workshopTitle`, `workshopStartsAt`, `workshopEndsAt`, `status`, and `createdAt`; QR fields (`qrPayload`, `qrSignature`, `qrIssuedAt`) are omitted from list items (satisfies FR-18, BR-17).
    - Cursor pagination uses opaque Base64 encoding `{id, sortField: "createdAt", primaryValue, secondaryValue: null}`; invalid cursors return `400 Bad Request` (satisfies FR-19, BR-17).
    - When student has zero registrations, returns `200 OK` with `items: []`, `hasMore: false`, `nextCursor: null`.
    - Executed with `@Transactional(readOnly = true)` to route queries to the read replica (satisfies BR-15).

- [x] **T5 — Implement single registration detail view with ownership check and conditional QR ticket fields (`GET /api/v1/registrations/{id}`)**
  - **Size:** S
  - **Depends on:** T2
  - **Layers touched:** Application (Query Use Case), Infrastructure (Repository Query & Replica Routing), Presentation (Controller & DTOs)
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/application/ports/in/GetRegistrationDetailUseCase.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/application/usecases/GetRegistrationDetailUseCaseImpl.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/presentation/dto/RegistrationDetailResponse.java`
    - `apps/backend/src/test/java/com/unihub/workshop/registrations/application/usecases/GetRegistrationDetailUseCaseImplTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/registrations/presentation/controllers/GetRegistrationDetailControllerTest.java`
  - **Acceptance Criteria:**
    - Calling `GET /api/v1/registrations/{id}` for a registration owned by `jwt.sub` with status `CONFIRMED` returns `200 OK` including `id`, `workshopId`, `workshopTitle`, `workshopStartsAt`, `workshopEndsAt`, `workshopLocation`, `studentId`, `status`, `createdAt`, `qrPayload`, `qrSignature`, and `qrIssuedAt` (satisfies FR-20, FR-22, Scenario 14).
    - When registration status is non-`CONFIRMED` (`CANCELED` or `FAILED`), returns `200 OK` with `qrPayload`, `qrSignature`, and `qrIssuedAt` omitted / null (satisfies FR-22).
    - Request for a non-existent registration ID returns `404 Not Found` (satisfies FR-21, Scenario 15).
    - Request for a registration belonging to a different student (`student_user_id != jwt.sub`) returns `403 Forbidden` (satisfies FR-23, BR-16).
    - Executed with `@Transactional(readOnly = true)` to route queries to the read replica (satisfies BR-15).

#### Checkpoint: Read Endpoints Complete

- [x] Both `GET /me` and `GET /{id}` pass unit and web layer tests with mocked dependencies.
- [x] Keyset cursor pagination and ownership boundaries are thoroughly verified.
- [x] Read-only transaction annotations are confirmed on query services.

### Phase 4: Async Notification Worker & QR Ticket Dispatch

- [ ] **T6 — Implement notification worker consumer for `WorkshopRegisteredEvent` with ZXing inline QR generation, email dispatch, in-app notification, and SSE publishing**
  - **Size:** M
  - **Depends on:** T1
  - **Layers touched:** Workers Inbound (DTO & Handler), Workers Core (Email Models), Workers Outbound (AMQP Config, JavaMail Inline Attachments, Thymeleaf Template)
  - **Files:**
    - `apps/workers/build.gradle`
    - `apps/workers/src/main/java/com/unihub/workshop/workers/notifications/inbound/dto/WorkshopRegisteredEventDto.java`
    - `apps/workers/src/main/java/com/unihub/workshop/workers/notifications/inbound/handler/WorkshopRegisteredNotificationHandler.java`
    - `apps/workers/src/main/java/com/unihub/workshop/workers/notifications/core/models/EmailMessage.java`
    - `apps/workers/src/main/java/com/unihub/workshop/workers/notifications/outbound/email/JavaMailSenderAdapter.java`
    - `apps/workers/src/main/java/com/unihub/workers/notifications/outbound/email/QrCodeGenerator.java`
    - `apps/workers/src/main/java/com/unihub/workshop/workers/notifications/outbound/config/NotificationAmqpConfig.java`
    - `apps/workers/src/main/resources/templates/email/workshop-registration-confirmation.html`
    - `apps/workers/src/test/java/com/unihub/workshop/workers/notifications/inbound/handler/WorkshopRegisteredNotificationHandlerTest.java`
    - `apps/workers/src/test/java/com/unihub/workshop/workers/notifications/outbound/email/QrCodeGeneratorTest.java`
  - **Acceptance Criteria:**
    - Add ZXing core and javase dependencies (`com.google.zxing:core:3.5.3`, `com.google.zxing:javase:3.5.3`) to `apps/workers/build.gradle`.
    - `NotificationAmqpConfig` binds `workshop.registered` routing key on exchange `unihub.events` to queue `q.notifications` (satisfies FR-24).
    - When `WorkshopRegisteredEvent` is received, handler uses ZXing to generate a 300x300 PNG image encoding `{"payload": <qr_payload_json>, "signature": "<base64_signature>"}` and attaches it inline with `Content-ID: <ticket-qr>` (satisfies FR-26, BR-11, Scenario 16).
    - Handler renders `workshop-registration-confirmation.html` with workshop title, start/end dates, location, and the inline QR code, dispatching email via `MultiChannelNotificationDeliveryUseCase` (satisfies FR-25, FR-26, Scenario 16).
    - Handler creates an in-app notification linking to `/workshops/{slug}` and publishes an SSE signal to the student's stream channel (satisfies FR-27, FR-28, Scenario 17).
    - If email delivery or realtime publishing fails, AMQP retry/DLQ handles the event; failure does not affect the registration record in the backend (satisfies FR-29, BR-14, Scenario 18).
    - `HexagonalArchitectureTest` in `apps/workers` passes without architectural violations.

#### Checkpoint: Feature Complete

- [ ] All tasks (T1 to T6) are checked off.
- [ ] Every acceptance criterion across all tasks has been verified with unit tests.
- [ ] Architecture tests pass: `ModulithArchitectureTest` for backend, `HexagonalArchitectureTest` for workers.
- [ ] No warnings or unhandled compiler/linter issues remain.
- [ ] Ready for `/review`.

## Risks and Mitigations

| Risk                                                                                   | Impact | Mitigation                                                                                                                                                                                                                                                  |
| :------------------------------------------------------------------------------------- | :----- | :---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Concurrency race on last available seat when multiple students register simultaneously | High   | Redis atomic `DECR` guarantees single winner; unsuccessful attempts immediately invoke compensating `INCR` and return `409 Conflict`. Database partial unique index on `(workshop_id, student_user_id)` acts as final safety net against duplicate inserts. |
| In-flight duplicate requests with same idempotency key causing race conditions         | High   | Two-tier idempotency: atomic Redis `SETNX` lock marks key `PENDING` with 10s TTL; concurrent requests receive `409 Conflict` + `Retry-After: 2`. Primary DB `idempotency_keys` table persists final `COMPLETED` response for 24h replay durability.         |
| Cross-module boundary violations between `registrations`, `workshops`, and `iam`       | Medium | All cross-module lookups strictly use typed interfaces (`WorkshopApi`, `IamApi`) in peer module `api/` packages. No domain entities or repositories are shared. Enforced via `ModulithArchitectureTest`.                                                    |
| Outbox event publication failure leaving database and worker desynchronized            | Medium | `WorkshopRegisteredEvent` is persisted directly into Spring Modulith's `event_publication` table within the same database transaction as the registration record. Failed broker deliveries are re-polled and forwarded by Spring Modulith.                  |
| Email delivery failure or slow external SMTP blocking registration                     | Medium | Asynchronous decoupling via RabbitMQ: registration commits in backend within < 100ms; notification worker consumes event in background. SMTP failure triggers exponential backoff and DLQ without rolling back registration.                                |
| Keyset cursor pagination instability under concurrent registrations                    | Low    | Deterministic multi-column ordering `(createdAt DESC, id DESC)` with opaque Base64 keyset cursor (`JpaKeysetPagination`) prevents offset drift and skips/duplicates during active registration bursts.                                                      |

## Open Questions

None.
