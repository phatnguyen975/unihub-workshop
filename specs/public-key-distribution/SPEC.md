# Spec: Public Key Distribution

> **Status:** APPROVED  
> **Domain:** 1 — Identity & Access Management  
> **Owner/area:** `iam` module (presentation layer) + `shared/security` (key material)  
> **Last revised:** 2026-09-05 — first version

## 1. Overview

The `public-key-distribution` feature exposes a dedicated, authenticated endpoint through which the mobile check-in app can retrieve the current RSA public key in PEM format on demand. When a `STAFF` member opens the mobile app while online, the app calls this endpoint to fetch and locally persist the latest public key in its SQLite `mobile_public_keys` table. This stored key is subsequently used to verify QR code signatures offline during check-in events, with no network connection required. The endpoint is the sole recovery mechanism when a key rotation event occurs while the mobile app is already authenticated — without it, a `STAFF` device holding a stale key would be unable to verify any QR code after rotation until the user logs out and back in. The feature is owned jointly by the `shared/security` shared kernel (which holds the RSA key material) and the `iam` module (which exposes the HTTP presentation layer and enforces JWT authentication).

## 2. Actors & Preconditions

| Actor                      | Precondition                                                                                                                   |
| :------------------------- | :----------------------------------------------------------------------------------------------------------------------------- |
| `STAFF` (primary consumer) | Must be authenticated with a valid, non-revoked RS256 JWT with `role = STAFF`. The JWT must not be on the Redis JTI blacklist. |

> **Primary Consumer:** The mobile React Native app acting on behalf of a `STAFF` user. This endpoint is the authoritative source for mobile offline key refresh.

## 3. Functional Requirements

### Key Retrieval

- **FR-01:** The system SHALL return the current RSA public key material in PEM-encoded format when a request with a valid, non-revoked JWT is received.
- **FR-02:** The system SHALL include a `keyId` field in the response that uniquely identifies the current public key, enabling the mobile app to detect key rotation without comparing PEM content byte-by-byte.
- **FR-03:** The system SHALL include an `issuedAt` timestamp in the response, in ISO 8601 UTC format, representing when the key became active on this server instance (i.e. request-time UTC).

### Authentication and Authorization

- **FR-04:** The system SHALL reject requests that do not carry an `Authorization: Bearer <token>` header with HTTP `401 Unauthorized`.
- **FR-05:** The system SHALL reject requests where the JWT is expired, malformed, missing mandatory claims (`sub`, `org`, `jti`, `role`, `sid`, `iat`, `exp`), has an invalid signature, or whose `jti` claim is present in the Redis JTI blacklist, with HTTP `401 Unauthorized`.
- **FR-06:** The system SHALL reject requests from authenticated users who do not have the `STAFF` role with HTTP `403 Forbidden`.

### Key Material Source

- **FR-07:** The system SHALL derive the PEM response from the same RSA public key that is currently loaded in memory and used for JWT signature verification — there is exactly one active key at any time, sourced from the configured key file at application startup.
- **FR-08:** The system SHALL derive `keyId` deterministically from the public key material so that: (a) the same key always produces the same `keyId`, and (b) a different key (after rotation) always produces a different `keyId`, without requiring any database state.

## 4. Business Rules

- **BR-01:** This endpoint is strictly limited to the `STAFF` role. Other roles (`STUDENT`, `ORGANIZER`) have no legitimate use case for offline key caching and must be blocked from accessing this endpoint.
- **BR-02:** The RSA public key material is loaded from `classpath:security/public.pem` at application startup and held in memory for the lifetime of the process. The endpoint must not read from disk on every request; it must read from the already-loaded in-memory bean (`RsaKeyProperties`). This is a correctness and performance constraint.
- **BR-03:** The `keyId` returned must be stable (deterministic) for the same key material and must change when the key material changes. A non-deterministic `keyId` that changes on every request would cause unnecessary cache churn on the mobile side. (Algorithm: SHA-256 fingerprint).
- **BR-04:** The JTI blacklist check is fail-closed (ADR-010): if Redis is unavailable, the authentication filter returns `503 Service Unavailable`. This is enforced by the existing `RedisJtiValidator` and is not changed by this feature.
- **BR-05:** No database tables are read or written by this endpoint. Key material is sourced entirely from the in-memory `RsaKeyProperties` bean. Consistent with `docs/feature-catalog.md 1.3`: "DB Tables: None."
- **BR-06:** The `public.pem` file must exist at application startup — the existing `RsaKeyConfig` constructor throws `IllegalStateException` if either key is missing. This feature inherits that startup fail-fast guarantee and adds no new startup requirement.

## 5. State Transitions

N/A — this feature does not introduce or modify any stateful entity. The RSA key pair is static for the lifetime of a running process instance. Key rotation is an operational procedure (replacing PEM files and restarting) that is outside this feature's runtime scope.

## 6. Non-Functional Requirements

- **Performance:** The endpoint response must be generated entirely from in-memory data (no disk I/O, no database query, no external network call). Response time must be well under 50ms p99 under normal load. This endpoint is a low-call-volume utility path (called once per STAFF login session).
- **Concurrency:** No mutable state is touched by this endpoint. The public key bean is immutable after startup. Multiple concurrent requests may be safely served from the same in-memory key without synchronization overhead.
- **Idempotency:** The endpoint is a `GET` (read-only). Idempotency is guaranteed by HTTP method semantics. No `Idempotency-Key` header is required.
- **Rate-limiting:** Not subject to the Bucket4j registration rate-limit tier. Standard server-side connection limits apply. No custom rate-limiting required.
- **Security:** Requires a valid, non-revoked JWT (enforced by existing `JwtAuthenticationFilter` + `RedisJtiValidator`). The private key must never be returned or exposed by this endpoint.
- **Reliability / Failure modes:** The only runtime failure mode is Redis unavailability (fail-closed `503` per BR-04), which is pre-existing behavior. Key material loading failures are caught at startup (BR-06), not at request time.
- **Observability:** Standard API logging format applies (`docs/api-standards.md`). Standard Micrometer HTTP instrumentation captures request-duration and status-code counters. No additional custom metrics required.

## 7. Acceptance Criteria

### Scenario 1: `STAFF` retrieves the public key successfully

```gherkin
Given a STAFF user is authenticated with a valid, non-revoked JWT
When the user sends GET /api/v1/auth/public-key with the Authorization: Bearer header
Then the response status is 200 OK
And the response body contains a "data" object with fields: keyId, publicKeyPem, issuedAt
And keyId is a non-empty string
And publicKeyPem begins with "-----BEGIN PUBLIC KEY-----" and ends with "-----END PUBLIC KEY-----"
And issuedAt is an ISO 8601 UTC timestamp string
```

_(Traces to: FR-01, FR-02, FR-03)_

### Scenario 2: Unauthenticated request is rejected

```gherkin
Given a request with no Authorization header
When the request is sent to GET /api/v1/auth/public-key
Then the response status is 401 Unauthorized
And the response body has "success": false with RFC 7807 error format
And the error "type" is "urn:unihub:error:unauthorized"
```

_(Traces to: FR-04)_

### Scenario 3: Expired JWT is rejected

```gherkin
Given a JWT whose exp claim is in the past
When the user sends GET /api/v1/auth/public-key with that JWT
Then the response status is 401 Unauthorized
And the response body follows the RFC 7807 error format
And the error "type" is "urn:unihub:error:unauthorized"
```

_(Traces to: FR-05)_

### Scenario 4: Revoked JWT (on Redis blacklist) is rejected

```gherkin
Given a JWT whose jti is present in the Redis JTI blacklist
When the user sends GET /api/v1/auth/public-key with that JWT
Then the response status is 401 Unauthorized
And the response body follows the RFC 7807 error format
And the error "type" is "urn:unihub:error:unauthorized"
```

_(Traces to: FR-05)_

### Scenario 5: `ORGANIZER` is rejected with Forbidden

```gherkin
Given an ORGANIZER user is authenticated with a valid, non-revoked JWT
When the user sends GET /api/v1/auth/public-key with the Authorization header
Then the response status is 403 Forbidden
And the response body follows the RFC 7807 error format
And the error "type" is "urn:unihub:error:forbidden"
```

_(Traces to: FR-06, BR-01)_

### Scenario 6: `STUDENT` is rejected with Forbidden

```gherkin
Given a STUDENT user is authenticated with a valid, non-revoked JWT
When the user sends GET /api/v1/auth/public-key with the Authorization header
Then the response status is 403 Forbidden
And the response body follows the RFC 7807 error format
And the error "type" is "urn:unihub:error:forbidden"
```

_(Traces to: FR-06, BR-01)_

### Scenario 7: `keyId` is deterministic and stable for the same key

```gherkin
Given the same RSA public key is loaded in memory
When GET /api/v1/auth/public-key is called twice by the same authenticated user
Then both responses return the same keyId value
And both responses return the same publicKeyPem value
```

_(Traces to: FR-07, FR-08, BR-03)_

### Scenario 8: `keyId` changes after key rotation

```gherkin
Given the server is restarted with a different RSA key pair (key rotation)
And a STAFF user is authenticated with a JWT signed by the new private key
When the user sends GET /api/v1/auth/public-key
Then the response contains a keyId different from the keyId returned before rotation
And publicKeyPem reflects the new public key material
```

_(Traces to: FR-08, BR-03)_

### Scenario 9: Redis unavailable during JTI validation (fail-closed)

```gherkin
Given Redis is unreachable or returns a connection error
When an authenticated STAFF user sends GET /api/v1/auth/public-key with a valid JWT
Then the response status is 503 Service Unavailable
And the response header "Retry-After" is "30"
And the response body has "success": false with RFC 7807 error format
And the error "type" is "urn:unihub:error:service-unavailable"
```

_(Traces to: BR-04, ADR-010)_

## 8. Out of Scope

- **Key rotation triggering:** This feature does not implement any mechanism to rotate the RSA key pair. Rotation is an operational procedure outside scope.
- **Key versioning history / key list:** The endpoint returns only the single, currently active public key. No history of past keys.
- **JWKS format / JWKS endpoint changes:** The existing `/.well-known/jwks.json` endpoint is not modified. This feature is complementary (PEM for mobile vs. JWKS for downstream JWT verification).
- **Role-specific key variants:** There is one RSA key pair for the entire system.
- **Mobile storage logic:** How the mobile app stores the retrieved key in `mobile_public_keys` SQLite is a mobile-client concern.
- **QR code signing:** This is part of F-6.1 (`mobile-offline-checkin`), which depends on this feature as a prerequisite.
- **Push-based key refresh:** Key updates are pull-based only.
- **Admin endpoint to trigger rotation:** No rotation trigger endpoint is introduced.

## 9. Linked References

| Document                                                        | Section(s) Referenced                                                                            |
| :-------------------------------------------------------------- | :----------------------------------------------------------------------------------------------- |
| `docs/feature-catalog.md`                                       | §1.3 `public-key-distribution`                                                                   |
| `docs/system-design.md`                                         | §5 — Offline PKI & Cryptography                                                                  |
| `docs/adrs.md`                                                  | ADR-005 (JWT RS256), ADR-010 (Redis fail-closed), ADR-029 (Offline QR Verification Cryptography) |
| `docs/database-schema.md`                                       | Mobile SQLite — `mobile_public_keys` table (keyId, publicKeyPem, issuedAt)                       |
| `docs/api-standards.md`                                         | HTTP Status Codes, Mandatory Headers, Success Response Envelope, RFC 7807 Error, ISO 8601 UTC    |
| `docs/project-structure.md`                                     | §3 Core Backend — `shared/security`, `iam` module layout                                         |
| `apps/backend/src/main/resources/application.yml`               | `rsa.public-key`, `app.security.public-paths`                                                    |
| `apps/backend/.../shared/security/RsaKeyProperties.java`        | `RSAPublicKey publicKey()` bean                                                                  |
| `apps/backend/.../shared/security/RsaKeyConfig.java`            | Startup fail-fast guard                                                                          |
| `apps/backend/.../shared/security/JwksController.java`          | Existing complementary JWKS endpoint (not modified)                                              |
| `apps/backend/.../shared/security/SecurityConfig.java`          | public-paths list (confirms auth required)                                                       |
| `apps/backend/.../shared/security/JwtAuthenticationFilter.java` | Auth + JTI blacklist enforcement (pre-existing)                                                  |
