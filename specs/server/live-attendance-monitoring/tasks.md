# Implementation Plan: Live Attendance Monitoring

> **Spec:** `specs/server/live-attendance-monitoring/SPEC.md`  
> **API Contract:** `contracts/http/features/live-attendance-monitoring.md`  
> **Status:** APPROVED

## 1. Overview

Repair and regression-check the existing streaming, assignment-provisioning, and registration-summary capabilities before introducing attendance monitoring. After the human verifies those APIs, build a complete Organizer summary slice, extend that same read to assigned Staff, and complete unavailable-dependency and admission behavior. This plan changes backend only, creates no new client or worker capability, and retains the approved summary/event contracts. Every task is separately invoked with `$server-build live-attendance-monitoring <task-id>`; plan approval does not start implementation.

## 2. Architecture Decisions for This Plan

- **Repair before expansion:** T1–T3 fix existing contractual defects. Checkpoint A is a blocking human API-regression gate before T4. These repairs are authorized companion work under SPEC §8 and the human's planning instruction, not new requirements retrofitted into older features.
- **Current code was checked:** Both feature inputs are `APPROVED`. The original three findings remain present: nested `Instant` serialization in the Redis publisher; provisioning entry-point `readOnly = true`; and registration-summary fallback to `startsAt`/`now()` instead of workshop `updatedAt`. Existing task plans describe these risks. Do not treat their historical completion checkboxes as proof that the corrections are already implemented.
- **Verify physical routing, not just annotations:** T2 also covers connection acquisition timing. The current configuration exposes the routing datasource directly. In the locally installed Spring 7.0.7 sources, transaction begin may acquire a JDBC connection before transaction synchronization publishes its read-only flag. The first regression must exercise statement execution through the configured datasource with mocked JDBC dependencies; setting thread flags by hand alone is insufficient evidence. This is source-level evidence, not a claim that a live API experiment has been performed.
- **Protect existing public DTO callers:** T3 adds a narrow scalar workshop-timestamp query to the existing public facade rather than adding a constructor field to `WorkshopRegistrationDetailsDto`, whose constructor is used throughout registration/payment tests. The existing reporting transaction supplies its Replica routing. No peer repository access or unrelated DTO/mapper rewrite is required.
- **One full baseline slice:** T4 implements a usable Organizer summary with actual counts and the complete recent preview, across all layers. It does not ship placeholder counts, an always-empty preview, a schema differing from the approved contract, or a persistence-only preparatory task. The human explicitly accepted approximately 16–18 files for this one baseline task on 2026-11-06; its expected scope below is 18 files including tests/configuration. All other tasks remain small or medium within approximately ten files.
- **Two explicit transactions:** The new summary coordinator has no encompassing transaction. It owns two configured `TransactionTemplate` operations, initialized once: Primary context/authorization with `readOnly = false`, then separate Replica reporting with `readOnly = true`. Use independent transaction propagation so an ambient caller cannot pin both phases to one datasource. No annotation-based self-invocation or parallel reads across these phases. The transaction policy changes routing, not the read-only business behavior.
- **Reuse actual public surfaces:** Reuse `WorkshopApi.getWorkshopForRegistration` and `isStaffAssignedToWorkshop`; add only a confirmed-count scalar to `RegistrationApi`. For that scalar, initially reuse the existing `RegistrationRepository.getRegistrationRawCounts(...).confirmedCount()` path: it is workshop-scoped and bounded, although it also obtains pending count/latest update. Do not confuse the active-count SPI with confirmed count. Any later count-query optimization requires measurement and the normal improvement process rather than a speculative extra task here.
- **Keep models cohesive:** Use one pure `AttendanceSummary` read model with existing internal `Attendance` values for the preview. A dedicated REST response record may contain its own four-field recent-scan item record; neither record is reused as a persistence entity. The existing presentation MapStruct mapper projects only the approved wire fields. The inbound query stays in `application/ports/in`.
- **Index belongs to its first caller:** T4 includes the additive partial index and migration configuration needed by the preview. Use `CREATE INDEX CONCURRENTLY` rather than blocking attendance writes. PostgreSQL requires that command outside a transaction; the installed Flyway 11.14.1 PostgreSQL parser already recognizes it as non-transactional. Configure session-level Flyway advisory locking with `spring.flyway.postgresql.transactional-lock=false`, keeping other migrations' normal transaction behavior. Deploy the index before relying on the query access path. See the technical references in §7.
- **Existing security plumbing stays authoritative:** Current `GlobalExceptionHandler` already supplies a default `Retry-After` for `ServiceUnavailableException`, including revocation-check failure. Verify that path in T6 instead of inventing another legacy defect or changing a working security policy. Summary quota uses the existing default tier; no new bucket, stream, or idempotency engine is added.

## 3. Task List

### Phase 1: Restore Existing API Contracts

- [x] **T1 — Existing attendance updates reach SSE consumers with UTC text timestamps and unchanged publishing semantics**
  - **Size:** S — 4 expected files.
  - **Depends on:** —
  - **Layers touched:** Existing attendance application event production; shared realtime infrastructure and wire representation.
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/shared/realtime/infrastructure/RedisRealtimePublisherAdapter.java` — correct nested timestamp serialization in its actual publisher configuration.
    - `apps/backend/src/test/java/com/unihub/workshop/shared/realtime/infrastructure/RedisRealtimePubSubTest.java` — inspect JSON passed to mocked Redis and retain channel/delivery behavior.
    - `apps/backend/src/test/java/com/unihub/workshop/shared/realtime/infrastructure/RealtimeEventSerializationTest.java` — use real timestamp-valued payload fixtures, not just preformatted strings; check the supported event types.
    - `apps/backend/src/test/java/com/unihub/workshop/attendances/application/events/AttendanceSyncEventPublisherTest.java` — preserve producer guards and timestamp provenance through regression coverage.
  - **Acceptance Criteria:**
    - A committed sync inserting new attendance still returns its existing `207`; the resulting `attendance.updated` has exactly `{workshopId, checkedInCount, capacity, lastSyncedAt}`, with nested `lastSyncedAt` and envelope `timestamp` as ISO-8601 UTC JSON strings. The regression must fail for an actual `Instant` payload before the fix. _(F-6.2 FR-42–FR-46; F-6.3 FR-32, Scenario 17; API contract §6.)_
    - A same-key replay or a new-key retry inserting zero rows publishes no new attendance update; rollback publishes none. Counter sampling remains Primary and counts `VALID` rows. _(F-6.2 FR-43–FR-45; F-6.3 FR-33.)_
    - All seven existing event types retain their event names, payload fields, identifier/count types, target channels, and string timestamp convention. Publication failure remains best effort and does not turn an already committed sync into failure. _(F-10.5 event catalog; F-6.3 BR-12.)_
    - The existing stream still routes workshop events only to authorized organizers/assigned staff and personal events only to their recipients; stream framing/heartbeat behavior is unchanged. Run the existing routing/registry tests even if their files need no edits. _(F-6.3 FR-34.)_
  - **Human API retest:** Sync a new valid scan while observing the existing stream, then repeat it with the same and a fresh idempotency key. Verify `207` outcomes, textual timestamps, one event for the first insert, no extra event/attendance for retries, and at least one other existing event family after changing the shared serializer.

- [x] **T2 — Assigned staff immediately see current workshop assignments while existing reporting and mutation requests keep the correct datasource**
  - **Size:** M — up to 9 expected files, including the existing Primary-unavailable response repair approved in `improvement-proposal.md`.
  - **Depends on:** T1 — execution order for the legacy repair phase; no new streaming dependency is introduced.
  - **Layers touched:** Workshop application transaction policy, shared datasource infrastructure, existing presentation path and its unit verification.
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/usecases/ListStaffAssignedWorkshopsUseCaseImpl.java` — Primary-routed provisioning entry transaction.
    - `apps/backend/src/main/java/com/unihub/workshop/shared/database/DataSourceRoutingConfig.java` — defer physical routing/connection acquisition with the existing Spring `LazyConnectionDataSourceProxy`; retain the routing bean identity and existing target pools.
    - `apps/backend/src/test/java/com/unihub/workshop/shared/database/DataSourceRoutingConfigTest.java` — verify the configured proxy/target chain and pool configuration without opening a real connection.
    - `apps/backend/src/test/java/com/unihub/workshop/shared/database/TransactionAwareRoutingDataSourceConnectionTest.java` — new isolated transaction/statement regression with mocked datasources, connections, and statements.
    - `apps/backend/src/test/java/com/unihub/workshop/workshops/application/usecases/ListStaffAssignedWorkshopsUseCaseImplTest.java` — provisioning transaction and existing filters.
    - `apps/backend/src/test/java/com/unihub/workshop/workshops/infrastructure/persistence/WorkshopAssignmentRepositoryAdapterProvisioningTest.java` — new unit coverage of parameterized native SQL, bounds, seek direction, and row mapping with persistence mocked.
    - `apps/backend/src/test/java/com/unihub/workshop/workshops/presentation/controllers/StaffWorkshopControllerTest.java` — preserve existing REST behavior with its use case mocked.
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/presentation/controllers/StaffWorkshopController.java` or `apps/backend/src/main/java/com/unihub/workshop/shared/web/GlobalExceptionHandler.java` — narrowly translate the verified Primary connection-availability failure to the existing `503` contract. Select the appropriate boundary after tracing the actual exception; do not map unrelated transaction, SQL, or programming failures to `503`.
    - If the shared exception handler is changed, `apps/backend/src/test/java/com/unihub/workshop/shared/web/GlobalExceptionHandlerTest.java` — verify the intended classification and preserve other handlers' error semantics. Reuse the controller test above for endpoint-specific behavior.
  - **Acceptance Criteria:**
    - `GET /api/v1/workshops/assigned` sees an assignment committed on Primary even while Replica lacks it, and no longer treats a revoked Primary assignment as valid solely because Replica lags. Provisioning still selects only the authenticated staff member's non-canceled workshops. _(F-6.1 FR-03–FR-06, BR-01–BR-03.)_
    - The real statement-execution path in the isolated transaction test selects Primary for provisioning/write transactions, Replica for ordinary read-only reporting, and Primary outside a transaction. Nested participating facade/repository calls retain the enclosing transaction's route; transaction completion restores thread state. Test selection at statement execution, not only `determineCurrentLookupKey()` in isolation. Default-connection metadata initialization is distinguished from business SQL. _([server ADR-011](../../../docs/server/adrs/ADR-011-primary-replica-routing-strategy.md); shared routing rules; F-6.3 FR-09/FR-24 prerequisites.)_
    - Provisioning's native SQL still honors `startsAt ASC NULLS LAST, id ASC`, `size + 1`, existing filter fingerprints, default/max sizes 20/100, and existing `400` validation behavior. Do not replace that order with a generic descending helper. _(F-6.1 FR-07–FR-21.)_
    - Endpoint role and success response shape remain unchanged. Repair the existing Primary-unavailable failure path: a verified Primary connection-availability failure produces the standard `503` and `Retry-After`, with no Replica fallback. Reproduce the actual lazy-connection/transaction exception chain with mocked JDBC before choosing the narrow translation; do not classify unrelated SQL, validation, authorization, transaction, or programming failures as availability errors. Preserve fail-closed security and make no business write. _(F-6.1 API contract.)_
    - Existing registration, payment, idempotency, sync, lifecycle, and read-only reporting unit suites remain green after the shared datasource change. Never route a mutation or its participating reads to Replica to fix an assertion. _([server ADR-010](../../../docs/server/adrs/ADR-010-idempotency-mechanism.md)/[server ADR-011](../../../docs/server/adrs/ADR-011-primary-replica-routing-strategy.md); F-6.2 Primary requirement.)_
  - **Human API retest:** On an upcoming published workshop, assign/revoke staff through the existing administration API and immediately query their assigned-workshop feed. Retest its pagination/filter errors, an existing read-only report, a registration mutation, and sync. Pool/connection evidence comes from tests or existing observability, never a new debug response field. Use separate fixtures for assignment timing and on-site sync.

- [x] **T3 — Existing registration summary reports the workshop update time when no registrations exist**
  - **Size:** M — 6 expected files.
  - **Depends on:** T2 — datasource routing is verified before changing an existing Replica reporting path.
  - **Layers touched:** Workshop public facade/application read, registration application reporting, existing presentation response and regression verification.
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/api/WorkshopApi.java` — additive scalar `getWorkshopUpdatedAt(...)` query, with absence represented explicitly.
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/facade/WorkshopFacade.java` — resolve the timestamp through its owned repository; participate in the caller's read transaction.
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/application/usecases/GetWorkshopRegistrationSummaryUseCaseImpl.java` — use that timestamp only for the no-registration fallback; do not fall back to schedule time or `now()`.
    - `apps/backend/src/test/java/com/unihub/workshop/workshops/application/facade/WorkshopFacadeTest.java` — known/missing workshop timestamp query.
    - `apps/backend/src/test/java/com/unihub/workshop/registrations/application/usecases/GetWorkshopRegistrationSummaryUseCaseTest.java` — failing no-registration regression with distinct timestamps and an unscheduled draft.
    - `apps/backend/src/test/java/com/unihub/workshop/registrations/presentation/controllers/WorkshopRegistrationMonitoringControllerTest.java` — new controller unit coverage, use cases mocked, preserving wire representation and existing summary/roster permissions.
  - **Acceptance Criteria:**
    - `GET /api/v1/admin/workshops/{workshopId}/registrations/summary` returns `lastUpdatedAt == workshop.updatedAt` when no registrations exist, including a draft with no schedule. Choose fixtures where `updatedAt`, `startsAt`, and current time differ so the regression fails before the fix. _(F-4.4 FR-08, Scenario 3; API contract summary fields.)_
    - With registrations present, the latest registration update remains authoritative; counts, seat availability, occupancy calculation, and response shape remain unchanged. No unnecessary fallback lookup occurs when a registration timestamp is available. _(F-4.4 FR-04–FR-09.)_
    - Existing missing-workshop `404`, cross-organization `403`, and role restrictions remain intact. No timestamp or other business value is fetched by reaching into another module's repository/table. The additional lookup is constant per empty summary, not per row. _(F-4.4 FR-01–FR-03, BR-06.)_
    - The constructor/fields of `WorkshopRegistrationDetailsDto` and its registration/payment callers are unchanged; its existing facade tests and the full backend suite pass. _(Module boundary and compatibility requirements.)_
  - **Human API retest:** Retrieve an owned empty workshop's registration summary using a known `updatedAt`, then a workshop with registrations. Include an unscheduled draft, a foreign organizer, missing workshop, and the existing roster to confirm the shared controller still behaves correctly.

#### Checkpoint A — Existing APIs must be stable before F-6.3 starts

- [x] T1–T3 have failing-before/passing-after regressions, their acceptance criteria pass, the full backend unit suite passes, and `ModulithArchitectureTest` passes.
- [x] The human has retested the affected existing APIs and explicitly confirmed the legacy baseline is stable. Automated green results alone do not open this gate.
- [x] Any regression in an existing feature introduced by these changes has been reproduced and repaired in the responsible task, with a failing regression guard and repeated relevant/full unit verification. If reported after a checkbox was completed, reopen that task; do not proceed to T4 while the failure remains.
- [x] No attendance-summary endpoint, F-6.3 confirmed-count facade extension, reporting index, or other F-6.3 production change has begun before this gate clears. T3's timestamp query belongs to the existing registration-summary repair.

This checkpoint is a gate, not an extra implementation task. Passing it does not invoke the next task; the human separately invokes `$server-build live-attendance-monitoring T4`.

### Phase 2: Complete Organizer Monitoring Baseline

- [x] **T4 — An authorized Organizer can retrieve the complete attendance summary with real metrics and the newest-ten preview**
  - **Size:** M, explicitly accepted scope exception — 18 expected files across the complete vertical slice.
  - **Depends on:** T1, T2, T3, **and human confirmation of Checkpoint A**.
  - **Layers touched:** Pure domain read model/calculation, application inbound port/orchestration, public registration facade, attendance persistence, additive schema/configuration, presentation DTO/controller/MapStruct mapping.
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/attendances/domain/model/AttendanceSummary.java` — new pure immutable summary and half-up calculation; internal preview values may reuse `Attendance`.
    - `apps/backend/src/main/java/com/unihub/workshop/attendances/application/ports/in/GetAttendanceSummaryUseCase.java` — new inbound capability.
    - `apps/backend/src/main/java/com/unihub/workshop/attendances/application/ports/in/GetAttendanceSummaryQuery.java` — new caller/workshop query values.
    - `apps/backend/src/main/java/com/unihub/workshop/attendances/application/usecases/GetAttendanceSummaryUseCaseImpl.java` — new two-phase coordinator with configured transaction operations; Organizer access in this slice.
    - `apps/backend/src/main/java/com/unihub/workshop/attendances/application/ports/out/AttendanceRepository.java` — add the fixed bounded recent-valid read; retain the existing valid-count operation.
    - `apps/backend/src/main/java/com/unihub/workshop/attendances/infrastructure/persistence/AttendanceRepositoryAdapter.java` — parameterized workshop/`VALID` query with `checked_in_at DESC, id DESC` and fixed limit 10; reuse existing JDBC row handling.
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/api/RegistrationApi.java` — additive scalar `countConfirmedByWorkshopId(...)` for this real peer caller.
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/application/facade/RegistrationFacade.java` — delegate to the existing owned raw-count port and return its confirmed count, joining the reporting transaction.
    - `apps/backend/src/main/java/com/unihub/workshop/attendances/presentation/controllers/AttendanceSummaryController.java` — new approved GET path and standard envelope; caller identity is extracted from authentication.
    - `apps/backend/src/main/java/com/unihub/workshop/attendances/presentation/dto/AttendanceSummaryResponse.java` — new REST record with the six approved fields and a nested four-field scan item record.
    - `apps/backend/src/main/java/com/unihub/workshop/attendances/presentation/mapper/AttendancePresentationMapper.java` — generated inbound query and summary/scan mappings; no manual boundary object assembly.
    - `apps/backend/src/main/resources/db/migration/V3__add_valid_attendance_reporting_index.sql` — next unused migration version (currently V3), additive concurrent partial index only; never edit V1/V2.
    - `apps/backend/src/main/resources/application.yml` — Flyway session advisory-lock setting required by the concurrent index; retain transaction defaults for other migrations.
    - `apps/backend/src/test/java/com/unihub/workshop/attendances/domain/model/AttendanceSummaryTest.java` — new framework-free calculation/immutability tests.
    - `apps/backend/src/test/java/com/unihub/workshop/attendances/application/usecases/GetAttendanceSummaryUseCaseImplTest.java` — new port-mocked authorization, routing order, actual counts, and read-only behavior tests.
    - `apps/backend/src/test/java/com/unihub/workshop/attendances/infrastructure/persistence/AttendanceRepositoryAdapterTest.java` — mocked JDBC query/bounds/mapping coverage; retain sync adapter regressions.
    - `apps/backend/src/test/java/com/unihub/workshop/registrations/application/facade/RegistrationFacadeTest.java` — confirmed-only scoped count and participating transaction coverage.
    - `apps/backend/src/test/java/com/unihub/workshop/attendances/presentation/controllers/AttendanceSummaryControllerTest.java` — new standalone controller tests with use case mocked and the actual generated presentation mapper for wire/mapping assertions; no application context.
  - **Acceptance Criteria:**
    - An owned workshop requested at `GET /api/v1/workshops/{workshopId}/attendances/summary` returns `200` and exactly `workshopId`, `capacity`, `confirmedCount`, `checkedInCount`, `checkInPercentage`, `recentScans` inside the standard success envelope. No pagination controls/container or inferred summary `lastSyncedAt` is introduced. _(FR-11–FR-23, FR-31; API contract §3; Scenarios 1, 8–12.)_
    - For confirmed count 80, pending 7, failed 3, canceled 4, valid attendance 50 and invalidated attendance 2, the returned counts are 80/50 and the percentage is `62.50`. A valid attendance linked to a canceled registration is still counted. Verify `1/32 → 3.13`, `2/3 → 66.67`, `3/2 → 150.00`, and `1/0 → 0.00`; never use capacity or active-registration count as denominator. _(FR-12–FR-16; Scenarios 1, 9–10.)_
    - Empty replica-visible reporting is `200` with zero counts, zero percentage and `[]`; an existing draft's absent capacity becomes `0`. No empty dataset is mistaken for a missing Primary workshop. Owned workshops in all four states are readable without a schedule gate. _(FR-10–FR-11, FR-15, FR-17; Scenarios 7–8.)_
    - The preview returns all eligible records when fewer than 10 exist, otherwise exactly the newest 10 by `checkedInAt DESC, id DESC`. It excludes other workshops and invalidated rows, exposes only the four documented item fields, preserves both timestamps, and does not reorder an older offline scan by recent arrival time. Retry outcomes create no duplicates. _(FR-18–FR-23; Scenarios 11–12.)_
    - Organizer organization context is required; missing/invalid context or a foreign existing workshop yields `403`, missing workshop `404`, malformed path UUID `400`. No Replica reporting happens on denied access. `STUDENT` is forbidden; successful `STAFF` access is deliberately enabled in T5, not represented by a fake successful response in this slice. _(FR-01–FR-06, FR-09; Scenarios 3–5.)_
    - Primary authorization/metadata finishes before Replica registration/attendance reads. With Primary count 60 and Replica count 58, return 58 with Primary capacity. No encompassing transaction, hidden Primary reporting query, cross-module SQL aggregation, per-scan identity lookup, or direct peer-internal dependency is added. Verify transaction definitions and sequencing as well as reuse of T2's physical routing guard. _(FR-09, FR-24, BR-02/BR-07; Scenario 13.)_
    - Reading/repeating the summary without an idempotency key changes no business row, creates no claim, publishes no event, and starts no stream/job. UTC timestamp representation, required empty fields, metadata, and response field minimization are asserted through the REST boundary. _(FR-29–FR-31; Scenario 16.)_
    - The concurrent partial index has the documented name/predicate/key order; old code remains compatible. The migration executes on a session-capable Primary connection, outside a transaction as detected by the PostgreSQL parser, without disabling transactional execution globally. Do not apply session advisory locking through a transaction-pooled connection. No actual database execution is part of the automated tests. _(SPEC performance NFR; database schema/index rules.)_
  - **Human API retest:** Apply the reviewed additive migration in the controlled development environment, then test owned/foreign/missing workshops, empty draft, nonempty summaries, more than ten scans, equal scan timestamps, late arrivals and percentage boundaries. Read back the existing registration summary and provisioning APIs as regression checks. This task's manual verification does not mark Staff support or final failure coverage complete.

#### Checkpoint B — Organizer slice is real and reviewable

- [x] T4 serves real data through the complete REST path; no placeholder fields, fake empty preview, alternate endpoint, or unbounded read remains.
- [x] T1–T3 still pass, full backend unit tests and static architecture checks pass, and the human's Organizer/API regression checks are green.
- [x] The new index is verified valid in the controlled manual environment before relying on its access path. A failed/incomplete concurrent index is not hidden with `IF NOT EXISTS` or treated as a completed migration.
- [x] T4's accepted size exception has not silently expanded beyond its declared scope; material new files/architecture changes are surfaced for plan review.

### Phase 3: Assigned Staff and Failure Semantics

- [x] **T5 — Assigned Staff can read the same summary while unassigned or revoked Staff cannot**
  - **Size:** S — 6 expected files. Gate 1 approved the query/mapper scope correction in `improvement-proposal.md`.
  - **Depends on:** T4 and Checkpoint B.
  - **Layers touched:** Existing summary application authorization and REST admission; existing workshop public facade is reused.
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/attendances/application/ports/in/GetAttendanceSummaryQuery.java` — carry the authenticated caller role and user ID alongside the workshop and optional organizer organization context.
    - `apps/backend/src/main/java/com/unihub/workshop/attendances/presentation/mapper/AttendancePresentationMapper.java` — map authenticated identity and role into the query through MapStruct.
    - `apps/backend/src/main/java/com/unihub/workshop/attendances/application/usecases/GetAttendanceSummaryUseCaseImpl.java` — enable the Staff branch of the Primary authorization phase.
    - `apps/backend/src/main/java/com/unihub/workshop/attendances/presentation/controllers/AttendanceSummaryController.java` — allow both approved roles, derive user ID and role from authentication, and extract organization context only for Organizer.
    - `apps/backend/src/test/java/com/unihub/workshop/attendances/application/usecases/GetAttendanceSummaryUseCaseImplTest.java` — assigned/unassigned/revoked and phase ordering.
    - `apps/backend/src/test/java/com/unihub/workshop/attendances/presentation/controllers/AttendanceSummaryControllerTest.java` — identical response schema and role behavior with use case mocked.
  - **Acceptance Criteria:**
    - A staff JWT with an existing Primary assignment receives the same `200` summary even if its organization differs or it has no organizer organization context. Only `jwt.sub` identifies the staff member; no query/body override is accepted as authority. _(FR-07–FR-09; Scenario 2; API contract §3.)_
    - No assignment, a same-organization-but-unassigned caller, or a Primary revocation not yet reflected on Replica yields `403` before any reporting read. A missing workshop still yields `404`. _(FR-05, FR-07, FR-09; Scenarios 5–6.)_
    - Assigned Staff can report all four workshop states without a time gate; that permission does not enable sync for `DRAFT` or `CANCELED` and does not change the provisioning feed's canceled-workshop rule. _(FR-10; Scenario 7; API contract §6.)_
    - Organizer behavior, `STUDENT` denial, field minimization, replica sourcing, and existing sync/assignment authorization remain unchanged. No IAM dependency or new assignment-state field is added. _(FR-01–FR-24, BR-01.)_
  - **Human API retest:** Use assigned, unassigned, same-organization-unassigned, and revoked Staff identities against the summary; include a canceled historical workshop. Retest Organizer access, the Staff assigned-workshop feed, and sync eligibility with the same role boundaries.

- [x] **T6 — Summary failures preserve retry hints, security, admission quotas, and a recoverable baseline without breaking existing APIs**
  - **Size:** M — up to 10 expected files, including the scoped unexpected-error handling and rate-limit fail-open repair approved in `improvement-proposal.md`.
  - **Depends on:** T5.
  - **Layers touched:** Summary application/transaction failure boundary, presentation error serialization, shared security/admission behavior through unit verification.
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/attendances/application/usecases/GetAttendanceSummaryUseCaseImpl.java` — translate unavailable required reads/transaction acquisition into the existing service-unavailable failure; retain unexpected failures as internal errors.
    - `apps/backend/src/main/java/com/unihub/workshop/attendances/presentation/controllers/AttendanceSummaryController.java` or `apps/backend/src/main/java/com/unihub/workshop/shared/web/GlobalExceptionHandler.java` — translate unexpected state/argument exceptions to a sanitized `500` at the summary endpoint only; select the narrowest boundary verified by exception-resolution tests.
    - `apps/backend/src/main/java/com/unihub/workshop/shared/ratelimit/RateLimitFilter.java` — restrict fail-open handling to quota-backend evaluation; invoke downstream filters once and propagate their failures without falsely recording a Redis outage.
    - `apps/backend/src/test/java/com/unihub/workshop/attendances/application/usecases/GetAttendanceSummaryUseCaseImplTest.java` — Primary/Replica failures, completion/cleanup, no fallback, no partial summary.
    - `apps/backend/src/test/java/com/unihub/workshop/attendances/presentation/controllers/AttendanceSummaryControllerTest.java` — exact status/envelope/content type, retry header, metadata, repeated reads and UTC representation.
    - `apps/backend/src/test/java/com/unihub/workshop/shared/security/JwtAuthenticationFilterTest.java` — new endpoint authentication failure paths with decoder/validator/resolver mocked.
    - `apps/backend/src/test/java/com/unihub/workshop/shared/security/RedisJtiValidatorTest.java` — preserve fail-closed behavior and outage observability.
    - `apps/backend/src/test/java/com/unihub/workshop/shared/ratelimit/RateLimitFilterTierRoutingTest.java` — new GET uses default tier, sync and stream routes retain their existing tiers.
    - `apps/backend/src/test/java/com/unihub/workshop/shared/ratelimit/RateLimitFilterFailOpenTest.java` — admission-only fail-open with backend dependencies mocked.
    - `apps/backend/src/test/java/com/unihub/workshop/shared/web/GlobalExceptionHandlerTest.java` — reuse existing `503` default retry handling and standard `400/403/404/405/500` error representations.
  - **Acceptance Criteria:**
    - Unavailable Primary workshop/permission reads or Replica registration/count/preview reads yield `503`, `urn:unihub:error:service-unavailable`, and a positive `Retry-After` through the real error representation. Include failure while acquiring the transaction/connection, not only a repository callback failure. No successful partial/zero substitute or Primary reporting fallback is returned. _(FR-25, BR-08; Scenario 14; API contract error table.)_
    - Unverifiable JTI revocation yields the existing fail-closed `503` with its shared retry hint; missing/invalid/expired/revoked authentication stays `401`. An admission-backend outage cannot bypass these checks. Reuse working shared production behavior rather than weakening it to make a new endpoint test pass. _(FR-01, FR-26, FR-28; Scenarios 3, 14–15.)_
    - Exhausting the user's shared default quota of 100 requests/60 seconds yields `429` with retry/rate headers. Quota evaluation alone failing permits an otherwise authenticated/authorized read. A downstream filter or handler exception propagates without re-invoking the chain or emitting a false Redis-outage metric; fail-open does not bypass authentication. Stream handshakes still use 10/60 seconds. _(FR-27–FR-28; Scenario 15; API contract §4.)_
    - Documented `400/401/403/404/405/429/500/503` outcomes retain standard envelopes/content types and no leaked internals. Unexpected mapping/programming failures, including `IllegalStateException` and `IllegalArgumentException` in the summary handler, yield sanitized `500`, not `422` or a retryable datasource outage. Verify framework binding `400`, explicit domain errors and unrelated endpoints' existing error behavior remain unchanged. No new state-validation, idempotency-conflict, or `207` response is added to GET. _(API contract §3.)_
    - A fresh read after reconnect can recover replica-visible state without `Last-Event-ID` replay or an idempotency key. A returned count older than the latest SSE count is permitted; it is not labeled an atomic/newer snapshot or equipped with a fabricated timestamp/version. Repeated reads emit no event. _(FR-29–FR-34, BR-10–BR-12; Scenarios 13, 16–17.)_
    - All existing API regressions from Phase 1 remain green after the final failure-path changes. If a manual regression is found, reopen the responsible task and repair it before claiming feature readiness. _(Human instruction; project Definition of Done.)_
  - **Human API retest:** Verify the status/error matrix and retry hints in a controlled environment, then restore dependencies and verify recovery. Reconnect the existing stream and read the summary after a committed sync; repeat a sync retry to confirm no extra event/count. The plan does not require implementing a client refresh scheduler or automatically performing outage experiments against shared infrastructure.

#### Checkpoint C — Implementation ready for human-invoked review

- [x] T1–T6 are complete and every task acceptance criterion, not only each checkbox, has been verified.
- [x] All 34 FRs, 12 BRs, and 17 spec scenarios are covered by the task mapping below; client refresh/rendering obligations remain contract-only.
- [x] Full backend unit suite and `ModulithArchitectureTest` pass; no integration test, app-context test, Testcontainers, or real service was introduced in automated verification.
- [x] The human's existing-API regression checks and new Organizer/Staff summary API checks pass. No unresolved regression is carried forward.
- [x] The migration is additive and compatible with previous backend code; data and existing uniqueness constraints are preserved.
- [x] Ready for the human to invoke `$server-review live-attendance-monitoring`. Do not run `$server-review`, `$server-fix`, or another `$server-build` automatically.

## 4. Definition of Done and Regression Discipline

1. Before each task, establish the relevant test baseline. Write a failing regression/behavior test before changing business implementation, then work inward-to-outward within that single capability. Pure migration/configuration edits follow their applicable validation policy rather than a test that merely repeats a file's text.
2. At the end of every task, run from `apps/backend/`:

   ```powershell
   .\gradlew.bat test
   .\gradlew.bat test --tests "com.unihub.workshop.ModulithArchitectureTest"
   ```

   Targeted tests support diagnosis but never replace the full suite. Do not skip, disable, weaken, or delete failing tests/security checks to obtain green results. Workers need no build/test run because no worker files change.

3. Domain tests remain framework-free; application tests mock outbound ports/peer facades; persistence tests mock JDBC/JPA clients; presentation tests mock use cases. Plain transaction-manager/proxy objects with mocked datasources are allowed isolated unit subjects, not a Spring application context or live integration fixture. Generated MapStruct mapping is exercised inside presentation unit tests without starting the application.
4. Every task ends with its diff, automated results, exact affected API routes, fixture prerequisites, and expected manual outcomes for the human. Keep tokens, credentials, and real connection strings out of documentation/logs. Manual API checks are operator verification against a controlled running environment, not new automated integration tests.
5. If a task breaks an existing feature, apply debugging inline: reproduce the smallest failure, locate the cause, add a failing regression, repair it, rerun relevant and full checks, and repeat the human API check. Stay in/reopen the responsible task. Neither a passing targeted test nor the next task is a substitute for resolving the regression.
6. If resolving a failure requires a business-contract change, reordered/new capability, or an unapproved architecture/schema change, follow the improvement-proposal process before coding that deviation. Do not silently expand this plan or reinterpret an approved requirement. Routine fixes restoring the approved behavior remain within the responsible task.
7. No task is started by another task's completion. The human invokes builds one at a time and owns all git writes. Never self-mark `tasks.md` approved or the feature DONE.

## 5. Traceability

| Requirement / scenario         | Planned coverage                                                                                                                                                               |
| :----------------------------- | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| FR-01–FR-06; Scenarios 3–5     | T4 resource admission and Organizer scope; T6 shared authentication/error boundary.                                                                                            |
| FR-07–FR-08; Scenarios 2, 5–6  | T5 Staff assignment branch and stale-revocation denial.                                                                                                                        |
| FR-09–FR-10; Scenarios 6–7, 13 | T2 routing prerequisite; T4 split phases/all states; T5 Staff Primary checks.                                                                                                  |
| FR-11–FR-18; Scenarios 1, 8–10 | T4 actual metrics, empty/draft behavior, uncapped calculation and workshop scope.                                                                                              |
| FR-19–FR-23; Scenarios 11–12   | T4 bounded preview, provenance, index and persisted-record semantics.                                                                                                          |
| FR-24–FR-28; Scenarios 13–15   | T4 Replica routing; T6 unavailable dependencies, retry hints and quota/security failure modes.                                                                                 |
| FR-29–FR-31; Scenarios 7, 16   | T4 read-only representation/baseline; T6 repeat/reconnect and error/recovery checks.                                                                                           |
| FR-32–FR-34; Scenario 17       | T1 existing event wire/production guard repair; T4/T6 no event from summary; reuse existing hub-routing tests.                                                                 |
| BR-01–BR-09                    | T2 routing foundation; T4 full Organizer read; T5 Staff access; T6 failure behavior.                                                                                           |
| BR-10–BR-12                    | Existing event/reconnect contract and API manual checks in T1/T6; no client implementation task.                                                                               |
| Legacy corrections             | T1: F-6.2/F-10.5 event catalog; T2: F-6.1 FR-06/BR-03 and [server ADR-011](../../../docs/server/adrs/ADR-011-primary-replica-routing-strategy.md); T3: F-4.4 FR-08/Scenario 3. |

## 6. Risks and Mitigations

| Risk                                                                                                           | Impact | Mitigation                                                                                                                                                                                                                                                                               |
| :------------------------------------------------------------------------------------------------------------- | :----- | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Shared serializer changes affect events outside attendance.                                                    | High   | T1 checks all seven catalog event families and retains actual producer payload types; keep the change to timestamp serialization, not event fields/transports.                                                                                                                           |
| Annotation-only routing tests pass while SQL uses a connection selected before read-only flags.                | High   | T2 exercises the configured proxy/transaction/statement path with mocked JDBC dependencies and distinguishes business statements from initialization; regression-check both Primary mutation and Replica reporting paths.                                                                |
| Correcting routing exposes stale-read assumptions in another old capability.                                   | High   | The full suite and human legacy API gate are blocking. Repair the responsible regression before T4 or before any later task continues; do not force all reads onto Primary to conceal it.                                                                                                |
| Expanding a shared workshop DTO causes widespread constructor or mapping changes.                              | Medium | T3 uses an additive scalar facade query only when the approved timestamp fallback is needed; preserve existing DTO constructors and facade consumers.                                                                                                                                    |
| First summary slice exceeds the usual file-count guidance.                                                     | Medium | Human accepted the one approximately 16–18-file exception. T4's 18 files include complete behavior, migration/configuration and tests; do not split it into unfinished layers or use the exception for later tasks.                                                                      |
| Confirmed-count facade reuses a broader aggregate with an unused latest-update query.                          | Medium | Reuse the existing encapsulated, constant-read path initially. Record its real query count; avoid N+1 or loading rows to count. Measure before proposing a separate count-only optimization.                                                                                             |
| Primary context, Replica counts, preview and SSE reflect different times.                                      | Medium | Return counts as sampled and derive percentage from those returned counts. Keep live counters distinct at the consumer boundary; no freshness/version claim, artificial clamping, or synchronous cross-store snapshot.                                                                   |
| Concurrent index migration cannot run in a transaction and Flyway's transactional advisory lock can interfere. | High   | T4 uses the installed PostgreSQL parser's non-transactional detection and session advisory locking on a session-capable Primary connection. Keep old migrations unchanged and deploy the additive index before relying on the access path.                                               |
| Concurrent index build fails and leaves an invalid index or failed migration record.                           | High   | Inspect validity and migration state in the controlled manual environment. Do not hide it with a name-existence check, edit applied migrations, or blindly run repair/cleanup. Review a targeted recovery action; code rollback can retain a valid additive index without changing data. |
| Query with a parameterized status does not use the intended partial index under a generic prepared plan.       | Medium | The fixed preview may use the declared `VALID` predicate as a named SQL constant while binding caller-controlled workshop values. Review the query/index match; use a controlled operator query plan when available, never a live DB automated test.                                     |
| Availability translation masks programming errors or misses transaction acquisition failures.                  | Medium | T6 distinguishes datasource/security unavailability from unexpected errors and tests begin/callback failure boundaries; preserve generic `500` and existing positive retry hints.                                                                                                        |
| Manual API checking is mistaken for permission to add integration tests or perform destructive outage actions. | High   | Automated tests remain isolated units/static architecture checks. The human controls the environment, fixtures, migrations and outage experiments; the plan describes checks without executing them.                                                                                     |

## 7. References and Open Questions

**Project references:** Approved feature SPEC/API_CONTRACT; `docs/server/feature-delivery.md` §6.3; `docs/server/source-structure.md` module/read-workflow rules; `docs/server/database-schema.md` attendance index and routing requirements; [server ADR-006](../../../docs/server/adrs/ADR-006-caching-and-rate-limiting-storage.md)/[server ADR-011](../../../docs/server/adrs/ADR-011-primary-replica-routing-strategy.md)/[server ADR-025](../../../docs/server/adrs/ADR-025-real-time-event-streaming-via-server-sent-events-sse-and-redis-pubsub.md); existing F-6.1, F-6.2, F-4.4 and F-10.5 specifications/contracts and their recorded implementation risks.

**Technical references for migration/routing choices:**

- The installed Spring 7.0.7 sources for `AbstractPlatformTransactionManager`, `DataSourceTransactionManager`, and `LazyConnectionDataSourceProxy` establish begin/flag timing and delayed physical acquisition; this informs T2's unit regression rather than replacing it.
- A normal index build blocks table writes; concurrent creation avoids that write-blocking build mode and must run outside a transaction. [PostgreSQL CREATE INDEX](https://www.postgresql.org/docs/current/sql-createindex.html).
- Flyway automatically detects supported statements that cannot run transactionally; the installed PostgreSQL parser recognizes concurrent index commands. [Flyway migration transaction handling](https://documentation.red-gate.com/fd/migration-transaction-handling-273973399.html).
- Session advisory locking is required for this concurrent-index migration strategy. [Flyway PostgreSQL transactional lock setting](https://documentation.red-gate.com/flyway/reference/configuration/flyway-namespace/flyway-postgresql-namespace/flyway-postgresql-transactional-lock-setting), [Spring Boot Flyway property](https://docs.spring.io/spring-boot/appendix/application-properties/).

**Open questions:** None blocking the task breakdown. The human resolved the one baseline-sizing exception explicitly; business scope remains the approved spec. API fixtures, environment availability and a session-capable migration connection are execution prerequisites checked with the human during the relevant task, not permission to read secrets or run destructive infrastructure commands during planning.
