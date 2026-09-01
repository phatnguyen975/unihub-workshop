# Implementation Plan: Authentication and Session Management

> **Spec:** `specs/authentication-and-session-management/SPEC.md`  
> **API Contract:** `specs/authentication-and-session-management/API_CONTRACT.md`  
> **Status:** APPROVED

## Overview

This plan delivers the four caller-observable capabilities of feature 1.2: (1) the JWKS public endpoint so downstream services can verify JWT signatures; (2) the login endpoint — the core capability that validates credentials, enforces security gates, issues tokens, and seeds a refresh token row; (3) the token refresh endpoint — the rotation capability that atomically consumes a refresh token and issues a new token pair; and (4) the logout endpoint — which writes the current access token's JTI to the Redis blacklist and revokes the refresh token row. Login is built first because refresh and logout both require a valid token pair to test end-to-end. JWKS is built first among all because it shares the RSA key infrastructure and can be verified as a standalone endpoint with zero dependencies on the auth flow.

## Architecture Decisions for This Plan

- **T1 (JWKS) before T2 (Login)** — JWKS is a pure read of the RSA public key already loaded at startup; it has no dependency on any login infrastructure and can be verified immediately. Placing it first lets us confirm the key-loading wiring works before building token signing in T2.
- **JWT signing infrastructure placed in T2** — The JWT access token signer (`JwtTokenIssuer` or equivalent) is first needed by login (T2) and is also reused by token refresh (T3). Per the skill's _Handling Shared Foundations_ guidance, it is built as part of the first capability that needs it (T2), not as a separate infrastructure-only task.
- **T3 (Refresh) and T4 (Logout) both depend on T2** — Their acceptance criteria require a real access token and refresh token issued by the login endpoint to test end-to-end. They could run in parallel, but T4 (logout) also benefits from T3 being present because the test scenario "logout then retry with the old refresh token" is richer after T3 exists.
- **`UserJpaEntity` and `RefreshTokenJpaEntity` introduced in T2** — Both are needed by T2 (login reads `users`, writes `refresh_tokens`) and reused by T3 and T4. Building them in T2 means T3 and T4 find the persistence layer ready.
- **Micrometer counters placed in each task's own slice** — `auth.login.attempts` in T2, `auth.token.refresh` in T3. Observability is part of each vertical slice, not a separate final task.
- **No Flyway migration needed** — `users` and `refresh_tokens` tables exist from `V1__init_schema.sql` (feature 10.4). This plan adds JPA mappings only, no new DDL.

## Task List

### Phase 1: Public Key Infrastructure

- [ ] **T1 — JWKS endpoint: expose RSA public key for downstream JWT verification**
  - **Size:** S
  - **Depends on:** — (RSA key pair already loaded by `infrastructure-bootstrap-and-seed-data`)
  - **Layers touched:** Shared/Security (presentation), Application/Config
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/shared/security/JwksController.java`
    - `apps/backend/src/main/java/com/unihub/workshop/shared/security/JwksResponse.java`
    - `apps/backend/src/test/java/com/unihub/workshop/shared/security/JwksControllerTest.java`
  - **Acceptance Criteria:**
    - `GET /.well-known/jwks.json` without an `Authorization` header returns `200 OK` with `Content-Type: application/json`.
    - Response body is a raw JWK Set object (not wrapped in the standard success envelope) with a `keys` array containing exactly one entry.
    - The key entry has `kty = "RSA"`, `use = "sig"`, `alg = "RS256"`, and non-empty `n` and `e` fields.
    - No private key material (`d`, `p`, `q`, `dp`, `dq`, `qi`) appears anywhere in the response.
    - _(Traces to: FR-12, NFR-11, Scenario 13)_

#### Checkpoint: Phase 1

- [ ] `GET /.well-known/jwks.json` returns a well-formed JWK Set with the correct RSA public key, verified by a unit test against the key loaded from `classpath:security/public.pem`. Build is clean. `ModulithArchitectureTest` passes.

### Phase 2: Core Authentication Flow (Login)

- [ ] **T2 — Login: validate credentials, enforce security gates, and issue access + refresh token pair**
  - **Size:** M
  - **Depends on:** T1 (JWT signing infrastructure introduced here reuses the RSA key confirmed in T1)
  - **Layers touched:** Domain, Application, Infrastructure/Persistence, Presentation
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/iam/domain/model/UserRole.java` _(enum: STUDENT, ORGANIZER, STAFF)_
    - `apps/backend/src/main/java/com/unihub/workshop/iam/domain/exceptions/InvalidCredentialsException.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/domain/exceptions/AccountDisabledException.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/domain/exceptions/TemporaryPasswordException.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/ports/in/LoginUseCase.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/ports/out/UserRepository.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/ports/out/RefreshTokenRepository.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/usecases/LoginUseCaseImpl.java`
    - `apps/backend/src/main/java/com/unihub/workshop/shared/security/JwtTokenIssuer.java` _(signing service — shared foundation for T2 & T3)_
    - `apps/backend/src/main/java/com/unihub/workshop/iam/infrastructure/persistence/UserJpaEntity.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/infrastructure/persistence/UserJpaRepository.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/infrastructure/persistence/RefreshTokenJpaEntity.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/infrastructure/persistence/RefreshTokenJpaRepository.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/infrastructure/persistence/UserRepositoryAdapter.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/infrastructure/persistence/RefreshTokenRepositoryAdapter.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/presentation/controllers/AuthController.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/presentation/dto/LoginRequest.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/presentation/dto/LoginResponse.java`
    - `apps/backend/src/main/resources/application.yml` _(add `app.security.access-token-ttl`, `app.security.refresh-token-ttl`; add `/api/v1/auth/login` to `public-paths`)_
    - `apps/backend/src/test/java/com/unihub/workshop/iam/application/usecases/LoginUseCaseImplTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/iam/presentation/controllers/AuthControllerLoginTest.java`
  - **Acceptance Criteria:**
    - `POST /api/v1/auth/login` with valid email + correct password for an active, non-temporary-password account returns `200 OK` with a `data` object containing non-empty `accessToken`, `refreshToken`, and `tokenType = "Bearer"`, `expiresIn` matching the configured TTL.
    - The issued `accessToken` is a valid RS256-signed JWT with claims `sub`, `role`, `org`, `jti`, `iat`, `exp` — and `role` contains no `ROLE_` prefix.
    - A new `refresh_tokens` row is created with `revoked_at = NULL`, `token_hash = SHA-256(refreshToken)`, and the raw token is **not** stored anywhere.
    - `users.last_login_at` is updated to approximately `NOW()` on the primary DB.
    - `POST /api/v1/auth/login` with unknown email or wrong password returns `401 Unauthorized` with `type = "urn:unihub:error:authentication"` — same response shape for both cases (anti-enumeration). Response time for unknown-email case is comparable to wrong-password case (dummy Argon2 verification executed).
    - `POST /api/v1/auth/login` with correct credentials for an account with `is_active = FALSE` returns `403 Forbidden` with `type = "urn:unihub:error:account-disabled"`.
    - `POST /api/v1/auth/login` with correct credentials for an account with `is_temporary_password = TRUE` returns `403 Forbidden` with `type = "urn:unihub:error:temporary-password"`, and no `refresh_tokens` row is created.
    - Micrometer counter `auth.login.attempts` is emitted with `outcome=success` on success and `outcome=failure,reason=<invalid_credentials|account_disabled|temporary_password>` on each failure path.
    - _(Traces to: FR-01 through FR-06, BR-01 through BR-11, Scenarios 1, 3, 4, 5, 6)_

#### Checkpoint: Phase 2

- [ ] `POST /api/v1/auth/login` is fully functional end-to-end for all three actors (STUDENT, ORGANIZER, STAFF) and all three rejection gates (wrong credentials, inactive, temporary password). All unit tests pass. `ModulithArchitectureTest` passes. Build is clean.

### Phase 3: Session Continuation (Token Refresh)

- [ ] **T3 — Token refresh: atomically rotate refresh token and issue a new access token**
  - **Size:** M
  - **Depends on:** T2 (requires a valid refresh token issued by login to test end-to-end)
  - **Layers touched:** Application, Infrastructure/Persistence, Presentation
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/ports/in/RefreshTokenUseCase.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/usecases/RefreshTokenUseCaseImpl.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/presentation/dto/RefreshTokenRequest.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/presentation/dto/RefreshTokenResponse.java`
    - `apps/backend/src/main/resources/application.yml` _(add `/api/v1/auth/refresh` to `public-paths`)_
    - `apps/backend/src/test/java/com/unihub/workshop/iam/application/usecases/RefreshTokenUseCaseImplTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/iam/presentation/controllers/AuthControllerRefreshTest.java`
  - **Acceptance Criteria:**
    - `POST /api/v1/auth/refresh` with a valid, non-expired, non-revoked refresh token returns `200 OK` with a new `accessToken` (different `jti` from original) and a new `refreshToken` (different raw value from original).
    - After a successful refresh, the old `refresh_tokens` row has `revoked_at IS NOT NULL`, and a new row exists with the new `token_hash` and `revoked_at = NULL`.
    - `POST /api/v1/auth/refresh` with a revoked token (`revoked_at IS NOT NULL`) returns `401 Unauthorized` with `type = "urn:unihub:error:authentication"` — no new tokens issued.
    - `POST /api/v1/auth/refresh` with an expired token (`expires_at < NOW()`) returns `401 Unauthorized` with `type = "urn:unihub:error:authentication"`.
    - `POST /api/v1/auth/refresh` with an unknown token (no matching `token_hash`) returns `401 Unauthorized`.
    - Concurrent calls with the same refresh token: exactly one returns `200 OK`; the other returns `401 Unauthorized` — only one non-revoked `refresh_tokens` row exists after both calls complete.
    - Micrometer counter `auth.token.refresh` is emitted with `outcome=success` or `outcome=failure`.
    - _(Traces to: FR-08, FR-09, BR-05, NFR-06, Scenarios 7, 8, 9, 15)_

#### Checkpoint: Phase 3

- [ ] `POST /api/v1/auth/refresh` performs correct token rotation under both normal and concurrent conditions. All unit tests pass. `ModulithArchitectureTest` passes. Build is clean.

### Phase 4: Session Termination (Logout)

- [ ] **T4 — Logout: blacklist JTI in Redis and revoke refresh token in DB, with fail-closed semantics**
  - **Size:** M
  - **Depends on:** T2 (requires a valid access token and refresh token issued by login), T3 (richer test: logout, then verify old refresh token also fails refresh)
  - **Layers touched:** Application, Infrastructure/Redis, Infrastructure/Persistence, Presentation
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/ports/in/LogoutUseCase.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/ports/out/JtiBlacklistPort.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/usecases/LogoutUseCaseImpl.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/infrastructure/security/RedisJtiBlacklistAdapter.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/presentation/dto/LogoutRequest.java` _(optional body — nullable `refreshToken` field)_
    - `apps/backend/src/test/java/com/unihub/workshop/iam/application/usecases/LogoutUseCaseImplTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/iam/presentation/controllers/AuthControllerLogoutTest.java`
  - **Acceptance Criteria:**
    - `POST /api/v1/auth/logout` with a valid Bearer access token and an optional `refreshToken` body field returns `204 No Content`.
    - After logout, a request to any protected endpoint using the same Bearer token returns `401 Unauthorized` (JTI blacklist entry verified in Redis with TTL = `exp - NOW()`).
    - After logout with a `refreshToken` in body, `POST /api/v1/auth/refresh` using the same refresh token returns `401 Unauthorized` (row has `revoked_at IS NOT NULL`).
    - After logout **without** a `refreshToken` in body, all non-expired, non-revoked `refresh_tokens` rows for that user have `revoked_at IS NOT NULL`.
    - `POST /api/v1/auth/logout` when Redis is unavailable returns `503 Service Unavailable` with `Retry-After: 30` header, and no changes are made to the `refresh_tokens` table.
    - `POST /api/v1/auth/logout` without a valid Bearer token returns `401 Unauthorized` (enforced by the `JwtAuthenticationFilter` before the use case runs).
    - _(Traces to: FR-10, FR-11, NFR-01, BR-07, Scenarios 10, 11, 12)_

#### Checkpoint: Feature Complete

- [ ] All tasks T1–T4 are checked off.
- [ ] Every acceptance criterion across all tasks has been verified end-to-end (endpoint called, response inspected, DB/Redis state confirmed).
- [ ] Full unit test suite passes, including `ModulithArchitectureTest`.
- [ ] No raw password or raw refresh token appears in any log output.
- [ ] Ready for `/review authentication-and-session-management`.

## Risks and Mitigations

| Risk                                                                                                                                                                                                                            | Impact | Mitigation                                                                                                                                                                                                                                                                                                           |
| :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | :----- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Argon2 dummy verification (BR-11) adds 100–500ms to every failed login for unknown emails — this is intentional, but may confuse developers writing tests if they don't account for it.                                         | Low    | Document the constant-time behaviour in `LoginUseCaseImplTest` with a comment; use a low-cost Argon2 parameter set in the test profile to keep tests fast while still exercising the code path.                                                                                                                      |
| Concurrent refresh race condition: two simultaneous calls with the same token may both pass the `revoked_at IS NULL` check before either can commit.                                                                            | High   | `@Transactional` with `SELECT ... FOR UPDATE` on the `refresh_tokens` row (or relying on the DB's unique constraint on `token_hash` combined with the `revoked_at` update) is the linearization point. Must be explicitly unit-tested with a simulated concurrent scenario (Scenario 15).                            |
| Redis fail-closed in T4: if the JTI write is attempted but Redis throws mid-way, the response is `503` and the DB must not be mutated. The ordering of operations (Redis first, DB second) is critical.                         | High   | `LogoutUseCaseImpl` must write to Redis **before** writing to the DB. If the Redis write throws, the method exits immediately with `503` — the DB write is never reached. This ordering is enforced by the unit test for the Redis-unavailable scenario (Scenario 12).                                               |
| `JwtTokenIssuer` signing service is placed in `shared/security` (a shared kernel package, not inside `iam`). Spring Modulith boundary rules must allow `iam` to call into `shared/security` without violating the module graph. | Medium | `shared/security` is already the established cross-cutting security package (it houses `JwtAuthenticationFilter`, `RedisJtiValidator`, etc.). Placing `JwtTokenIssuer` there is consistent with the existing pattern. `ModulithArchitectureTest` must pass at each checkpoint to catch any boundary violation early. |
| `RedisJtiBlacklistAdapter` in T4 wraps the same Redis client already used by `RedisJtiValidator` in `shared/security`. These should not duplicate configuration.                                                                | Low    | `RedisJtiBlacklistAdapter` is placed in `iam/infrastructure/security/` and reuses the `RedisTemplate<String, String>` bean declared in the shared Spring config — no new Redis connection configuration is needed.                                                                                                   |

## Open Questions

None. All design decisions in the spec and contract were resolved before this plan was written.
