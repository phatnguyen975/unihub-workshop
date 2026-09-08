# Spec: User Profile Management

> **Status:** APPROVED  
> **Domain:** 1 — Identity & Access Management  
> **Owner/area:** `docs/feature-catalog.md §1.8`, `iam` module  
> **Last revised:** 2026-09-08 — first version

## 1. Overview

This feature provides authenticated users of any role (`STUDENT`, `ORGANIZER`, `STAFF`) with the ability to view and self-manage their own profile information. It covers three distinct self-service capabilities: (1) retrieving the caller's full profile snapshot to initialise client-side state on page load or app resume, (2) updating mutable personal details (display name, contact phone, and avatar image URL) while strictly protecting identity and access-control fields from modification, and (3) performing an authenticated, self-service password change that verifies the user's current credential before replacing it, then immediately revokes all other active login sessions as a security measure. Account deactivation and role/organization assignment are explicitly outside this feature's scope and remain under `organization-member-management` (F-1.5). This feature belongs to the `iam` module within the Identity & Access Management domain.

## 2. Actors & Preconditions

### Actors

| Actor       | Description                                                                      |
| :---------- | :------------------------------------------------------------------------------- |
| `STUDENT`   | An authenticated student user managing their own profile details and password.   |
| `ORGANIZER` | An authenticated organizer user managing their own profile details and password. |
| `STAFF`     | An authenticated staff user managing their own profile details and password.     |

### Preconditions

| Actor / Context       | Precondition                                                                                                                                                                           |
| :-------------------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Authenticated Caller  | The user possesses a valid, unexpired, and non-revoked RS256-signed JWT access token containing a `sub` (User ID), `jti` (Token ID), `exp` (Expiration), and `sid` (Session ID) claim. |
| Account Status        | The caller's account is active (`is_active = true`).                                                                                                                                   |
| Primary Database      | The primary PostgreSQL database is operational and accessible for strongly consistent reads and writes.                                                                                |
| Redis (password flow) | The centralized Redis cluster is operational for session `jti` blacklisting during the password-change revocation step. Required only for `PATCH /api/v1/users/me/password`.           |

## 3. Functional Requirements

### Profile Retrieval

- **FR-01:** The system SHALL return the full profile of the authenticated caller when a profile retrieval request is received.
- **FR-02:** The system SHALL derive the caller's identity exclusively from the `sub` claim of the presented JWT access token and SHALL NOT accept a user identifier as a path parameter, query parameter, or request body field.
- **FR-03:** The profile response SHALL include the following fields: `id`, `email`, `fullName`, `role`, `phoneNumber`, `avatarUrl`, `lastLoginAt`, `organizationId`, and `studentRecordId`.
- **FR-04:** The system SHALL omit nullable fields (`phoneNumber`, `avatarUrl`, `lastLoginAt`, `organizationId`, `studentRecordId`) from the response payload when their stored value is `null` (non-null serialization per `docs/api-standards.md`).
- **FR-05:** The system SHALL route all profile retrieval queries to the primary database to guarantee read-after-write consistency and eliminate replication lag.

### Profile Update

- **FR-06:** The system SHALL update the caller's mutable personal fields (`fullName`, `phoneNumber`, `avatarUrl`) when a profile update request is received containing at least one valid, modifiable field.
- **FR-07:** The system SHALL apply partial-update semantics: only fields explicitly present in the request body are candidates for update; fields absent from the request body are left unchanged in the database.
- **FR-08:** The system SHALL reject any profile update request that contains fields from the strictly immutable set (`id`, `email`, `role`, `organizationId`, `studentRecordId`, `isActive`, `isTemporaryPassword`, `createdAt`, `createdBy`) with `422 Unprocessable Content`, without altering any stored value.
- **FR-09:** The system SHALL return the complete updated profile in the response body after a successful update, reflecting all new field values.
- **FR-10:** The system SHALL enforce the following validation constraints on each field supplied in a profile update request:
  - `fullName`: required to be non-blank if provided; maximum 200 characters; may not be set to `null`.
  - `phoneNumber`: must either be `null` (to clear the stored value) or, if non-null, match the international format pattern `^\+?[0-9]{8,15}$`; maximum 20 characters.
  - `avatarUrl`: must either be `null` (to clear the stored value) or, if non-null, be a well-formed absolute URL; maximum 2048 characters.
- **FR-11:** The system SHALL reject a profile update request with `422 Unprocessable Content` when any supplied field fails its validation constraint defined in FR-10, including the full list of offending fields in `validationErrors`.
- **FR-12:** The system SHALL route all profile update writes to the primary database.
- **FR-13:** The system SHALL set the `updated_at` audit timestamp to the current UTC time and `updated_by` to the caller's UUID on each successful profile update.

### Password Change

- **FR-14:** The system SHALL allow an authenticated caller to change their own account password by providing a `currentPassword` and a `newPassword`.
- **FR-15:** The system SHALL verify that `currentPassword` matches the stored password hash for the caller's account using the platform's standard password verification algorithm (Argon2) before performing any mutation.
- **FR-16:** The system SHALL reject a password change request with `422 Unprocessable Content` when `currentPassword` does not match the stored hash, without altering any stored value.
- **FR-17:** The system SHALL reject a password change request with `422 Unprocessable Content` when `newPassword` is identical to `currentPassword`.
- **FR-18:** The system SHALL enforce the following password strength requirements on `newPassword`: minimum 8 characters, maximum 255 characters, containing at least one uppercase letter, one lowercase letter, one digit, and one special character.
- **FR-19:** The system SHALL hash `newPassword` using Argon2 and atomically persist the resulting hash as the new `password_hash` for the caller's account only after all validations pass and Redis is reachable.
- **FR-20:** Upon a successful password change, the system SHALL revoke all active refresh-token sessions belonging to the caller **except** the caller's current session (identified by the `sid` claim of the presented JWT), by setting `revoked_at = NOW()` on each affected `refresh_tokens` record in the primary database.
- **FR-21:** For each remote session revoked under FR-20, the system SHALL write the associated active access token `jti` to the Redis JTI blacklist with a TTL equal to the remaining validity duration of that token (`exp - NOW()`).
- **FR-22:** The system SHALL preserve the caller's current session (identified by `sid`) unmodified in both the database and the Redis blacklist, ensuring the caller remains authenticated without interruption.
- **FR-23:** The system SHALL enforce a fail-closed policy on the Redis blacklist write: if Redis is unreachable at the time of the password change request, the system SHALL abort the entire operation (no password hash update, no session revocations) and return `503 Service Unavailable` with a `Retry-After` header.
- **FR-24:** The system SHALL route all password change reads and writes to the primary database.
- **FR-25:** The system SHALL set `updated_at` to the current UTC time and `updated_by` to the caller's UUID on each successful password change.

### Security & Access Control

- **FR-26:** The system SHALL reject any request to any endpoint in this feature that does not present a valid, unexpired, non-revoked JWT Bearer access token with `401 Unauthorized`.
- **FR-27:** The system SHALL enforce that all operations (profile read, profile update, password change) act exclusively on the account whose `id` matches the `sub` claim of the caller's JWT; no path parameter, query parameter, or body field may override this identity binding.

## 4. Business Rules

- **BR-01 (Strict Identity Immutability):** The fields `id`, `email`, `role`, `organizationId`, `studentRecordId`, `isActive`, and `isTemporaryPassword` are identity and access-control fields managed by other system flows. They must never be writable through profile self-service endpoints. Source: `docs/feature-catalog.md §1.8 Key Constraints`.
- **BR-02 (Self-Service Boundary):** A user may only read and modify their own profile. There is no administrative override path in this feature for reading or modifying another user's profile. Source: `docs/feature-catalog.md §1.8 Key Constraints`, `docs/system-design.md §5 RBAC Model`.
- **BR-03 (Current Password Verification):** A password change requires the caller to prove knowledge of the existing credential before the hash is replaced. This prevents an attacker who has hijacked an authenticated session from silently locking out the account owner. Source: `docs/feature-catalog.md §1.8 Key Constraints`.
- **BR-04 (New Password Must Differ):** `newPassword` must not be identical to `currentPassword`. Setting the same password is rejected as a no-op that could mask an expected security action.
- **BR-05 (Revoke Remote Sessions on Password Change):** A successful password change must immediately invalidate all other active sessions. The caller's current session is preserved. Source: Design Decision 1A, confirmed 2026-09-08; consistent with F-1.7 Revoke All Others semantics.
- **BR-06 (Fail-Closed Blacklist on Password Change):** The Redis JTI blacklist write is a mandatory security control for password change. Failing open would leave revoked session tokens valid across distributed backend nodes. Source: ADR-010, `docs/system-design.md §5 Multi-Layer Enforcement Points`.
- **BR-07 (Primary DB Routing):** All reads and writes in this feature route to the primary PostgreSQL database to avoid stale data from replication lag. Source: ADR-015, `docs/database-schema.md §Replication Lag`.
- **BR-08 (Password Strength Policy Consistency):** The `newPassword` strength requirements (8–255 chars, mixed case, digit, special character) must be identical to those enforced in F-1.1 (registration) and F-1.4 (password reset) to avoid inconsistent security posture across flows.
- **BR-09 (Partial Update — fullName Non-Nullable):** `fullName` may not be set to `null` or empty string via the profile update endpoint, because the database schema defines `full_name VARCHAR(200) NOT NULL` and it is a required display field system-wide. All other mutable fields (`phoneNumber`, `avatarUrl`) may be cleared by passing `null`. Source: `docs/database-schema.md §Table: users`.
- **BR-10 (Audit Trail):** Successful profile update and password change mutations must write `updated_at = NOW()` and `updated_by = callerId` to maintain a complete, attributable audit trail. Source: `docs/database-schema.md §Table: users`.

## 5. State Transitions

This feature does not introduce a new stateful entity. State changes are field-level mutations on existing records. The password-change flow triggers session revocation with state consequences on `refresh_tokens`.

| Entity           | Field / State Change                          | Trigger                                       | Valid? | Rejection Behavior (if invalid)                                                   |
| :--------------- | :-------------------------------------------- | :-------------------------------------------- | :----: | :-------------------------------------------------------------------------------- |
| `users`          | `full_name`, `phone_number`, `avatar_url`     | `PATCH /api/v1/users/me` — success            |  Yes   | N/A                                                                               |
| `users`          | Immutable field mutation attempted            | `PATCH /api/v1/users/me` with immutable field |   No   | `422 Unprocessable Content`; no field altered.                                    |
| `users`          | `password_hash` replaced                      | `PATCH /api/v1/users/me/password` — success   |  Yes   | N/A                                                                               |
| `users`          | Password change when Redis unavailable        | `PATCH /api/v1/users/me/password`             |   No   | `503 Service Unavailable`; entire operation aborted; `password_hash` unchanged.   |
| `users`          | Password change when `currentPassword` wrong  | `PATCH /api/v1/users/me/password`             |   No   | `422 Unprocessable Content`; `password_hash` unchanged.                           |
| `refresh_tokens` | `revoked_at` set to `NOW()` (remote sessions) | Password change — success                     |  Yes   | N/A — all active sessions except `sid` session become REVOKED.                    |
| `refresh_tokens` | Current session (`sid`) revocation attempted  | Password change                               |   No   | System explicitly excludes `sid` session from bulk revocation; it remains ACTIVE. |

## 6. Non-Functional Requirements

- **Performance:** Profile retrieval is a single primary-key lookup; target p99 ≤ 50 ms. Profile update and password change are single-row read + write; target p99 ≤ 200 ms (excluding Redis roundtrip for password flow).
- **Concurrency:** Concurrent profile updates by the same user are handled by JPA `@DynamicUpdate` (only dirty columns flushed), which is acceptable for profile fields. Password change read-then-write must be performed within a single database transaction to prevent TOCTOU races on the hash comparison.
- **Idempotency:** Profile update (`PATCH /api/v1/users/me`) is naturally idempotent for the same payload. Password change is not idempotent (second call with same `currentPassword` fails because the hash has already changed); no `Idempotency-Key` header is required.
- **Rate Limiting:** All endpoints are subject to the Browsing Tier Bucket4j Token Bucket filter (keyed by `jwt.sub`), operating fail-open on Redis unavailability for rate-limit checks (ADR-019). The Redis fail-closed policy (FR-23) applies only to the password change blacklist write, not to the rate-limit check.
- **Security:** The `password_hash` field must never appear in any API response. Argon2 is the mandatory hashing algorithm, consistent with the rest of the IAM module. All inputs crossing the trust boundary are validated. Error messages must not leak whether a particular email or user ID exists.
- **Reliability:** Profile retrieval and update are non-critical paths (fail with `500` on DB error). Password change is a security-critical path with explicit fail-closed semantics on the Redis step (FR-23, BR-06).
- **Observability:** Standard Micrometer → Prometheus → Grafana instrumentation. Request logs include `traceId`, HTTP method/URI, status, duration, and `processedBy` per `docs/api-standards.md §API Logging`.

## 7. Acceptance Criteria

### Scenario 1: Successful profile retrieval

```gherkin
Given an authenticated STUDENT user with a valid non-revoked JWT
When the user sends GET /api/v1/users/me
Then the response status is 200 OK
And success is true in the ApiResponse envelope
And data.id matches the JWT sub claim
And data.email, data.fullName, and data.role are correct
And data.phoneNumber is absent if the stored value is null
```

> Satisfies: FR-01, FR-02, FR-03, FR-04

### Scenario 2: Profile retrieval rejected without token

```gherkin
Given no Authorization header is present
When the user sends GET /api/v1/users/me
Then the response status is 401 Unauthorized
And error.type is urn:unihub:error:unauthorized
```

> Satisfies: FR-26

### Scenario 3: Successful partial profile update — fullName only

```gherkin
Given an authenticated user with a valid JWT
And the stored fullName is "Alice Nguyen", phoneNumber is "+84912345678", and organizationId is "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"
When the user sends PATCH /api/v1/users/me with body { "fullName": "Alice Thu Nguyen" }
Then the response status is 200 OK
And data.fullName equals "Alice Thu Nguyen"
And data.phoneNumber still equals "+84912345678"
And data.organizationId still equals "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"
And the email field in the database is unchanged
```

> Satisfies: FR-06, FR-07, FR-09

### Scenario 4: Partial update — clearing phoneNumber

```gherkin
Given an authenticated user with a valid JWT
And the stored phoneNumber is "+84912345678"
When the user sends PATCH /api/v1/users/me with body { "phoneNumber": null }
Then the response status is 200 OK
And data.phoneNumber is absent from the response
And phone_number in the database is NULL
```

> Satisfies: FR-06, FR-07, FR-09, FR-04, FR-10

### Scenario 5: Profile update rejected — invalid phoneNumber format

```gherkin
Given an authenticated user with a valid JWT
When the user sends PATCH /api/v1/users/me with body { "phoneNumber": "not-a-phone" }
Then the response status is 422 Unprocessable Content
And error.type is urn:unihub:error:validation
And validationErrors lists the phoneNumber constraint violation
```

> Satisfies: FR-10, FR-11

### Scenario 6: Profile update rejected — blank fullName

```gherkin
Given an authenticated user with a valid JWT
When the user sends PATCH /api/v1/users/me with body { "fullName": "   " }
Then the response status is 422 Unprocessable Content
And error.type is urn:unihub:error:validation
And validationErrors lists the fullName constraint violation
```

> Satisfies: FR-10, FR-11

### Scenario 7: Profile update rejected — immutable field attempted

```gherkin
Given an authenticated user with a valid JWT
When the user sends PATCH /api/v1/users/me with body { "email": "hacker@evil.com" }
Then the response status is 422 Unprocessable Content
And error.type is urn:unihub:error:validation
And the email value in the database is unchanged
```

> Satisfies: FR-08, FR-11

### Scenario 8: Successful password change with remote session revocation

```gherkin
Given an authenticated user with a valid JWT (sid = session-A) and two other active sessions (session-B, session-C)
When the user sends PATCH /api/v1/users/me/password with { "currentPassword": "Current@Pass1", "newPassword": "New@Secure2!" }
Then the response status is 200 OK
And success is true with a confirmation message
And the data field is omitted from the response body (non-null serialization)
And password_hash in the database is updated to the Argon2 hash of "New@Secure2!"
And session-B and session-C have revoked_at set in the database
And the jti values of session-B and session-C tokens are written to Redis blacklist
And session-A remains active with revoked_at = NULL
```

> Satisfies: FR-14, FR-15, FR-19, FR-20, FR-21, FR-22

### Scenario 9: Password change rejected — wrong current password

```gherkin
Given an authenticated user with a valid JWT
When the user sends PATCH /api/v1/users/me/password with { "currentPassword": "WrongPassword!", "newPassword": "New@Secure2!" }
Then the response status is 422 Unprocessable Content
And error.type is urn:unihub:error:validation
And password_hash in the database is unchanged
```

> Satisfies: FR-15, FR-16

### Scenario 10: Password change rejected — new password same as current

```gherkin
Given an authenticated user with a valid JWT and current password "Same@Password1"
When the user sends PATCH /api/v1/users/me/password with { "currentPassword": "Same@Password1", "newPassword": "Same@Password1" }
Then the response status is 422 Unprocessable Content
And error.type is urn:unihub:error:validation
And validationErrors notes the new password must differ from the current password
```

> Satisfies: FR-17

### Scenario 11: Password change rejected — weak new password

```gherkin
Given an authenticated user with a valid JWT
When the user sends PATCH /api/v1/users/me/password with { "currentPassword": "Current@Pass1", "newPassword": "weakpass" }
Then the response status is 422 Unprocessable Content
And error.type is urn:unihub:error:validation
And validationErrors lists the password strength constraint violation
```

> Satisfies: FR-18

### Scenario 12: Password change aborted — Redis unavailable

```gherkin
Given an authenticated user with a valid JWT
And the Redis cluster is unreachable
When the user sends PATCH /api/v1/users/me/password with a valid payload
Then the response status is 503 Service Unavailable
And error.type is urn:unihub:error:service-unavailable
And the response includes a Retry-After header
And password_hash in the database is unchanged
```

> Satisfies: FR-23

### Scenario 13: One user cannot retrieve another user's profile via this endpoint

```gherkin
Given authenticated user A with a valid JWT (sub = userId-A)
When user A sends GET /api/v1/users/me
Then the response reflects only userId-A's profile
And there is no mechanism to supply userId-B to retrieve another user's data through this endpoint
```

> Satisfies: FR-02, FR-27

## 8. Out of Scope

- **Account activation / deactivation:** Toggling `is_active` is handled exclusively by `organization-member-management` (F-1.5).
- **Email change:** Not supported anywhere in the current system scope.
- **Role or organization reassignment:** A privileged administrative action outside self-service scope.
- **Avatar image file upload:** Only the `avatarUrl` string is stored here; actual binary file upload to MinIO and URL generation are not part of this feature.
- **Password reset via OTP (unauthenticated):** Handled by `password-reset` (F-1.4) via `POST /api/v1/auth/password/reset`.
- **Temporary password first-login exchange:** Handled by F-1.4 via `POST /api/v1/auth/password/temporary`.
- **Viewing or managing other users' profiles:** Handled by `organization-member-management` (F-1.5).
- **Multi-factor authentication enrollment:** Not in scope for this platform.
- **Profile change history / audit log viewing:** Audit fields are written but not exposed as a queryable history in this feature.

## 9. Linked References

| Document                                          | Section(s) Referenced                                                                                             |
| :------------------------------------------------ | :---------------------------------------------------------------------------------------------------------------- |
| `docs/feature-catalog.md`                         | §1.8 `user-profile-management` — Description, Key Constraints, Module Ownership, DB Tables, ADR Refs              |
| `docs/system-design.md`                           | §5 RBAC Model & Logical Multi-Tenancy; §5 Multi-Layer Enforcement Points; §5 Account Provisioning & Security      |
| `docs/database-schema.md`                         | §Table: `users` (columns, constraints, indexes); §Replication Lag & Read-After-Write Consistency                  |
| `docs/api-standards.md`                           | §HTTP Status Codes; §Mandatory Headers; §Success Response Envelope; §Error Response (RFC 7807); §API Logging      |
| `docs/adrs.md`                                    | ADR-005 (RS256 JWT, JTI revocation); ADR-006 (RBAC); ADR-010 (Fail-closed Redis); ADR-015 (Primary DB routing)    |
| `auth-endpoints.md`                               | §Feature 1.8 — endpoint designs for GET /api/v1/users/me, PATCH /api/v1/users/me, PATCH /api/v1/users/me/password |
| `specs/active-session-management/SPEC.md`         | FR-13–FR-17 (Revoke All Other Sessions semantics reused by password-change side-effect)                           |
| `specs/active-session-management/API_CONTRACT.md` | §DELETE /api/v1/auth/sessions — contract pattern for remote session revocation                                    |
