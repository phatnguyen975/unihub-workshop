# Implementation Plan: Attendance Sync Reconciliation

> **Spec:** `specs/attendance-sync-reconciliation/SPEC.md`  
> **API Contract:** `specs/attendance-sync-reconciliation/API_CONTRACT.md`  
> **Status:** APPROVED

## 1. Overview

This plan builds the net-new `attendances` Spring Modulith module in `apps/backend/` and delivers `POST /api/v1/workshops/{workshopId}/attendances/sync` in seven vertical slices. The slices are ordered so that at the end of **every** phase the endpoint is safe: it can never admit a ticket twice, never write a record for another workshop, and never report success for an entry it could not prove was a genuine first check-in. Phase 1 delivers the authoritative reconciliation path for new scans; Phase 2 makes an individual scan durably idempotent; Phase 3 makes a whole batch idempotent; Phase 4 hardens the request boundary; Phase 5 adds the post-commit counter event and the operational behavior (metrics, structured logging, failure containment).

## 2. Architecture Decisions for This Plan

- **Module bootstrapping is folded into T1, not split into its own task.** A brand-new module has no caller-observable behavior until the endpoint exists, so a "create the module skeleton" task would be a horizontal, non-vertical slice.
- **T1 is deliberately larger than the ~10-file guideline (~22 production files); this was explicitly accepted by the human on 2026-10-31 (see §5).** Nine of those files are one-line enums/records, five are additive edits to the `workshops::api` / `registrations::api` surfaces, and the rest is the minimum set of layers a net-new module needs to be end-to-end testable. Splitting T1 per layer is explicitly forbidden by the planning skill and would leave nothing verifiable end-to-end.
- **The two peer-module facade extensions ship inside T1.** `WorkshopApi.isStaffAssignedToWorkshop` and `RegistrationApi.getTicketContexts` have no caller-observable behavior of their own (the sync endpoint is their first and only consumer), so they are groundwork inside the first capability that needs them rather than a task with no observable outcome.
- **Classification lives in `application/policy/AttendanceReconciliationPolicy`, not `domain/`.** It consumes the `registrations::api` DTO, and `ModulithArchitectureTest` forbids `..domain..` → `..api..`. The policy stays pure Java with zero framework dependencies and is unit-tested with zero mocks.
- **Persistence uses `NamedParameterJdbcTemplate` with one multi-row `INSERT ... ON CONFLICT DO NOTHING RETURNING sync_id`.** It is set-based (≤500 rows × 11 columns, well under PostgreSQL's 65,535-parameter limit), makes the database the final arbiter (BR-05), and reconciles the identifiers actually persisted instead of relying on exception-driven control flow. `syncId` is the per-entry identity echoed in the response, so an entry is `SUCCESS` exactly when its `syncId` comes back from the write. No JPA entity is introduced because the module only performs bulk writes and reads.
- **The sync use case is `@Transactional` with `readOnly = false`.** `TransactionAwareRoutingDataSource` only selects the primary for a non-read-only transaction (BR-02); the peer facade calls (`@Transactional(readOnly = true)`) join that transaction and therefore also stay on the primary.
- **The idempotency claim is the first statement of the use case.** It must precede every workshop/registration/attendance read so that a replayed batch never touches business tables, matching the contract's evaluation order.
- **Body validation is invoked manually by the controller (no `@Valid` on `@RequestBody`).** Spring validates `@Valid` bodies _before_ the controller method runs, which would turn a request with both a missing `Idempotency-Key` and an invalid body into `422`; the contract requires the `400` header check to win. T4 introduces the explicit validator.
- **The counter event is registered through `TransactionSynchronization.afterCommit`.** Precedent: `WorkshopRegistrationBroadcasterImpl`. A rolled-back batch must never emit an event (FR-44).
- **Shared filters already deliver `401`, `415` and `429` for this path** (JWT filter chain, content negotiation, default rate-limit tier). No task is allocated to them; T7 only verifies the Redis-degradation behavior.
- **Operational behavior is sequenced last (T5–T7).** Each slice is additive over an already-correct core and changes no response contract.

## 3. Task List

### Phase 1: Core Reconciliation Path

- [x] **T1 — A batch of scans from an assigned STAFF user is reconciled, persisted as `VALID` / `OFFLINE_SYNC` records, and answered with one `207` outcome per entry**
  - **Size:** M
  - **Depends on:** —
  - **Layers touched:** Domain, Application, Infrastructure (Persistence), Presentation (REST), plus additive edits to the `workshops::api` and `registrations::api` surfaces
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/attendances/package-info.java` — `@ApplicationModule(displayName = "Attendances Module", allowedDependencies = { "shared", "workshops::api", "registrations::api" })`
    - `apps/backend/src/main/java/com/unihub/workshop/attendances/domain/model/Attendance.java` — aggregate record + `createOfflineSync(...)` factory that clamps `checkedInAt` to `[startsAt, now]`
    - `apps/backend/src/main/java/com/unihub/workshop/attendances/domain/model/AttendanceStatus.java` — `VALID`, `INVALIDATED`
    - `apps/backend/src/main/java/com/unihub/workshop/attendances/domain/model/AttendanceClassification.java` — `SUCCESS`, `ALREADY_USED`, `INVALID_TICKET`
    - `apps/backend/src/main/java/com/unihub/workshop/attendances/domain/model/AttendanceOutcome.java` — `(syncId, classification)`
    - `apps/backend/src/main/java/com/unihub/workshop/attendances/domain/model/SyncEntry.java` — `(syncId, registrationId, scannedAt)`
    - `apps/backend/src/main/java/com/unihub/workshop/attendances/application/ports/in/SyncAttendancesCommand.java` — `(workshopId, staffUserId, deviceId, entries)`
    - `apps/backend/src/main/java/com/unihub/workshop/attendances/application/ports/in/SyncAttendancesUseCase.java`
    - `apps/backend/src/main/java/com/unihub/workshop/attendances/application/ports/out/AttendanceRepository.java` — `insertAll(...)` returning the persisted `syncId`s
    - `apps/backend/src/main/java/com/unihub/workshop/attendances/application/policy/AttendanceReconciliationPolicy.java` — ticket validity + insert/no-insert classification
    - `apps/backend/src/main/java/com/unihub/workshop/attendances/application/usecases/SyncAttendancesUseCaseImpl.java` — workshop existence → assignment → status → bulk ticket read → classify → bulk write → outcomes
    - `apps/backend/src/main/java/com/unihub/workshop/attendances/infrastructure/persistence/AttendanceRepositoryAdapter.java` — one multi-row `INSERT ... ON CONFLICT DO NOTHING RETURNING sync_id`
    - `apps/backend/src/main/java/com/unihub/workshop/attendances/presentation/controllers/AttendanceSyncController.java` — `POST /api/v1/workshops/{workshopId}/attendances/sync`, `@PreAuthorize("hasRole('STAFF')")`, returns `ResponseEntity.status(207)`
    - `apps/backend/src/main/java/com/unihub/workshop/attendances/presentation/dto/AttendanceSyncRequest.java` — `(deviceId, entries)`
    - `apps/backend/src/main/java/com/unihub/workshop/attendances/presentation/dto/AttendanceSyncEntryRequest.java` — `(syncId, registrationId, scannedAt)`
    - `apps/backend/src/main/java/com/unihub/workshop/attendances/presentation/dto/AttendanceSyncOutcomeResponse.java` — `(syncId, status)`
    - `apps/backend/src/main/java/com/unihub/workshop/attendances/presentation/mapper/AttendancePresentationMapper.java` — MapStruct, request → command and outcome → response
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/api/WorkshopApi.java` — add `boolean isStaffAssignedToWorkshop(UUID workshopId, UUID staffUserId)`
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/facade/WorkshopFacade.java` — implement it via the existing `WorkshopAssignmentRepository.existsByWorkshopIdAndStaffUserId`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/api/RegistrationApi.java` — add `Map<UUID, RegistrationTicketContextDto> getTicketContexts(Collection<UUID> registrationIds)`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/api/dto/RegistrationTicketContextDto.java` — `(registrationId, workshopId, status)`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/application/facade/RegistrationFacade.java` — implement it via the existing `RegistrationRepository.findAllByIds`
    - Tests: `attendances/domain/model/AttendanceTest.java`, `attendances/application/policy/AttendanceReconciliationPolicyTest.java`, `attendances/application/usecases/SyncAttendancesUseCaseImplTest.java`, `attendances/infrastructure/persistence/AttendanceRepositoryAdapterTest.java`, `attendances/presentation/controllers/AttendanceSyncControllerTest.java`, plus one added case each in `workshops/application/facade/WorkshopFacadeTest.java` and `registrations/application/facade/RegistrationFacadeTest.java`
  - **Acceptance Criteria:**
    - A valid `STAFF` JWT assigned to a `PUBLISHED` workshop, submitting three `CONFIRMED` entries, receives `207` with three outcome objects in submitted order, all `SUCCESS`; three rows exist with `check_in_method = 'OFFLINE_SYNC'`, `status = 'VALID'`, `checked_in_by_user_id = jwt.sub`, and the submitted `sync_id` / `device_id` (FR-04, FR-14, FR-25, FR-32, FR-33, FR-37–FR-40; Scenario 1).
    - An unknown workshop returns `404`; an existing workshop without an assignment for the caller returns `403`; a `DRAFT` or `CANCELED` workshop returns `422`; none of the three writes an attendance row (FR-03, FR-04, FR-13; Scenarios 8–10).
    - A malformed `{workshopId}` path segment returns `400`, and a non-`STAFF` role returns `403`, both before any reconciliation (FR-02; Scenario 7; API_CONTRACT §3).
    - An entry whose registration is absent, not `CONFIRMED`, or bound to another workshop is reported `INVALID_TICKET` in the same `207` response while the remaining entries are still persisted (FR-21–FR-23, FR-41; Scenarios 12 and 24).
    - `checked_in_at` equals the submitted `scannedAt` clamped to `[workshop.starts_at, server now()]`; `created_at` / `updated_at` are the server receipt time; `created_by` / `updated_by` are `jwt.sub` (FR-34, FR-35; Scenario 17).
    - An entry whose `syncId` is not returned by the bulk write — a conflict on either `(workshop_id, registration_id)` or `(workshop_id, sync_id)` — is reported `ALREADY_USED`, the request still returns `207`, and the pre-existing row is untouched (FR-29, FR-31, FR-36; Scenarios 15–16).
    - An absent, `null` or empty `entries` array, or a `null` entry field, returns `422` with `validationErrors` and writes nothing (FR-07, FR-11; Scenario 19).
    - The request performs exactly one workshop read, one assignment existence check, one registration bulk read and one bulk write, regardless of entry count — verified by mocking the outbound ports and asserting one interaction each (BR-04).

#### Checkpoint: Core Reconciliation Path

- [ ] T1 passes through `AttendanceSyncControllerTest` (MockMvc slice, use case mocked) and `SyncAttendancesUseCaseImplTest` (all outbound ports mocked); the adapter test asserts a single bulk statement and returned-identifier reconciliation.
- [ ] `./gradlew test --tests "com.unihub.workshop.ModulithArchitectureTest"` passes with `attendances` registered as a business module.
- [ ] The T1 test classes pass; the human runs the full backend suite separately (see §6).
- [ ] Human reviewed the T1 diff before T2 starts.

### Phase 2: Durable Per-Scan Idempotency

- [ ] **T2 — A retried scan is reported `SUCCESS` without a second record, while a genuine double scan, an `INVALIDATED` row, or a reused `syncId` is reported `ALREADY_USED`**
  - **Size:** M
  - **Depends on:** T1
  - **Layers touched:** Application (policy + use case), Infrastructure (bulk read), Domain (existing `Attendance` reused as the read model)
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/attendances/application/ports/out/AttendanceRepository.java` — add `findByWorkshopIdAndRegistrationIds(...)`
    - `apps/backend/src/main/java/com/unihub/workshop/attendances/infrastructure/persistence/AttendanceRepositoryAdapter.java` — one workshop-scoped bulk `SELECT`
    - `apps/backend/src/main/java/com/unihub/workshop/attendances/application/policy/AttendanceReconciliationPolicy.java` — full precedence table (FR-30)
    - `apps/backend/src/main/java/com/unihub/workshop/attendances/application/usecases/SyncAttendancesUseCaseImpl.java` — load existing records and delegate to the policy
    - Tests: `AttendanceReconciliationPolicyTest.java`, `SyncAttendancesUseCaseImplTest.java`, `AttendanceRepositoryAdapterTest.java`
  - **Acceptance Criteria:**
    - Re-submitting the same batch under a **fresh** `Idempotency-Key` with identical `syncId` values returns `207` with every outcome `SUCCESS`; the attendance row count is unchanged and no `checked_in_at` / `updated_at` is modified (FR-26, FR-36; Scenario 2).
    - An existing `VALID` row for the same registration with a different `syncId` returns `ALREADY_USED`, and the stored `sync_id` is unchanged (FR-27; Scenario 13).
    - An existing `INVALIDATED` row returns `ALREADY_USED` for both the same and a different `syncId`, and the row stays `INVALIDATED` (FR-28, FR-30, BR-07; Scenario 14).
    - Precedence is exactly: invalid ticket → `INVALIDATED` → same `syncId` → different `syncId` → new insert; a registration that has since become `CANCELED` is `INVALID_TICKET` even when a `VALID` row exists, and that row is not altered (FR-30, BR-10; Spec §5 state table).
    - The reconciliation issues exactly one workshop-scoped attendance read for the whole batch (BR-04).
    - `AttendanceReconciliationPolicyTest` covers every precedence branch of FR-30 with zero mocks.

#### Checkpoint: Durable Per-Scan Idempotency

- [ ] T2 acceptance criteria pass at the use-case, policy and adapter layers.
- [ ] The T2 test classes and `./gradlew test --tests "com.unihub.workshop.ModulithArchitectureTest"` pass; the human runs the full backend suite separately (see §6).
- [ ] Human reviewed the T2 diff before T3 starts.

### Phase 3: Request-Level Idempotency

- [ ] **T3 — A batch replayed under the same `Idempotency-Key` returns the stored `207` without re-executing, and conflicting reuse is `409`**
  - **Size:** M
  - **Depends on:** T1
  - **Layers touched:** Application (use case), Presentation (controller)
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/attendances/presentation/controllers/AttendanceSyncController.java` — required `Idempotency-Key` header, translate the validator's `ValidationException` into `BadRequestException`
    - `apps/backend/src/main/java/com/unihub/workshop/attendances/application/ports/in/SyncAttendancesCommand.java` — add `idempotencyKey`
    - `apps/backend/src/main/java/com/unihub/workshop/attendances/application/usecases/SyncAttendancesUseCaseImpl.java` — claim before the first business read, complete on success, fail on any thrown exception, canonical request hash over method + path + body
    - `apps/backend/src/main/java/com/unihub/workshop/attendances/presentation/mapper/AttendancePresentationMapper.java` — pass the normalized key into the command
    - Tests: `SyncAttendancesUseCaseImplTest.java`, `AttendanceSyncControllerTest.java`
  - **Acceptance Criteria:**
    - The same key with an identical body returns `207` with a body identical to the first response, and the outbound ports are not invoked a second time (FR-15; Scenario 3).
    - The same key with a different body — including a different `{workshopId}` — returns `409` `urn:unihub:error:conflict` and writes nothing (FR-16; Scenario 4).
    - The same key while the first request is still in flight returns `409` with `Retry-After: 2` (FR-17; Scenario 5).
    - A missing, non-UUID, or over-64-character `Idempotency-Key` returns `400` `urn:unihub:error:bad-request` (not `422`) and writes nothing (FR-06; Scenario 6).
    - Two different staff members using the same key value are treated as independent requests (FR-18).
    - The claim is recorded before the first business read, completed with code `207` and the outcome payload on success, and released with `fail(...)` whenever reconciliation throws so the same key can be retried (FR-19).
    - The stored replay payload is typed as `AttendanceOutcome[]` (a domain type) and re-mapped to the wire DTO, so neither the application layer nor the idempotency record depends on a presentation type.

#### Checkpoint: Request-Level Idempotency

- [ ] T3 acceptance criteria pass at the use-case and MockMvc layers, including the `400` vs `422` distinction for a bad header.
- [ ] The T3 test classes and `./gradlew test --tests "com.unihub.workshop.ModulithArchitectureTest"` pass; the human runs the full backend suite separately (see §6).
- [ ] Human reviewed the T3 diff before T4 starts.

### Phase 4: Request Boundary Hardening

- [ ] **T4 — Malformed, oversized, or internally duplicated batches are rejected with `422` before any database work**
  - **Size:** S
  - **Depends on:** T1, T3
  - **Layers touched:** Presentation (DTO constraints, validator, mapper), Application (command input)
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/attendances/presentation/dto/AttendanceSyncRequest.java` — `@NotEmpty`, `@Size(max = 500)`, `@Size(max = 100)` on `deviceId`
    - `apps/backend/src/main/java/com/unihub/workshop/attendances/presentation/dto/AttendanceSyncEntryRequest.java` — string-typed fields with `@NotBlank`
    - `apps/backend/src/main/java/com/unihub/workshop/attendances/presentation/validation/AttendanceSyncBatchValidator.java` — programmatic `jakarta.validation.Validator` pass (violations mapped to `ValidationException`), plus duplicate `syncId` / `registrationId` detection and UUID / ISO-8601-UTC parsing with field-level messages
    - `apps/backend/src/main/java/com/unihub/workshop/attendances/presentation/controllers/AttendanceSyncController.java` — invoke the validator after the header/path checks and before the use case
    - `apps/backend/src/main/java/com/unihub/workshop/attendances/presentation/mapper/AttendancePresentationMapper.java` — explicit `String → UUID` / `String → Instant` conversions (validated upstream)
    - Tests: `AttendanceSyncBatchValidatorTest.java`, `AttendanceSyncControllerTest.java`, `AttendancePresentationMapperTest.java`
  - **Acceptance Criteria:**
    - A batch of 501 entries returns `422` with a field-level error on `entries` and writes nothing (FR-08; Scenario 20).
    - Two entries sharing a `syncId` return `422` naming the duplicate index and write nothing (FR-09; Scenario 18).
    - Two entries sharing a `registrationId` return `422` and write nothing (FR-10; Scenario 18).
    - A present-but-invalid UUID, or a `scannedAt` that is not an ISO-8601 UTC instant, returns `422` (not `400`), because the JSON is syntactically valid but the value is semantically invalid (FR-11).
    - A `deviceId` longer than 100 characters returns `422`; an omitted `deviceId` persists `NULL` (FR-12).
    - A request with both a missing `Idempotency-Key` and an invalid body returns `400`, and no `422` response ever consumes an idempotency claim (API_CONTRACT §3 evaluation order; BR-13).
    - A syntactically malformed JSON body still returns `400`, not `422`.

#### Checkpoint: Request Boundary Hardening

- [ ] T4 acceptance criteria pass through the MockMvc slice, including the `400`-wins ordering case and the `400` vs `422` field-format split.
- [ ] The T4 test classes and `./gradlew test --tests "com.unihub.workshop.ModulithArchitectureTest"` pass; the human runs the full backend suite separately (see §6).
- [ ] Human reviewed the T4 diff before T5 starts.

### Phase 5: Post-Commit Counter Event and Operational Behavior

- [ ] **T5 — Exactly one `attendance.updated` event is published after commit, and only when new records were persisted**
  - **Size:** S
  - **Depends on:** T1, T2
  - **Layers touched:** Application (event publisher + use case), Infrastructure (aggregate count query)
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/attendances/application/ports/out/AttendanceRepository.java` — add `countValidByWorkshopId(...)`
    - `apps/backend/src/main/java/com/unihub/workshop/attendances/infrastructure/persistence/AttendanceRepositoryAdapter.java` — one aggregate count statement
    - `apps/backend/src/main/java/com/unihub/workshop/attendances/application/events/AttendanceSyncEventPublisher.java` — `TransactionSynchronization.afterCommit` + `RealtimeEvent.of(ATTENDANCE_UPDATED, payload)`
    - `apps/backend/src/main/java/com/unihub/workshop/attendances/application/usecases/SyncAttendancesUseCaseImpl.java` — register the publish only when at least one row was inserted
    - Tests: `AttendanceSyncEventPublisherTest.java`, `SyncAttendancesUseCaseImplTest.java`, `AttendanceRepositoryAdapterTest.java`
  - **Acceptance Criteria:**
    - A batch of two new scans plus one same-`syncId` retry publishes exactly one event on `unihub:stream:workshop:{workshopId}` with `{workshopId, checkedInCount, capacity, lastSyncedAt}`, where `checkedInCount` is the post-write count of `VALID` rows and `capacity` comes from the workshop (FR-42, FR-45, FR-46; Scenario 21).
    - A batch made only of same-`syncId` retries publishes zero events (FR-43; Scenario 22).
    - A failed or rolled-back bulk write publishes zero events (FR-44; Scenario 23).
    - The publisher is invoked from `afterCommit`, never while the transaction is still open (FR-44).
    - `lastSyncedAt` is the server time of the sync, not `max(checked_in_at)` (FR-45).

- [ ] **T6 — Every sync request is observable through Micrometer metrics and one structured log line, without leaking ticket data**
  - **Size:** S
  - **Depends on:** T1
  - **Layers touched:** Application (observability component + use case)
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/attendances/application/observability/AttendanceSyncMetrics.java` — counters, timer and structured log
    - `apps/backend/src/main/java/com/unihub/workshop/attendances/application/usecases/SyncAttendancesUseCaseImpl.java` — record counts, latency and classification tallies
    - Tests: `AttendanceSyncMetricsTest.java`, `SyncAttendancesUseCaseImplTest.java`
  - **Acceptance Criteria:**
    - Each request increments `attendance.sync.requests.total` and records latency in `attendance.sync.duration` (NFR Observability).
    - `attendance.sync.outcomes.total` is incremented once per outcome with tag `status` in `{SUCCESS, ALREADY_USED, INVALID_TICKET}` (NFR Observability).
    - `attendance.sync.bulk_write.failures.total` is incremented when the bulk write fails (NFR Observability).
    - Exactly one structured log line per request carries `user_id`, `workshopId`, entry count, per-classification counts, latency and trace id; no QR payload, signature, or full request body is logged (NFR Security / Observability).
    - The metrics are visible at `/actuator/prometheus` through the existing registry, with no new registry or configuration class.

- [ ] **T7 — Primary-database failure is contained: `503` with `Retry-After`, claim released, no partial write, no replica fallback**
  - **Size:** S
  - **Depends on:** T1, T3
  - **Layers touched:** Application (use case)
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/attendances/application/usecases/SyncAttendancesUseCaseImpl.java` — translate `DataAccessResourceFailureException` into `ServiceUnavailableException`, release the claim, never mask the original failure
    - Tests: `SyncAttendancesUseCaseImplTest.java`
  - **Acceptance Criteria:**
    - A `DataAccessResourceFailureException` raised by any outbound port during reconciliation returns `503` `urn:unihub:error:service-unavailable` with a `Retry-After` header, not `500` (Scenario 23; BR-02).
    - The failure path releases the idempotency claim, and a second failure inside the release does not mask the original error (FR-19).
    - No attendance row is written and no event is published on the `503` path (Scenario 23).
    - The use case remains non-read-only transactional, so every read and write is routed to the primary (BR-02).
    - With L1 Redis unavailable, a replayed request is still deduplicated by the durable L2 `idempotency_keys` row — the use case returns the replayed `207` when `IdempotencyPort` resolves from L2, and the rate limiter fails open (BR-15, NFR Reliability; existing `RedisIdempotencyAdapterTest` already covers the adapter).

#### Checkpoint: Feature Complete

- [ ] All seven tasks are checked off.
- [ ] Every acceptance criterion across Scenarios 1–24 is traced to a passing unit/MockMvc test; no integration test was introduced.
- [ ] `./gradlew test --tests "com.unihub.workshop.ModulithArchitectureTest"` passes.
- [ ] The targeted test classes for T1–T7 pass; the human runs the full backend suite separately and reports any failure (see §6).
- [ ] Human reviewed the T5–T7 diffs.
- [ ] Ready for `/review attendance-sync-reconciliation`.

## 4. Risks and Mitigations

| Risk                                                                                                                                                                                               | Impact | Mitigation                                                                                                                                                                                                                                                              |
| :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :----- | :---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **T1 is a net-new module slice spanning ~22 production files**, above the ~10-file guideline.                                                                                                      | Medium | Keep T1 as one M task because every file is required for a single end-to-end-testable capability; most files are thin records/enums and the two facade extensions are additive. Explicitly accepted by the human on 2026-10-31 (see §5).                                |
| **Concurrent batches both read "no existing record" and both attempt the insert**; an exception-driven adapter would surface the loser as `409` instead of `ALREADY_USED`.                         | High   | One `INSERT ... ON CONFLICT DO NOTHING RETURNING sync_id`; every entry whose `syncId` is not in the returned set is classified `ALREADY_USED` (BR-05, BR-10). The adapter test asserts returned-identifier reconciliation, and the use-case test covers the loser path. |
| **Jackson maps a malformed UUID/instant to `400`** (`HttpMessageNotReadableException`), contradicting the contract's `422` for field formats.                                                      | Medium | T4 types the DTO fields as strings and validates/parses them in `AttendanceSyncBatchValidator`; a regression test asserts `422` for a malformed UUID and a non-ISO `scannedAt`, while syntactically broken JSON stays `400`.                                            |
| **Validation ordering:** `@Valid` on `@RequestBody` runs before the controller method, so a request with both a missing `Idempotency-Key` and an invalid body would return `422` instead of `400`. | Medium | No `@Valid` on the body; the controller validates the header/path first and then invokes the batch validator. A controller test covers the combined-failure case.                                                                                                       |
| **Replay payload typing:** storing a presentation DTO in the idempotency record would invert the dependency direction.                                                                             | Medium | Store `AttendanceOutcome[]` (a domain type) and re-map on replay; the test asserts the replayed JSON body is identical to the first response.                                                                                                                           |
| **`workshops.starts_at` is nullable**, so the `checked_in_at` lower clamp has no bound for an anomalous workshop row.                                                                              | Low    | `Attendance.createOfflineSync(...)` treats a `null` `starts_at` as "no lower bound" and still clamps to `now()`; unit-tested.                                                                                                                                           |
| **The after-commit count query can observe a commit that happened after this request**, making `checkedInCount` a slightly later snapshot.                                                         | Low    | The event is defined as a snapshot (`lastSyncedAt` = sync time); F-6.3 owns the authoritative summary endpoint and reconciles on read. Documented in `AttendanceSyncEventPublisher`'s Javadoc.                                                                          |
| **`docs/project-structure.md`'s module graph shows only `Attendances → REG_API`**, while the catalog also requires `workshops::api`.                                                               | Low    | `docs/feature-catalog.md` §6.2 is authoritative and lists `workshops/api`; no code impact. If the human wants the diagram corrected, raise it as a mid-build improvement proposal rather than editing `docs/` during `/build`.                                          |

## 5. Open Questions

None. Both planning questions were resolved by the human on 2026-10-31:

1. **T1 sizing** — accepted as a single M task (~22 production files), because every file is required for one end-to-end-testable capability and splitting per layer is forbidden. No preparatory facade task is created.
2. **`503` translation scope** — `DataAccessResourceFailureException` is translated locally inside the attendances use case (`ServiceUnavailableException`); `shared/web/GlobalExceptionHandler` is left unchanged.

## 6. Build Execution Notes

These constraints apply to every `/build attendance-sync-reconciliation <task-id>` run, in addition to `AGENTS.md` §5.

- **Targeted test execution only.** Run only the test classes covering the files changed by the current task (for example `./gradlew test --tests "com.unihub.workshop.attendances.application.usecases.SyncAttendancesUseCaseImplTest"`), plus `ModulithArchitectureTest` as the architecture regression guard required by `AGENTS.md` §6. Do **not** re-run the whole module or the whole backend suite after each task; the human runs the full suite and reports any failure.
- **Complete imports before use.** Every type used in a file must have its explicit import in place (the codebase convention) — no wildcard imports and no relying on a later cleanup pass.
- **Warnings are defects.** Any compiler or IDE warning introduced by the change (unused import, unchecked cast, raw type, deprecated API, nullability, etc.) must be fixed at the root cause. Do not suppress it with `@SuppressWarnings`, `//noinspection`, build-config exclusions, or by ignoring it.
