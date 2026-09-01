# Implementation Plan: Ingestion Reject Report

> **Spec:** `specs/ingestion-reject-report/SPEC.md`  
> **API Contract:** `specs/ingestion-reject-report/API_CONTRACT.md`  
> **Status:** APPROVED

## Overview

This plan builds the backend for Feature 9.2 in three vertical slices: first a reusable JWT RS256 security foundation in `shared/security` that all future authenticated endpoints will inherit; then the paginated ingestion job history endpoint for `ORGANIZER` users; then the reject-artifact pre-signed URL endpoint. No workers are involved — the artifacts are already in MinIO, written by Feature 9.1. The plan deliberately front-loads the security infrastructure because both business endpoints depend on it, and a defect there (wrong `401`/`403` behavior, broken JTI check) would invalidate every test built on top.

## Architecture Decisions for This Plan

- **T1 carries the shared security foundation** — No separate "infra setup" task is created. The JWT filter chain is owned by T1 because it is first observable capability that demands it (FR-01, FR-04, FR-05). T2 and T3 inherit it without additional security config changes.
- **T2 before T3** — T3's domain logic (artifact key selection) builds directly on T2's `IngestionJobReport` domain model and the `IngestionJobReportRepository` port. Implementing T2 first avoids duplicating that setup in T3.
- **Replica vs. primary routing handled per-task** — T2 uses the replica datasource; T3 uses the primary. Each task wires its own datasource routing annotation/qualifier so the routing is observable and tested in isolation.
- **No Flyway migration needed** — `ingestion_job_reports` already exists (written by Feature 9.1). This plan adds no new tables or indexes.
- **MinIO client bean** — The existing `shared/config` already wires a MinIO/S3 client (per current codebase state). T3's infrastructure adapter simply injects it; no new config bean is needed unless the `PRESIGN_TTL_MINUTES` property is missing from `application.yml`.

## Task List

### Phase 1: JWT RS256 Security Foundation

This phase establishes the security baseline required by every authenticated endpoint in this and all future features. It is the highest-risk task in the plan — a wrong security posture cascades across the entire backend — so it is built and verified independently before any business endpoint is written.

- [x] **T1 — Authenticated requests to `/api/v1/**` are rejected with 401/403 based on JWT validity and role; public endpoints (Swagger, Actuator, JWKS, Stripe webhook) remain accessible without a token**
  - **Size:** M
  - **Depends on:** —
  - **Layers touched:** Shared/Security (new), Application config (`application.yml` / `application-prod.yml`), Shared/Config (RSA key, already present)
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/shared/security/JwtAuthenticationFilter.java` _(new)_
    - `apps/backend/src/main/java/com/unihub/workshop/shared/security/JwtClaimsExtractor.java` _(new)_
    - `apps/backend/src/main/java/com/unihub/workshop/shared/security/JwtAuthenticatedPrincipal.java` _(new)_
    - `apps/backend/src/main/java/com/unihub/workshop/shared/security/RedisJtiValidator.java` _(new)_
    - `apps/backend/src/main/java/com/unihub/workshop/shared/security/SecurityConfig.java` _(new)_
    - `apps/backend/src/main/java/com/unihub/workshop/shared/security/SecurityProperties.java` _(new)_
    - `apps/backend/src/main/resources/application.yml` _(updated — add `app.security.presign-ttl-minutes: 15` and public path whitelist config)_
    - `apps/backend/src/test/java/com/unihub/workshop/shared/security/SecurityConfigTest.java` _(new)_
  - **Acceptance Criteria:**
    - A `GET /api/v1/admin/ingestion-jobs` request with **no `Authorization` header** returns `401 Unauthorized` with `Content-Type: application/problem+json`. _(Traces to: FR-01, Scenario 8)_
    - A `GET /api/v1/admin/ingestion-jobs` request with a **valid JWT where `role = STUDENT`** returns `403 Forbidden`. _(Traces to: FR-02, Scenario 9)_
    - A `GET /api/v1/admin/ingestion-jobs` request with a **valid JWT where `role = ORGANIZER`** passes the filter chain (returns any non-401/403 status — even 404 is acceptable at this stage before T2 is wired). _(Traces to: FR-01, FR-02)_
    - A `GET /api/v1/admin/ingestion-jobs` request with a **JWT whose `jti` is present in the Redis blacklist** returns `503 Service Unavailable` when Redis is unavailable (fail-closed). _(Traces to: FR-01, BR-08)_
    - A `GET /swagger-ui/index.html` request with **no token** returns `200 OK` (not blocked by security). _(Traces to: FR-05, BR-10)_
    - A `GET /actuator/health` request with **no token** returns `200 OK`. _(Traces to: FR-05, BR-10)_
    - After T1, adding a new `@RestController` mapping under `/api/v1/**` requires **zero changes** to `SecurityConfig.java` to be automatically protected. _(Traces to: FR-05)_
    - `ModulithArchitectureTest` passes. _(Definition of Done)_

#### Checkpoint: Phase 1 — Security Foundation

- [x] T1 acceptance criteria all pass end-to-end (filter behavior verified by `SecurityConfigTest`).
- [x] No existing tests broken; `ModulithArchitectureTest` passes.
- [x] Build is clean (`./gradlew test`).

### Phase 2: Business Capabilities

With the security foundation in place, both business endpoints are implemented in dependency order. T2 establishes the domain model and persistence layer shared by both; T3 builds directly on top of T2's domain types.

- [x] **T2 — An authenticated ORGANIZER can retrieve their organization's paginated ingestion job history list, org-scoped exclusively to their JWT, with correct `artifactAvailable` flag and cursor-based pagination**
  - **Size:** M
  - **Depends on:** T1
  - **Layers touched:** Domain (new — `IngestionJobReport` read model), Application (new — use case + outbound port), Infrastructure (new — JDBC/JPA repository, replica routing), Presentation (new — controller + DTOs)
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/domain/model/IngestionJobReport.java` _(new)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/domain/model/IngestionJobOutcome.java` _(new — enum: `COMPLETED`, `FILE_REJECTED`)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/ports/out/IngestionJobReportRepository.java` _(new — outbound port interface)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/usecases/ListIngestionJobsUseCase.java` _(new)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/infrastructure/persistence/IngestionJobReportJpaRepository.java` _(new — Spring Data JPA, replica datasource)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/infrastructure/persistence/IngestionJobReportJpaEntity.java` _(new — `@Entity` mapping to `ingestion_job_reports`)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/infrastructure/persistence/IngestionJobReportRepositoryAdapter.java` _(new — implements port, applies keyset cursor)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/presentation/controllers/IngestionJobController.java` _(new)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/presentation/dto/IngestionJobListResponse.java` _(new)_
    - `apps/backend/src/test/java/com/unihub/workshop/workshops/application/usecases/ListIngestionJobsUseCaseTest.java` _(new)_
    - `apps/backend/src/test/java/com/unihub/workshop/workshops/infrastructure/persistence/IngestionJobReportRepositoryAdapterTest.java` _(new)_
    - `apps/backend/src/test/java/com/unihub/workshop/workshops/presentation/controllers/IngestionJobControllerTest.java` _(new — MockMvc, T1 security wired)_
  - **Acceptance Criteria:**
    - `GET /api/v1/admin/ingestion-jobs` by an ORGANIZER whose org has 3 reports returns `200 OK` with exactly 3 items, all belonging to that org, and zero items from any other org. _(Traces to: FR-06, Scenario 1)_
    - A job with `outcome = FILE_REJECTED` and `file_rejection_source_key` set returns `artifactAvailable = true`; a job with `outcome = COMPLETED`, `reject_artifact_key` set, and `rejectedRowCount > 0` returns `artifactAvailable = true`; a clean completed job returns `artifactAvailable = false`. _(Traces to: FR-09, Scenario 3)_
    - `GET /api/v1/admin/ingestion-jobs?size=3` with 5 total records returns 3 items, `cursor.hasMore = true`, and a non-null `cursor.nextCursor`; following with `?size=3&cursor={nextCursor}` returns the remaining 2 items and `cursor.hasMore = false`. _(Traces to: FR-07, Scenario 2)_
    - `GET /api/v1/admin/ingestion-jobs?size=0` returns `400 Bad Request`. _(Traces to: API_CONTRACT.md §GET list, Validation Rules)_
    - `GET /api/v1/admin/ingestion-jobs?size=500` returns results clamped to 100 items. _(Traces to: FR-07, API_CONTRACT.md §GET list)_
    - The repository query uses the composite index `(organization_id, completed_at DESC, id DESC)` for keyset pagination (verifiable via query inspection in tests). _(Traces to: FR-10, BR-09)_
    - `ModulithArchitectureTest` passes. _(Definition of Done)_

- [x] **T3 — An authenticated ORGANIZER can obtain a pre-signed MinIO download URL for an artifact-bearing report belonging to their org; non-owned, non-existent, or artifact-free reports return 404**
  - **Size:** M
  - **Depends on:** T2
  - **Layers touched:** Domain (new rule — artifact key selection logic added to `IngestionJobReport`), Application (new use case + outbound port for MinIO), Infrastructure (new MinIO presign adapter, primary datasource lookup), Presentation (new controller method + DTOs)
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/ports/out/ArtifactPresignPort.java` _(new — outbound port for URL generation)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/usecases/GetRejectArtifactUrlUseCase.java` _(new)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/infrastructure/storage/MinioArtifactPresignAdapter.java` _(new — implements `ArtifactPresignPort` using MinIO SDK)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/presentation/dto/RejectArtifactUrlResponse.java` _(new)_
    - `apps/backend/src/main/resources/application.yml` _(updated if `app.security.presign-ttl-minutes` was not added in T1)_
    - `apps/backend/src/test/java/com/unihub/workshop/workshops/application/usecases/GetRejectArtifactUrlUseCaseTest.java` _(new)_
    - `apps/backend/src/test/java/com/unihub/workshop/workshops/infrastructure/storage/MinioArtifactPresignAdapterTest.java` _(new)_
    - `apps/backend/src/test/java/com/unihub/workshop/workshops/presentation/controllers/IngestionJobControllerRejectArtifactTest.java` _(new — MockMvc)_
  - **Acceptance Criteria:**
    - `GET /api/v1/admin/ingestion-jobs/{jobId}/reject-artifact` where the report has `outcome = FILE_REJECTED` and `file_rejection_source_key` set returns `200 OK` with a non-null `downloadUrl` referencing the `file_rejection_source_key`. _(Traces to: FR-13, FR-14, Scenario 4)_
    - `GET /api/v1/admin/ingestion-jobs/{jobId}/reject-artifact` where the report has `outcome = COMPLETED`, `reject_artifact_key` set, and `rejectedRowCount > 0` returns `200 OK` with `artifactType = ROW_REPORT`. _(Traces to: FR-13, Scenario 5)_
    - `GET /api/v1/admin/ingestion-jobs/{jobId}/reject-artifact` where the report is a clean run (`rejectedRowCount = 0`, `reject_artifact_key` is null) returns `404 Not Found`. _(Traces to: FR-12, Scenario 6)_
    - `GET /api/v1/admin/ingestion-jobs/{jobId}/reject-artifact` where the `jobId` belongs to a different organization returns `404 Not Found`, and no MinIO SDK call is made. _(Traces to: FR-11, Scenario 7)_
    - `GET /api/v1/admin/ingestion-jobs/{not-a-uuid}/reject-artifact` returns `400 Bad Request`. _(Traces to: API_CONTRACT.md §GET reject-artifact, Validation Rules)_
    - The `downloadUrl` is generated using the artifact key retrieved from the database record only — no client-supplied key is accepted. _(Traces to: FR-14, BR-05, Scenario 11)_
    - The `expiresAt` field in the response equals `now + configured TTL` (default 15 min). _(Traces to: FR-14, BR-06)_
    - The database lookup for the pre-signed URL endpoint routes to the **primary** datasource. _(Traces to: FR-15)_
    - When the MinIO SDK throws an exception during presigning, the endpoint returns `500 Internal Server Error`. _(Traces to: API_CONTRACT.md §error table)_
    - `ModulithArchitectureTest` passes. _(Definition of Done)_

#### Checkpoint: Feature Complete

- [x] All tasks (T1, T2, T3) are checked off.
- [x] Every acceptance criterion across all tasks has been verified end-to-end (not just compiled).
- [x] Full test suite passes: `./gradlew test` is clean, including `ModulithArchitectureTest`.
- [x] `GET /api/v1/admin/ingestion-jobs` — happy path, 401, 403, pagination, and `artifactAvailable` flag all verified.
- [x] `GET /api/v1/admin/ingestion-jobs/{jobId}/reject-artifact` — both artifact types, 404 (clean run), 404 (wrong org), 400 (bad UUID), 500 (MinIO error) all verified.
- [x] Public endpoints (Swagger, Actuator) accessible without a token.
- [x] Ready for `/review ingestion-reject-report`.

## Risks and Mitigations

| Risk                                                                                                                                                                                                                                                                                                                | Impact                                                                                     | Mitigation                                                                                                                                                                                                                        |
| :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | :----------------------------------------------------------------------------------------- | :-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Spring Security 6 + Spring Boot 4 `SecurityFilterChain` configuration semantics may differ from older project examples.** Spring Boot 4 aligns with Spring Security 6's lambda DSL; legacy XML config or old `WebSecurityConfigurerAdapter` patterns do not apply.                                                | High — wrong config could silently pass all requests or block public endpoints             | Write `SecurityConfigTest` with specific `MockMvc` cases for `401`, `403`, and public path access before wiring any business endpoint. Red-Green-Refactor confirms each case explicitly.                                          |
| **Redis JTI blacklist fail-closed (503) under test.** Unit tests for the JWT filter must simulate Redis unavailability without actually bringing Redis down.                                                                                                                                                        | Medium — if not tested, the fail-closed guarantee is untested and could regress silently   | Mock the `RedisJtiValidator` to throw a connection exception; assert `503` in `SecurityConfigTest`.                                                                                                                               |
| **Keyset cursor encoding/decoding.** The cursor must encode `(completed_at, id)` in a format that is stable, non-guessable, and handles timezone serialization correctly (all timestamps must be UTC). A bug here would produce incorrect pagination or allow cursor injection.                                     | Medium — pagination bugs are subtle and easy to miss in code review                        | Use Base64-encoded JSON `{"completedAt":"2026-..Z","id":"..."}` for the cursor; validate the decoded value against the expected composite index predicate in `IngestionJobReportRepositoryAdapterTest`.                           |
| **MinIO SDK presign TTL behavior.** The SDK's `getPresignedObjectUrl` TTL is expressed in seconds in some SDK versions and minutes in others. A unit off here produces URLs that expire immediately or never.                                                                                                       | Medium — silent production bug; tests would not catch it unless TTL is explicitly asserted | Assert `expiresAt` in the controller test is within ±5 seconds of `now + configured TTL`.                                                                                                                                         |
| **Primary vs replica datasource routing in a single Spring context.** `TransactionAwareRoutingDataSource` already exists in `shared/config/datasource`. Both T2 (replica) and T3 (primary) must use the correct qualifier. A misconfigured routing annotation would route T3 reads to the replica, violating FR-15. | Medium — data freshness bug, not easily caught by unit tests                               | Use `@ReadOnlyProperty` / routing annotation on T2's repository; use the default (primary) on T3's lookup. Add a datasource-routing assertion in the adapter test using a test double that records which datasource was selected. |

## Open Questions

None.
