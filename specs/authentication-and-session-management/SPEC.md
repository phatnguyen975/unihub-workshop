# Spec: Authentication and Session Management

> **Status:** APPROVED  
> **Domain:** 1 — Identity & Access Management  
> **Owner/area:** `docs/feature-catalog.md §1.2`  
> **Last revised:** 2026-09-05 — first version

## 1. Overview

This feature implements the complete authentication session lifecycle for the UniHub Workshop platform. It covers four distinct capabilities that together make up a secure, stateless session contract: (1) **login** — validating email/password credentials and issuing a short-lived RS256-signed JWT access token paired with a long-lived refresh token; (2) **access token refresh** — exchanging a valid refresh token for a new access token and rotating the refresh token in the same atomic operation; (3) **logout** — revoking the caller's current access token via the Redis JTI blacklist and invalidating the corresponding refresh token row; and (4) **JWKS endpoint** — exposing the RSA public key in JWK Set format so that downstream services and the API gateway can verify token signatures without holding the private key.
The feature also enforces the mandatory first-login password change gate for accounts where `is_temporary_password = TRUE` — accounts provisioned via the `staff-and-organizer-provisioning` flow and root `ORGANIZER` accounts seeded at startup. A user presenting valid credentials but with `is_temporary_password = TRUE` receives a specific response code and **no tokens** — they must reset their password before obtaining a session.

This feature lives in the `iam` module of the Core Backend, with the JWT filter chain and Redis JTI blacklist living in `shared/security` (already established during feature `ingestion-reject-report`). It depends on `infrastructure-bootstrap-and-seed-data` (RSA key pair load, `refresh_tokens` TTL cleanup) and on the existing `shared/security` infrastructure.

## 2. Actors & Preconditions

### Actors

| Actor              | Involvement                                                                                   |
| :----------------- | :-------------------------------------------------------------------------------------------- |
| STUDENT            | Self-registered user performing login, refresh, and logout.                                   |
| ORGANIZER          | Platform-provisioned or seeded user performing login (may be forced to reset password first). |
| STAFF              | Platform-provisioned user performing login, refresh, and logout.                              |
| Anonymous / Public | Calls the JWKS endpoint (`/.well-known/jwks.json`) without authentication.                    |
| System / Sweeper   | The TTL cleanup scheduler that purges expired `refresh_tokens` rows (already spec'd in 10.4). |

### Preconditions

- **PC-01:** The RSA key pair (`private.pem`, `public.pem`) has been loaded into the Spring application context at startup (guaranteed by `infrastructure-bootstrap-and-seed-data` FR-02).
- **PC-02:** A `users` row exists with the submitted email (the account was created via `user-registration-and-identity-verification` or `staff-and-organizer-provisioning` or the startup seed runner).
- **PC-03:** Redis is reachable for JTI blacklist checks. If Redis is unavailable, the JTI blacklist check MUST fail closed per ADR-010.
- **PC-04:** The `refresh_tokens` table exists in the database (established by `V1__init_schema.sql` from `infrastructure-bootstrap-and-seed-data`).
- **PC-05:** For access token refresh: the caller presents a raw refresh token string that was previously issued to them by the login endpoint. The system locates the row by hashing the presented token and comparing against `token_hash`.

## 3. Functional Requirements

### FR-01 — Login: credential validation

The system **SHALL** accept an email and password from the request body and look up the matching `users` row by email (case-insensitive via `CITEXT`). The system **SHALL** verify the presented password against the stored `password_hash` using the same algorithm that produced the hash (Argon2, per `docs/database-schema.md §users`).

The system **SHALL** reject login with a generic `401 Unauthorized` when:

- No `users` row matches the submitted email, OR
- The submitted password does not match `password_hash`.

The error response **SHALL NOT** distinguish between "email not found" and "wrong password" — both map to the same generic `401` to prevent user enumeration.

### FR-02 — Login: inactive account gate

The system **SHALL** reject login with `403 Forbidden` when the matching `users` row has `is_active = FALSE`. The error detail **SHALL** indicate that the account has been disabled.

### FR-03 — Login: temporary password gate

The system **SHALL** check `is_temporary_password` on a successfully authenticated `users` row. When `is_temporary_password = TRUE`, the system **SHALL NOT** issue any tokens. Instead, the system **SHALL** return `403 Forbidden` with a specific error type (`urn:unihub:error:temporary-password`) so that the client can route the user to the mandatory password-change screen. No `refresh_tokens` row is created in this case.

### FR-04 — Login: access token issuance

The system **SHALL** issue a short-lived RS256-signed JWT access token upon successful authentication (after passing FR-01, FR-02, and FR-03 gates). The access token payload **SHALL** include:

| Claim  | Value                                                         |
| :----- | :------------------------------------------------------------ |
| `sub`  | The user's `UUID` (`users.id`)                                |
| `role` | The user's `role` string (`STUDENT`, `ORGANIZER`, or `STAFF`) |
| `org`  | The user's `organization_id` UUID (nullable, omitted if null) |
| `jti`  | A freshly generated UUIDv4 unique to this token               |
| `sid`  | The session UUID (`refresh_tokens.id`) bound to this token    |
| `iat`  | Current UTC timestamp (Unix epoch seconds)                    |
| `exp`  | `iat` + configured access token TTL (e.g., 1 hour)            |

The access token **SHALL** be signed with the RSA private key loaded at startup.

The access token TTL **SHALL** be configurable via `application.yml` (e.g., `app.security.access-token-ttl`).

### FR-05 — Login: refresh token issuance

The system **SHALL** generate a cryptographically random opaque refresh token string for each successful login. The raw token string **SHALL** be returned to the caller exactly once in the login response body. The system **SHALL** store only the `SHA-256` hash of the token in the `refresh_tokens.token_hash` column — the raw token **SHALL NOT** be stored.

The `refresh_tokens` row **SHALL** be created with:

- `user_id`: the authenticated user's `id`
- `token_hash`: `SHA-256(rawToken)`
- `device_info`: the `User-Agent` header value from the login request (truncated to 255 characters if longer)
- `ip_address`: the client IP address extracted from the request
- `expires_at`: current time + configured refresh token TTL (e.g., 7 days)
- `revoked_at`: `NULL`

The refresh token TTL **SHALL** be configurable via `application.yml` (e.g., `app.security.refresh-token-ttl`).

### FR-06 — Login: update `last_login_at`

The system **SHALL** update `users.last_login_at` to the current UTC timestamp on every successful login (after tokens are generated).

### FR-07 — Login: user profile response

The system **SHALL** return the authenticated user's profile in the `user` field of the login response body (`POST /api/v1/auth/login`). This enables the client (Web/Mobile) to immediately populate application state and render identity-dependent UI (e.g. Navbar, role-based navigation) without making an additional round-trip request to `/users/me`.

The user profile object **SHALL** include:

- `id`: the user's UUID
- `email`: user's email address
- `fullName`: user's full name
- `role`: user's role (`STUDENT`, `ORGANIZER`, `STAFF`)
- `phoneNumber`: user's phone number (nullable, omitted if null)
- `avatarUrl`: URL of user's avatar image (nullable, omitted if null)
- `lastLoginAt`: previous login UTC timestamp (nullable, omitted if null)
- `organizationId`: user's associated organization UUID (nullable, omitted if null)
- `studentRecordId`: associated student directory record UUID (nullable, omitted if null)

All nullable fields **SHALL** be omitted when null, conforming to `@JsonInclude(NON_NULL)`.

### FR-08 — Token refresh: locate and validate

The system **SHALL** accept a raw refresh token string in the request body. The system **SHALL** compute `SHA-256(rawToken)` and look up the matching `refresh_tokens` row by `token_hash`.

The system **SHALL** reject the refresh request with `401 Unauthorized` when:

- No row matches the computed hash (token was never issued or already fully deleted), OR
- The matched row has `revoked_at IS NOT NULL` (token was explicitly revoked), OR
- The matched row has `expires_at < NOW()` (token has expired by TTL).

### FR-09 — Token refresh: refresh token rotation

The system **SHALL** execute the following atomically in a single database transaction on a valid refresh request:

1. Set `revoked_at = NOW()` on the consumed `refresh_tokens` row (invalidate the old token).
2. Generate a new cryptographically random refresh token string.
3. Insert a new `refresh_tokens` row with the new `token_hash`, inheriting the same `user_id`, `device_info`, `ip_address`, and a fresh `expires_at = NOW() + refreshTokenTtl`. `revoked_at` is `NULL`.
4. Issue a new RS256-signed access token (with a fresh `jti` and `sid` matching the newly inserted `refresh_tokens.id`) for the user, and track the active session in Redis (`session:jti:{sessionId}`).

The raw new refresh token string and the new access token **SHALL** be returned in the response body.

**No `jti` blacklist entry is needed for the consumed access token on a pure refresh call** — the old access token will expire naturally by its own TTL. Blacklisting on refresh is reserved for the explicit logout endpoint (FR-10). The Redis session mapping `session:jti:{sessionId}` is rotated to point to the new session's JTI.

### FR-10 — Logout: JTI blacklisting and refresh token revocation

The system **SHALL** accept a logout request from an authenticated caller carrying a valid Bearer access token. The system **SHALL** perform the following in order:

1. Extract the `jti` claim from the caller's current access token.
2. Write `jti` to the Redis JTI blacklist with a TTL equal to the access token's remaining validity duration (`exp - now`). After this write, any further request presenting this access token **SHALL** be rejected by the `JwtAuthenticationFilter` with `401 Unauthorized`.
3. Look up and soft-revoke the caller's `refresh_tokens` row associated with the current session. If the caller includes a `refreshToken` value in the request body, use `SHA-256(refreshToken)` to identify the exact row to revoke. If no `refreshToken` is provided, the system **SHALL** revoke all non-expired, non-revoked `refresh_tokens` rows belonging to the caller (`user_id` filter), and **SHALL** log a warning that session-specific identification was not possible.

### FR-11 — Logout: Redis unavailability handling

When Redis is unavailable during logout, the system **SHALL** return `503 Service Unavailable` with a `Retry-After: 30` header, consistent with the fail-closed JTI policy in ADR-010. The refresh token revocation in the database **SHALL NOT** be attempted unless the Redis write succeeds — both operations must succeed or neither takes effect.

### FR-12 — JWKS endpoint

The system **SHALL** expose a public endpoint at `/.well-known/jwks.json` that returns the RSA public key in JWK Set format (RFC 7517 / RFC 7518). The response **SHALL** include at minimum:

- `kty`: `RSA`
- `use`: `sig`
- `alg`: `RS256`
- `n`: base64url-encoded RSA public modulus
- `e`: base64url-encoded RSA public exponent

The endpoint **SHALL** require no authentication (public). The public key material **SHALL** be the same key already loaded at startup by `shared/security/RsaKeyConfig` (the single source of truth for key material).

### FR-13 — Access token validation on every protected request

The system **SHALL** validate every `Authorization: Bearer <token>` on protected endpoints by:

1. Verifying the JWT signature against the RSA public key.
2. Verifying the token has not expired (`exp > NOW()`).
3. Verifying the `jti` claim is NOT present in the Redis JTI blacklist (fail-closed per ADR-010).
4. Populating the Spring Security `SecurityContext` with the authenticated principal (`sub`, `role`, `org`, `jti`).

These checks are already partially implemented in `shared/security.JwtAuthenticationFilter`; this feature ensures the implementation satisfies all four conditions end-to-end and is backed by unit-tested use cases in the `iam` module.

### FR-14 — Primary DB routing for all session operations

All reads from and writes to `refresh_tokens` **SHALL** be routed to the primary database, since refresh token operations are check-then-act flows that are sensitive to replication lag (per `docs/database-schema.md §4`). The `@Transactional` annotation (not `readOnly`) on all session mutation methods ensures correct routing through the `RoutingDataSource` established in feature `10.4`.

## 4. Business Rules

- **BR-01:** The `role` claim in the JWT **SHALL** be the exact string stored in `users.role` (`STUDENT`, `ORGANIZER`, or `STAFF`) without any `ROLE_` prefix in the database or in the token claim. The `JwtAuthenticationFilter` in `shared/security` prepends `ROLE_` only at the `GrantedAuthority` layer for Spring Security's internal `hasRole()` matching — this is a framework convention, not a claim in the token.
- **BR-02:** Refresh tokens are **never stored as plaintext**. Only `SHA-256(rawToken)` is persisted. Logging the raw token at any level is forbidden.
- **BR-03:** Access tokens have a short TTL (target: 1 hour, configurable). Refresh tokens have a longer TTL (target: 7 days, configurable). TTLs **SHALL** be declared in `application.yml` under `app.security.*-ttl` and not hard-coded in Java.
- **BR-04:** The `jti` claim is a UUIDv4 generated fresh for each issued access token. Two tokens for the same user in the same second **SHALL** have different `jti` values.
- **BR-05:** Refresh token rotation is mandatory on every use (FR-09). Reuse of a previously consumed refresh token (one with `revoked_at IS NOT NULL`) **SHALL** be rejected with `401` — no new token is issued.
- **BR-06:** A user may have multiple active `refresh_tokens` rows simultaneously (one per device/session). Single-device logout (`DELETE /api/v1/auth/sessions/{id}`) is handled by the `active-session-management` feature (1.7). The logout endpoint in this feature (FR-10) revokes the session associated with the current request.
- **BR-07:** The Redis JTI blacklist TTL **SHALL** match the remaining validity of the access token at the time of logout (i.e., `exp - NOW()`). This prevents unbounded Redis growth while guaranteeing the key stays present until the token naturally expires.
- **BR-08:** The `is_temporary_password` gate (FR-03) takes precedence over normal token issuance. An account with `is_temporary_password = TRUE` can never receive a standard session token until the password is changed via the `password-reset` or equivalent flow.
- **BR-09:** `users.last_login_at` is updated on the primary DB on every successful login, consistent with the primary-routing requirements in `docs/database-schema.md §4`.
- **BR-10:** Login attempts for non-existent emails **SHALL** take constant time (timing-safe comparison) to prevent user enumeration via timing side-channel. Argon2 verification is only called when the user row is found; when no row is found, a dummy Argon2 verification **SHALL** still be executed to preserve constant-time behaviour.

## 5. State Transitions

### 5.1 Session Lifecycle

The login endpoint does not mutate a single domain entity state machine, but a `refresh_tokens` row is created and `users.last_login_at` is updated:

```
[Unauthenticated User] ──login──► [Active Session]
                                       │
                                       ├──refresh──► [Rotated Session] (new refresh_tokens row; old revoked)
                                       │
                                       └──logout───► [Revoked Session] (revoked_at set; jti blacklisted)
```

### 5.2 Refresh Token Lifecycle

| State            | Condition                                     | Next Transition                                                          |
| :--------------- | :-------------------------------------------- | :----------------------------------------------------------------------- |
| ACTIVE           | `revoked_at IS NULL` AND `expires_at > NOW()` | → CONSUMED (on use via refresh), → EXPIRED (by time), → REVOKED (logout) |
| CONSUMED/REVOKED | `revoked_at IS NOT NULL`                      | Terminal — no further transitions                                        |
| EXPIRED          | `expires_at ≤ NOW()` AND `revoked_at IS NULL` | Terminal — TTL cleanup sweeper purges it                                 |

**Invalid transition:** Presenting a `CONSUMED` or `EXPIRED` token as if it were `ACTIVE` → rejected with `401 Unauthorized`.

### 5.3 Access Token Lifecycle

| State   | Condition                                        | Effect                             |
| :------ | :----------------------------------------------- | :--------------------------------- |
| VALID   | `exp > NOW()` AND `jti` not in Redis blacklist   | Requests proceed                   |
| REVOKED | `jti` present in Redis blacklist (set on logout) | `JwtAuthFilter` rejects with `401` |
| EXPIRED | `exp ≤ NOW()`                                    | `JwtDecoder` rejects with `401`    |

**Invalid transition:** A `REVOKED` or `EXPIRED` access token cannot be reinstated.

## 6. Non-Functional Requirements

| ID     | Category      | Requirement                                                                                                                                                                                                                       |
| :----- | :------------ | :-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| NFR-01 | Security      | The `jti` Redis blacklist check is **fail-closed** (ADR-010): Redis unavailability returns `503` with `Retry-After: 30` for both protected requests and the logout endpoint.                                                      |
| NFR-02 | Security      | Passwords are hashed with Argon2. The raw password **SHALL** never appear in logs, in a response body, or in the database.                                                                                                        |
| NFR-03 | Security      | Refresh tokens are stored only as `SHA-256` hashes. The raw token is returned to the client exactly once (in the response body). It is never logged, cached to Redis, or stored in any persistence layer.                         |
| NFR-04 | Performance   | Login MUST complete in under 2 seconds (p99) under normal load, including Argon2 verification, DB read/write, and Redis JTI write on logout. Argon2 parameters should be tuned so verification takes 50–500 ms on the target JVM. |
| NFR-05 | Performance   | Token refresh MUST complete in under 500 ms (p99) — it involves only DB reads/writes and no Argon2 computation.                                                                                                                   |
| NFR-06 | Concurrency   | Concurrent refresh requests using the same refresh token MUST NOT produce two valid new sessions. The `revoked_at` update is the linearization point; `@Transactional` on the refresh use case ensures correctness.               |
| NFR-07 | Observability | Failed login attempts (wrong password, inactive account, temporary password) **SHALL** be logged at `WARN` level with the user's email (not password) and the failure reason. Successful logins **SHALL** be logged at `INFO`.    |
| NFR-08 | Observability | A Micrometer counter `auth.login.attempts{outcome=success\|failure,reason=...}` **SHALL** be emitted on every login attempt, and `auth.token.refresh{outcome=...}` on every refresh attempt.                                      |
| NFR-09 | Rate Limiting | Login and token-refresh endpoints **SHALL** be subject to the existing Bucket4j browsing-tier rate-limiting filter to limit brute-force attack vectors. They are NOT subject to the registration-tier rate limit.                 |
| NFR-10 | Correctness   | `last_login_at` update on the `users` row **SHALL** be routed to the primary database.                                                                                                                                            |
| NFR-11 | Security      | The JWKS endpoint **SHALL NOT** expose the RSA private key or any secret material. Only the public key components are returned.                                                                                                   |
| NFR-12 | Timing safety | When no user row is found for the submitted email, the system **SHALL** still execute a dummy Argon2 verification to prevent timing attacks that reveal whether an email is registered.                                           |

## 7. Acceptance Criteria

### Scenario 1: Successful login

```gherkin
Given a user account with email "user@student.hcmus.edu.vn" and a valid Argon2 hashed password
And is_active = TRUE and is_temporary_password = FALSE
When the user submits a POST /api/v1/auth/login with correct email and password
Then the response is 200 OK
And the response body contains a non-empty accessToken (JWT)
And the response body contains a non-empty refreshToken (opaque string)
And the response body contains a "user" object with id, email, fullName, role, and organizationId
And a new refresh_tokens row is created in the database with revoked_at = NULL
And users.last_login_at is updated to approximately NOW()
```

_(Traces to: FR-01, FR-04, FR-05, FR-06, FR-07)_

### Scenario 2: Login validation failure

```gherkin
Given a caller submits POST /api/v1/auth/login with an invalid email format or empty password
Then the response is 422 Unprocessable Content
And the error type is "urn:unihub:error:validation"
And the response contains a "validationErrors" list
```

_(Traces to: `API_CONTRACT` validation)_

### Scenario 3: Login with wrong password

```gherkin
Given a STUDENT account with email "student@student.hcmus.edu.vn"
When the student submits POST /api/v1/auth/login with an incorrect password
Then the response is 401 Unauthorized
And the error type is "urn:unihub:error:unauthorized"
And no refresh_tokens row is created
And no accessToken is returned
```

_(Traces to: FR-01)_

### Scenario 4: Login with unknown email

```gherkin
Given no user with email "unknown@example.com" exists in the system
When a caller submits POST /api/v1/auth/login with email "unknown@example.com"
Then the response is 401 Unauthorized
And the error type is "urn:unihub:error:unauthorized"
And no refresh_tokens row is created
```

_(Traces to: FR-01, NFR-12)_

### Scenario 5: Login with inactive account

```gherkin
Given a STUDENT account with is_active = FALSE
When the student submits POST /api/v1/auth/login with correct credentials
Then the response is 403 Forbidden
And the error type is "urn:unihub:error:account-disabled"
And no tokens are returned
```

_(Traces to: FR-02)_

### Scenario 6: Login with temporary password

```gherkin
Given an ORGANIZER account with is_temporary_password = TRUE
When the organizer submits POST /api/v1/auth/login with correct credentials
Then the response is 403 Forbidden
And the error type is "urn:unihub:error:temporary-password"
And no accessToken or refreshToken is returned
And no refresh_tokens row is created
```

_(Traces to: FR-03)_

### Scenario 7: Successful token refresh with rotation

```gherkin
Given a user has an active refresh token "raw-token-abc"
And the corresponding refresh_tokens row has revoked_at = NULL and expires_at > NOW()
When the user submits POST /api/v1/auth/refresh with refreshToken = "raw-token-abc"
Then the response is 200 OK
And the response body contains a new accessToken
And the response body contains a new refreshToken different from "raw-token-abc"
And the old refresh_tokens row now has revoked_at IS NOT NULL
And a new refresh_tokens row is created with the new token_hash and revoked_at = NULL
```

_(Traces to: FR-08, FR-09)_

### Scenario 8: Refresh with revoked token

```gherkin
Given a refresh token that was previously consumed (revoked_at IS NOT NULL)
When the user submits POST /api/v1/auth/refresh with that token
Then the response is 401 Unauthorized
And the error type is "urn:unihub:error:unauthorized"
And no new tokens are issued
```

_(Traces to: FR-08, BR-05)_

### Scenario 9: Refresh with expired token

```gherkin
Given a refresh token whose refresh_tokens row has expires_at < NOW() and revoked_at IS NULL
When the user submits POST /api/v1/auth/refresh with that token
Then the response is 401 Unauthorized
And the error type is "urn:unihub:error:unauthorized"
And no new tokens are issued
```

_(Traces to: FR-08)_

### Scenario 10: Successful logout

```gherkin
Given a user is authenticated with a valid Bearer access token
And the user has an active refresh_tokens row
When the user submits POST /api/v1/auth/logout with their Bearer token
Then the response is 204 No Content
And the access token's jti is written to the Redis JTI blacklist with a TTL of (exp - NOW())
And the corresponding refresh_tokens row has revoked_at IS NOT NULL
And a subsequent request with the same Bearer token is rejected with 401 Unauthorized
```

_(Traces to: FR-10, BR-07)_

### Scenario 11: Protected endpoint rejects revoked access token

```gherkin
Given a user has logged out and their jti is in the Redis blacklist
When the user attempts a GET request to a protected endpoint with the old Bearer token
Then the response is 401 Unauthorized
And the error type is "urn:unihub:error:unauthorized"
```

_(Traces to: FR-13)_

### Scenario 12: Logout fails when Redis is unavailable

```gherkin
Given Redis is unreachable
When an authenticated user submits POST /api/v1/auth/logout
Then the response is 503 Service Unavailable with a Retry-After: 30 header
And no changes are made to the refresh_tokens table
```

_(Traces to: FR-11, NFR-01)_

### Scenario 13: JWKS endpoint returns public key without auth

```gherkin
Given the backend is running with an RSA key pair loaded
When an unauthenticated caller sends GET /.well-known/jwks.json
Then the response is 200 OK
And the response body contains a "keys" array with at least one key entry
And the key entry has kty = "RSA", use = "sig", alg = "RS256", and non-empty n and e fields
And no private key material (d, p, q, dp, dq, qi) appears in the response
```

_(Traces to: FR-12, NFR-11)_

### Scenario 14: Login timing is constant regardless of email existence

```gherkin
Given email "nonexistent@example.com" does not exist in the database
When a login request is made with that email
Then the response time is comparable to a login request with a valid-but-wrong-password email
```

_(Traces to: NFR-12)_

### Scenario 15: Concurrent refresh with same token produces only one new session

```gherkin
Given user has one active refresh token "concurrent-token"
When two simultaneous POST /api/v1/auth/refresh requests are submitted with "concurrent-token"
Then exactly one succeeds with 200 OK and receives a new token pair
And the other returns 401 Unauthorized (token already rotated)
And the database contains exactly one non-revoked refresh_tokens row for this session
```

_(Traces to: FR-09, NFR-06)_

## 8. Out of Scope

| Item                                                   | Reason                                                                                                            |
| :----------------------------------------------------- | :---------------------------------------------------------------------------------------------------------------- |
| User self-registration                                 | Handled by `user-registration-and-identity-verification` (1.1).                                                   |
| Password reset flow                                    | Handled by `password-reset` (1.4). This feature's FR-03 only _gates_ the login response; reset logic is separate. |
| ORGANIZER/STAFF account creation                       | Handled by `staff-and-organizer-provisioning` (1.5).                                                              |
| `GET /api/v1/auth/public-key` endpoint (PEM on demand) | Handled by `public-key-distribution` (1.3). Key retrieval is decoupled to the dedicated endpoint.                 |
| Active session listing and multi-session revocation    | Handled by `active-session-management` (1.7). This feature only handles logout for the caller's current session.  |
| OAuth2 / SSO / federated identity providers            | Not in scope per `docs/overview.md`.                                                                              |
| MFA / two-factor authentication                        | Not in scope for this version.                                                                                    |
| Key rotation mechanics                                 | The key pair is fixed at startup. Key rotation is an operational concern outside this feature's scope.            |
| JWKS endpoint caching headers or CDN distribution      | Not specified in documentation; deferred to operational configuration.                                            |
| User profile updates via the login endpoint            | Profile updates are handled by `user-profile-management` (1.8).                                                   |

## 9. Linked References

| Source                                                 | Section                                                                                                                                    | Relevance                                                                                  |
| :----------------------------------------------------- | :----------------------------------------------------------------------------------------------------------------------------------------- | :----------------------------------------------------------------------------------------- |
| `docs/feature-catalog.md`                              | §1.2 `authentication-and-session-management`                                                                                               | Authoritative feature definition, module ownership, DB tables, ADR refs, key constraints   |
| `docs/feature-catalog.md`                              | §1.7 `active-session-management`                                                                                                           | Out-of-scope boundary for multi-session revocation                                         |
| `docs/system-design.md`                                | §5 — RBAC Model & Multi-Layer Enforcement                                                                                                  | JWT structure, three-role model, multi-layer enforcement points                            |
| `docs/system-design.md`                                | §5 — Offline PKI & Cryptography                                                                                                            | STAFF login returns public key; mobile caches for offline verification                     |
| `docs/system-design.md`                                | §5 — Account Provisioning & Security                                                                                                       | `is_temporary_password` provisioning model                                                 |
| `docs/adrs.md`                                         | ADR-005 (Authentication Mechanism), ADR-006 (Authorization Model), ADR-010 (JTI Blacklist Failure Mode), ADR-028 (Zero-Trust Provisioning) |
| `docs/database-schema.md`                              | §users, §refresh_tokens                                                                                                                    | `users` and `refresh_tokens` table specs; primary-routing rules                            |
| `docs/database-schema.md`                              | §4 — Replication Lag & Read-After-Write Consistency                                                                                        | All `refresh_tokens` and `users.last_login_at` writes must target primary DB               |
| `docs/api-standards.md`                                | §HTTP Status Codes, Error Response RFC 7807, Mandatory Headers                                                                             | Governs response shapes and error formats for all endpoints in this feature                |
| `docs/project-structure.md`                            | §Core Backend — `iam/` and `shared/security/` layout                                                                                       | Package placement for use cases, ports, and security components                            |
| `specs/ingestion-reject-report/SPEC.md`                | FR-04, FR-05 (shared/security setup)                                                                                                       | Existing `JwtAuthenticationFilter`, `RedisJtiValidator`, `SecurityConfig` already in place |
| `specs/infrastructure-bootstrap-and-seed-data/SPEC.md` | FR-02 (RSA key loading), FR-08 (TTL cleanup for `refresh_tokens`)                                                                          | Dependency: key pair and table schema already established                                  |
