# Implementation Plan: Authentication and Session Management

> **Spec:** `specs/authentication-and-session-management/SPEC.md`  
> **API Contract:** `specs/authentication-and-session-management/API_CONTRACT.md`  
> **Status:** APPROVED

## Overview

This plan delivers the four caller-observable capabilities of feature 1.2: (1) the JWKS public endpoint so downstream services can verify JWT signatures; (2) the login endpoint — the core capability that validates credentials, enforces security gates, issues tokens, and returns user profile data for immediate UI rendering; (3) the token refresh endpoint — the rotation capability that atomically consumes a refresh token and issues a new token pair (`RefreshTokenResponse`); and (4) the logout endpoint — which writes the current access token's JTI to the Redis blacklist and revokes the refresh token row.

## Architecture Decisions for This Plan

- **T1 (JWKS) before T2 (Login)** — JWKS is a pure read of the RSA public key already loaded at startup; it has no dependency on any login infrastructure and can be verified immediately. (Already implemented and passing tests).
- **JWT signing infrastructure in T2** — The JWT access token signer (`JwtTokenIssuer`) is used by login (T2) and reused by token refresh (T3). `org` claim must be handled safely when `organization_id` is null.
- **Immediate UI profile rendering in Login** — In accordance with `auth-flows.md` and `docs/feature-catalog.md §1.2`, `POST /api/v1/auth/login` returns a nested `user` profile object (`UserProfileResponse`) inside `data`. This eliminates an extra `/api/v1/users/me` network hop on frontend initial load.
- **Dedicated `RefreshTokenResponse` for Token Refresh** — Refresh is a background headless operation triggered by interceptors; it only needs `accessToken`, `refreshToken`, `tokenType`, and `expiresIn`. A dedicated `RefreshTokenResponse` avoids sending unnecessary null fields or coupling with login profile payload.
- **RFC 7807 Error Alignment** — Request body semantic validation failures return `422 Unprocessable Content` with `urn:unihub:error:validation` and `validationErrors` list (per `GlobalExceptionHandler`). Malformed JSON syntax returns `400 Bad Request`.

## Task List

### Phase 1: Public Key Infrastructure

- [x] **T1 — JWKS endpoint: expose RSA public key for downstream JWT verification**
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

- [x] `GET /.well-known/jwks.json` returns a well-formed JWK Set with the correct RSA public key, verified by a unit test against the key loaded from `classpath:security/public.pem`. Build is clean. `ModulithArchitectureTest` passes.

### Phase 2: Core Authentication Flow (Login with User Profile)

- [x] **T2 — Login: validate credentials, enforce security gates, and issue access + refresh token pair with user profile**
  - **Size:** M
  - **Depends on:** T1
  - **Layers touched:** Domain, Application, Infrastructure/Persistence, Presentation
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/iam/presentation/dto/UserProfileResponse.java` _(new DTO for user profile in login response)_
    - `apps/backend/src/main/java/com/unihub/workshop/iam/presentation/dto/LoginResponse.java` _(add `user` field)_
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/ports/in/LoginUseCase.java` _(include `User` or `UserProfileResponse` in `LoginResult`)_
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/usecases/LoginUseCaseImpl.java` _(populate user profile in result; null-safe org claim)_
    - `apps/backend/src/main/java/com/unihub/workshop/shared/security/JwtTokenIssuer.java` _(null-safe handling for organizationId)_
    - `apps/backend/src/main/java/com/unihub/workshop/iam/presentation/controllers/AuthController.java` _(pass user profile to LoginResponse)_
    - `apps/backend/src/test/java/com/unihub/workshop/iam/application/usecases/LoginUseCaseImplTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/iam/presentation/controllers/AuthControllerLoginTest.java`
  - **Acceptance Criteria:**
    - `POST /api/v1/auth/login` with valid email + correct password for an active, non-temporary-password account returns `200 OK` with `accessToken`, `refreshToken`, `tokenType = "Bearer"`, `expiresIn`, and `user` object (`id`, `email`, `fullName`, `role`, `phoneNumber`, `avatarUrl`, `lastLoginAt`, `organizationId`, `studentRecordId`).
    - The issued `accessToken` is a valid RS256-signed JWT with claims `sub`, `role`, `org` (omitted if null), `jti`, `iat`, `exp` — with no `ROLE_` prefix.
    - A new `refresh_tokens` row is created with `revoked_at = NULL`, `token_hash = SHA-256(refreshToken)`, and the raw token is never stored in DB or logs.
    - `users.last_login_at` is updated to approximately `NOW()` on the primary DB.
    - `POST /api/v1/auth/login` with unknown email or wrong password returns `401 Unauthorized` with `type = "urn:unihub:error:unauthorized"` (dummy Argon2 verification executed for timing safety).
    - `POST /api/v1/auth/login` with correct credentials for an account with `is_active = FALSE` returns `403 Forbidden` with `type = "urn:unihub:error:account-disabled"`.
    - `POST /api/v1/auth/login` with correct credentials for an account with `is_temporary_password = TRUE` returns `403 Forbidden` with `type = "urn:unihub:error:temporary-password"`, and no `refresh_tokens` row is created.
    - `POST /api/v1/auth/login` with invalid email format or blank password returns `422 Unprocessable Content` with `type = "urn:unihub:error:validation"` and `validationErrors`.
    - Micrometer counter `auth.login.attempts` is emitted on success and failure paths.
    - _(Traces to: FR-01 through FR-07, BR-01 through BR-11, Scenarios 1, 2, 3, 4, 5, 6)_

#### Checkpoint: Phase 2

- [x] `POST /api/v1/auth/login` returns complete token pair and user profile for immediate UI rendering. All security gates and anti-enumeration timing safety verified by tests.

### Phase 3: Session Continuation (Token Refresh with Dedicated DTO)

- [x] **T3 — Token refresh: atomically rotate refresh token and issue a new access token**
  - **Size:** M
  - **Depends on:** T2
  - **Layers touched:** Application, Presentation, Infrastructure
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/iam/presentation/dto/RefreshTokenResponse.java` _(dedicated DTO: accessToken, refreshToken, tokenType, expiresIn)_
    - `apps/backend/src/main/java/com/unihub/workshop/iam/presentation/controllers/AuthController.java` _(return `ApiResponse<RefreshTokenResponse>`)_
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/usecases/RefreshTokenUseCaseImpl.java` _(null-safe organizationId in JWT issue)_
    - `apps/backend/src/test/java/com/unihub/workshop/iam/presentation/controllers/AuthControllerRefreshTest.java`
  - **Acceptance Criteria:**
    - `POST /api/v1/auth/refresh` with a valid, non-expired, non-revoked refresh token returns `200 OK` with a new `accessToken` and a new `refreshToken` in `RefreshTokenResponse`.
    - The old `refresh_tokens` row has `revoked_at IS NOT NULL`, and a new row is inserted with `revoked_at = NULL`.
    - `POST /api/v1/auth/refresh` with a revoked, expired, or unknown token returns `401 Unauthorized` with `type = "urn:unihub:error:unauthorized"`.
    - `POST /api/v1/auth/refresh` with a blank `refreshToken` returns `422 Unprocessable Content` with `type = "urn:unihub:error:validation"` and `validationErrors`.
    - Concurrent calls with the same token: exactly one succeeds; the other returns `401 Unauthorized`.
    - Micrometer counter `auth.token.refresh` is emitted.
    - _(Traces to: FR-08, FR-09, BR-05, NFR-06, Scenarios 7, 8, 9, 15)_

#### Checkpoint: Phase 3

- [x] `POST /api/v1/auth/refresh` performs atomic token rotation returning `RefreshTokenResponse`. Unit tests pass.

### Phase 4: Session Termination (Logout)

- [x] **T4 — Logout: blacklist JTI in Redis and revoke refresh token in DB, with fail-closed semantics**
  - **Size:** M
  - **Depends on:** T2, T3
  - **Layers touched:** Application, Infrastructure/Redis, Infrastructure/Persistence, Presentation
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/ports/in/LogoutUseCase.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/ports/out/JtiBlacklistPort.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/usecases/LogoutUseCaseImpl.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/infrastructure/security/RedisJtiBlacklistAdapter.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/presentation/dto/LogoutRequest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/iam/application/usecases/LogoutUseCaseImplTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/iam/presentation/controllers/AuthControllerLogoutTest.java`
  - **Acceptance Criteria:**
    - `POST /api/v1/auth/logout` with a valid Bearer access token and optional `refreshToken` returns `204 No Content`.
    - The access token's JTI is stored in Redis with TTL matching remaining validity. Subsequent requests with that token return `401 Unauthorized`.
    - If `refreshToken` is provided in body, only that row is revoked. If omitted, all active refresh tokens for the user are revoked.
    - If Redis is unreachable, returns `503 Service Unavailable` with `Retry-After: 30` header and makes no DB changes (fail-closed).
    - Unauthenticated request returns `401 Unauthorized`.
    - _(Traces to: FR-10, FR-11, NFR-01, BR-07, Scenarios 10, 11, 12)_

#### Checkpoint: Feature Complete

- [x] All tasks T1–T4 are complete.
- [x] Targeted tests for all modified classes pass in isolation (`./gradlew test --tests ...`).
- [x] Ready for `/review authentication-and-session-management`.

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
