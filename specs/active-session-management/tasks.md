# Implementation Plan: Active Session Management

> **Spec:** `specs/active-session-management/SPEC.md`  
> **API Contract:** `specs/active-session-management/API_CONTRACT.md`  
> **Status:** APPROVED

## Overview

This plan delivers the complete active session management capability (Domain 1: Identity & Access Management, Feature 1.7) across four focused vertical slices: (1) session token binding and distributed session tracking during login and token refresh, (2) active session inspection with keyset cursor-based pagination and caller session detection (`GET /api/v1/auth/sessions`), (3) single session revocation with immediate fail-closed distributed blacklist enforcement (`DELETE /api/v1/auth/sessions/{id}`), and (4) bulk revocation of all remote sessions while preserving the caller's active session uninterrupted (`DELETE /api/v1/auth/sessions`).

## Architecture Decisions for This Plan

- **Distributed Stateless Consistency via `sid` & Redis Mapping (T1)** — To satisfy ADR-005 and ensure remote session revocation takes effect immediately across $\ge$ 2 backend instances without sticky sessions or distributed DB polling, access tokens are bound to their session identifier (`sid` claim matching `refresh_tokens.id`), and Redis stores a temporary mapping `session:jti:{sessionId} -> {jti}` with TTL equal to the access token lifetime.
- **Keyset Cursor Pagination on Primary DB (T2)** — Active session listing uses keyset cursor pagination based on `(created_at DESC, id DESC)` over `refresh_tokens`. In accordance with ADR-015 and `docs/database-schema.md §4`, all session queries and mutations route strictly to the Primary PostgreSQL database to avoid replication lag anomalies.
- **Fail-Closed Security Posture on Revocation (T3 & T4)** — In accordance with ADR-010, the blacklist check and write operations are security-critical controls. In both single and bulk revocation, Redis blacklist operations are performed before database state updates; if Redis is unreachable, the operation is aborted, no database mutations occur, and HTTP `503 Service Unavailable` with `Retry-After` header (configurable via `app.http.retry-after-seconds`, default 30s) is returned.
- **Current Session Immunity during Bulk Revocation (T4)** — Bulk revocation extracts the caller's `sid` from the JWT principal and executes an atomic query updating only remote sessions (`WHERE user_id = :userId AND id != :currentSessionId AND revoked_at IS NULL`). The caller's session row and active access token remain active and unrevoked.
- **Clean Architecture & Module Encapsulation** — All new classes reside within `iam` and `shared/security`. Intra-module layering strictly follows `domain -> application (ports/usecases) -> infrastructure / presentation`. Cross-module communication adheres to Spring Modulith boundaries verified by `ModulithArchitectureTest`.

## Task List

### Phase 1: Session Identification & Distributed State Tracking

- [x] **T1 — Session token binding: embed mandatory `sid` claim in all access tokens and track active token JTI in Redis upon login, token refresh, and temporary password change**
  - **Size:** M
  - **Depends on:** —
  - **Layers touched:** Shared Security, Application (Ports/Use Cases), Infrastructure (Redis)
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/shared/security/JwtTokenIssuer.java`
    - `apps/backend/src/main/java/com/unihub/workshop/shared/security/JwtAuthenticatedPrincipal.java`
    - `apps/backend/src/main/java/com/unihub/workshop/shared/security/JwtClaimsExtractor.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/ports/out/SessionTrackerPort.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/infrastructure/security/RedisSessionTrackerAdapter.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/usecases/LoginUseCaseImpl.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/usecases/RefreshTokenUseCaseImpl.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/usecases/ChangeTemporaryPasswordUseCaseImpl.java`
    - `apps/backend/src/test/java/com/unihub/workshop/shared/security/JwtTokenIssuerTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/shared/security/JwtClaimsExtractorTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/iam/infrastructure/security/RedisSessionTrackerAdapterTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/iam/application/usecases/LoginUseCaseImplTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/iam/application/usecases/RefreshTokenUseCaseImplTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/iam/application/usecases/ChangeTemporaryPasswordUseCaseImplTest.java`
  - **Acceptance Criteria:**
    - `POST /api/v1/auth/login` returns an RS256-signed JWT access token containing a mandatory `sid` claim whose value exactly equals the `id` of the newly created `refresh_tokens` record.
    - `POST /api/v1/auth/refresh` returns an RS256-signed JWT access token containing a mandatory `sid` claim whose value exactly equals the `id` of the newly rotated `refresh_tokens` record.
    - `POST /api/v1/auth/password/temporary` returns an RS256-signed JWT access token containing a mandatory `sid` claim whose value exactly equals the `id` of the newly created `refresh_tokens` record.
    - Upon successful login, token refresh, and temporary password change, Redis stores the key `session:jti:{sessionId}` pointing to the token's `jti` with TTL equal to the access token duration.
    - `JwtClaimsExtractor` extracts the mandatory `sid` claim from incoming JWTs into `JwtAuthenticatedPrincipal.sid()`, throwing an exception if `sid` is missing or blank.
    - Unit tests verify `sid` emission in `JwtTokenIssuer`, session tracking in `LoginUseCaseImpl`, `RefreshTokenUseCaseImpl`, and `ChangeTemporaryPasswordUseCaseImpl`, and extractor behavior.
    - _(Traces to: FR-02, FR-11, FR-12, FR-14, Preconditions, ADR-005)_

#### Checkpoint: Phase 1

- [x] All login, refresh, and password exchange flows issue mandatory `sid`-bound JWTs and track active session JTIs in Redis. Unit tests pass and `ModulithArchitectureTest` passes.

### Phase 2: Active Session Inspection

- [x] **T2 — Active session query: list active sessions with keyset cursor pagination and current session indicator (`GET /api/v1/auth/sessions`)**
  - **Size:** M
  - **Depends on:** T1
  - **Layers touched:** Domain, Application (Ports/Use Cases), Infrastructure (Persistence), Presentation (Controller/DTOs)
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/iam/domain/model/ActiveSession.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/infrastructure/persistence/RefreshTokenJpaEntity.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/infrastructure/persistence/RefreshTokenJpaRepository.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/ports/out/RefreshTokenRepository.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/infrastructure/persistence/RefreshTokenRepositoryAdapter.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/ports/in/GetActiveSessionsUseCase.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/usecases/GetActiveSessionsUseCaseImpl.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/presentation/dto/SessionResponse.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/presentation/dto/SessionCursor.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/presentation/controllers/AuthController.java`
    - `apps/backend/src/test/java/com/unihub/workshop/iam/application/usecases/GetActiveSessionsUseCaseImplTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/iam/presentation/controllers/AuthControllerGetSessionsTest.java`
  - **Acceptance Criteria:**
    - `GET /api/v1/auth/sessions` with a valid Bearer token returns `200 OK` wrapped in `ApiResponse<List<SessionResponse>>` with `pagination` metadata (`nextCursor`, `hasNext`).
    - The returned sessions include only non-expired (`expires_at > NOW()`) and non-revoked (`revoked_at IS NULL`) sessions belonging exclusively to the authenticated user (`user_id = currentUserId`), ordered descending by `created_at DESC, id DESC`.
    - Exactly one session item has `isCurrentSession = true` matching the caller's JWT `sid` claim; all other sessions have `isCurrentSession = false`. If the caller's token has no matching active session in DB, all returned sessions have `isCurrentSession = false`.
    - Keyset cursor pagination correctly decodes Base64 JSON cursor (`createdAt`, `id`), limits results by `size` (default 20, capped at 100), and emits `nextCursor` and `hasNext = true` when additional active sessions exist.
    - Unauthenticated requests return `401 Unauthorized` (`urn:unihub:error:unauthorized`).
    - Database query executes strictly against the Primary database via `@Transactional(readOnly = true)`.
    - _(Traces to: FR-01, FR-02, FR-03, FR-04, FR-05, FR-06, FR-20, Scenarios 1, 2, 7, API Contract Section 3)_

#### Checkpoint: Phase 2

- [x] `GET /api/v1/auth/sessions` returns active sessions with cursor pagination and accurate `isCurrentSession` flags. Unit tests and `ModulithArchitectureTest` pass.

### Phase 3: Targeted Session Revocation

- [x] **T3 — Single session revocation: terminate a specific session remotely or logout current session with fail-closed Redis invalidation (`DELETE /api/v1/auth/sessions/{id}`)**
  - **Size:** M
  - **Depends on:** T1, T2
  - **Layers touched:** Application (Ports/Use Cases), Infrastructure (Persistence/Redis), Presentation (Controller)
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/ports/in/RevokeSessionUseCase.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/usecases/RevokeSessionUseCaseImpl.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/infrastructure/persistence/RefreshTokenJpaRepository.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/ports/out/RefreshTokenRepository.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/infrastructure/persistence/RefreshTokenRepositoryAdapter.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/presentation/controllers/AuthController.java`
    - `apps/backend/src/test/java/com/unihub/workshop/iam/application/usecases/RevokeSessionUseCaseImplTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/iam/presentation/controllers/AuthControllerRevokeSessionTest.java`
  - **Acceptance Criteria:**
    - `DELETE /api/v1/auth/sessions/{id}` for an active session belonging to the caller returns `204 No Content` with an empty body.
    - In PostgreSQL primary DB, the session record is marked with `revoked_at = NOW()`.
    - If `{id}` equals the caller's current session `sid`: The caller's JWT `jti` is added to Redis blacklist (`jti:blacklisted:{jti}`) with remaining TTL (`exp - NOW()`), immediately invalidating the caller.
    - If `{id}` is a remote session (`id != sid`): The remote session's active `jti` is retrieved from Redis `session:jti:{sessionId}`, blacklisted in Redis (`jti:blacklisted:{jti}`) for its remaining lifetime, and the session mapping is deleted.
    - If the session does not exist, belongs to another user, or has already been revoked: Returns `404 Not Found` (`urn:unihub:error:not-found`).
    - If `{id}` path parameter is not a well-formed UUID: Returns `400 Bad Request` (`urn:unihub:error:bad-request`).
    - If Redis is unreachable during revocation: Aborts immediately, performs no DB mutation, and returns `503 Service Unavailable` with `Retry-After` header (configurable via `app.http.retry-after-seconds`, default 30s) (fail-closed per ADR-010).
    - _(Traces to: FR-07, FR-08, FR-09, FR-10, FR-11, FR-12, FR-18, FR-19, FR-20, BR-01, BR-03, BR-04, BR-05, BR-06, Scenarios 3, 4, 5, 8, API Contract Section 3)_

#### Checkpoint: Phase 3

- [x] `DELETE /api/v1/auth/sessions/{id}` successfully revokes current and remote sessions with immediate distributed invalidation and fail-closed protection. Unit tests and `ModulithArchitectureTest` pass.

### Phase 4: Bulk Remote Session Revocation

- [ ] **T4 — Bulk session revocation: terminate all other active sessions while preserving caller's current session (`DELETE /api/v1/auth/sessions`)**
  - **Size:** M
  - **Depends on:** T1, T2, T3
  - **Layers touched:** Application (Ports/Use Cases), Infrastructure (Persistence/Redis), Presentation (Controller)
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/ports/in/RevokeAllOtherSessionsUseCase.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/usecases/RevokeAllOtherSessionsUseCaseImpl.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/infrastructure/persistence/RefreshTokenJpaRepository.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/ports/out/RefreshTokenRepository.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/infrastructure/persistence/RefreshTokenRepositoryAdapter.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/presentation/controllers/AuthController.java`
    - `apps/backend/src/test/java/com/unihub/workshop/iam/application/usecases/RevokeAllOtherSessionsUseCaseImplTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/iam/presentation/controllers/AuthControllerRevokeAllOtherSessionsTest.java`
  - **Acceptance Criteria:**
    - `DELETE /api/v1/auth/sessions` with a valid Bearer token returns `204 No Content`.
    - If the caller's JWT does not contain a resolvable `sid` claim: Returns `400 Bad Request` (`urn:unihub:error:bad-request`).
    - Atomically updates all active sessions for the user where `id != sid` and `revoked_at IS NULL` by setting `revoked_at = NOW()` in the PostgreSQL primary database.
    - Resolves and blacklists all active `jti`s for the affected remote sessions in Redis for their remaining validity durations, and deletes their session mappings.
    - The caller's current session record (`id == sid`) and active access token remain untouched, active, and valid for subsequent requests.
    - If the caller has zero other active sessions: Returns `204 No Content` without modifying the current session.
    - If Redis is unreachable during revocation: Aborts immediately, performs no DB mutation, and returns `503 Service Unavailable` with `Retry-After` header (configurable via `app.http.retry-after-seconds`, default 30s) (fail-closed per ADR-010).
    - _(Traces to: FR-13, FR-14, FR-15, FR-16, FR-17, FR-18, FR-19, FR-20, BR-01, BR-02, BR-03, BR-04, BR-05, Scenarios 6, 8, API Contract Section 3)_

#### Checkpoint: Feature Complete

- [ ] All tasks T1–T4 are checked off.
- [ ] Every acceptance criterion across all tasks has been verified by mock-based unit tests.
- [ ] Full unit test suite passes: `./gradlew :apps:backend:test`.
- [ ] `ModulithArchitectureTest` passes with zero architectural violations.
- [ ] Ready for `/review active-session-management`.

## Risks and Mitigations

| Risk                                                                                                                                | Impact | Mitigation                                                                                                                                                                                                                                                                                               |
| :---------------------------------------------------------------------------------------------------------------------------------- | :----- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Multi-node distributed token invalidation latency: remote instances might accept a revoked access token if only DB is updated.      | High   | T1 establishes `session:jti:{sessionId}` mapping in Redis at token issuance. T3 and T4 look up and write the active token `jti` directly to Redis blacklist (`jti:blacklisted:{jti}`) before updating PostgreSQL. Any backend instance checking Redis blacklist immediately rejects subsequent requests. |
| Redis fail-closed ordering: partial failures could leave DB updated while Redis blacklist is incomplete, allowing lingering access. | High   | All revocation use cases (`RevokeSessionUseCaseImpl`, `RevokeAllOtherSessionsUseCaseImpl`) execute Redis blacklist writes first. If Redis fails or throws, execution aborts with `503 Service Unavailable` and the DB transaction never commits, ensuring zero partial revocation state.                 |
| Accidental current session termination during bulk revocation: caller could be locked out of their own account.                     | High   | Bulk revocation explicitly requires `sid` claim from caller's token; if missing, it rejects with `400 Bad Request`. The DB query strictly enforces `WHERE user_id = :userId AND id != :currentSessionId`, making it impossible to revoke the caller's session row.                                       |
| Keyset cursor pagination ordering inconsistency if timestamps match.                                                                | Medium | Keyset cursor utilizes composite sorting `created_at DESC, id DESC` with tie-breaking on UUID `id`. The repository query checks `(createdAt < :cursorCreatedAt OR (createdAt = :cursorCreatedAt AND id < :cursorId))` ensuring deterministic ordering without skipping or repeating items.               |
| Primary vs Replica DB lag on active session inspection.                                                                             | Medium | All read queries in `GetActiveSessionsUseCaseImpl` and writes in revocation use cases are explicitly routed to the Primary PostgreSQL instance via `@Transactional(readOnly = true)` and `@Transactional` respectively (ADR-015).                                                                        |

## Open Questions

None. All architectural decisions, API contracts, and business rules were resolved and approved in `SPEC.md` and `API_CONTRACT.md`.
