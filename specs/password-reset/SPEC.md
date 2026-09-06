# Spec: Password Reset

> **Status:** APPROVED  
> **Domain:** 1 — Identity & Access Management  
> **Module Owner:** `iam` module; `notifications` worker; `shared/security`  
> **Last Revised:** 2026-09-06 — first version

## 1. Overview

This feature provides two dedicated password modification flows for unauthenticated users across all roles (`STUDENT`, `ORGANIZER`, `STAFF`):

1. **Forgot Password Flow (Single Atomic Request):** An active user who has forgotten their password submits their email via the shared OTP resend endpoint (`POST /api/v1/auth/otp/resend`). The system generates a 6-digit numeric OTP, stores its SHA-256 hash in `user_verification_tokens` (`token_type = 'PASSWORD_RESET_OTP'`) with a 15-minute TTL, and sends it asynchronously via email. The user then submits `{ email, otp, newPassword }` directly in a single atomic request to `POST /api/v1/auth/password/reset`. This endpoint atomizes verification and credential update: validates the OTP, marks it consumed (`used_at = now()`), updates the Argon2id password hash, revokes all active refresh tokens in PostgreSQL, and blacklists active access token JTIs in Redis. The user then logs in with their new password.
2. **First-Login Temporary Password Flow (Zero-Trust Session Issuance):** Staff and Organizer accounts provisioned by administrators (`POST /api/v1/organizations/users` under ADR-028) or seeded root organizers receive an auto-generated temporary password via email with `is_temporary_password = true`. When attempting to log in, `POST /api/v1/auth/login` returns `403 Forbidden` with error code `urn:unihub:error:temporary-password`. The client redirects the user to the "First-Time Password Setup" screen where the user submits `{ email, currentPassword, newPassword }` directly to `POST /api/v1/auth/password/temporary`. Upon verifying `currentPassword`, the system updates the password hash, flips `is_temporary_password = false`, records `last_login_at = now()`, and immediately issues a full authentication session (JWT RS256 access token, refresh token, and user profile data) so the user transitions seamlessly to their dashboard without an extra login step.

## 2. Actors & Preconditions

| Actor                                       | Precondition                                                                                                                                                            |
| :------------------------------------------ | :---------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Unauthenticated User (Forgot Password)**  | Holds an active platform account of any role (`STUDENT`, `ORGANIZER`, `STAFF`) with access to the registered email address.                                             |
| **User Holding a Reset OTP**                | Has received a 6-digit numeric `PASSWORD_RESET_OTP` via email triggered by `POST /api/v1/auth/otp/resend`.                                                              |
| **Temporary-Password Holder (First Login)** | Holds a provisioned `STAFF` or `ORGANIZER` account with `is_active = true` and `is_temporary_password = true`, having received initial temporary credentials via email. |
| **Notifications Service**                   | Asynchronously consumes `OtpRequestedEvent` (purpose: `PASSWORD_RESET`) published via Transactional Outbox to deliver OTP emails.                                       |

## 3. Functional Requirements

### 3.1 Reset OTP Request & Reissue (`POST /api/v1/auth/otp/resend`)

- **FR-01:** The system SHALL issue a 6-digit numeric password-reset OTP when an unauthenticated user submits an email address belonging to an active account (`is_active = true`) through the shared `POST /api/v1/auth/otp/resend` endpoint.
- **FR-02:** The system SHALL persist the SHA-256 hash of the generated OTP in `user_verification_tokens` with `token_type = 'PASSWORD_RESET_OTP'` and a 15-minute TTL.
- **FR-03:** The system SHALL invalidate all prior unconsumed `PASSWORD_RESET_OTP` tokens for that account (`used_at = now()`) before generating a replacement OTP.
- **FR-04:** The system SHALL publish an `OtpRequestedEvent` (with purpose `PASSWORD_RESET`) to the Transactional Outbox in the same database transaction.
- **FR-05:** The system SHALL return `404 Not Found` (`urn:unihub:error:not-found`) if no user exists for the submitted email.

### 3.2 Forgot Password Reset (`POST /api/v1/auth/password/reset`)

- **FR-06:** The system SHALL verify the submitted OTP and reset the user's password in a single atomic database transaction when receiving `{ email, otp, newPassword }`.
- **FR-07:** The system SHALL validate the OTP by checking:
  1. A `user_verification_tokens` record exists for the user with `token_type = 'PASSWORD_RESET_OTP'`.
  2. The record is not consumed (`used_at IS NULL`).
  3. The record is not expired (`expires_at > now()`).
  4. The SHA-256 hash of the submitted OTP matches `token_hash`.
- **FR-08:** The system SHALL reject the request with `422 Unprocessable Content` (`urn:unihub:error:validation` with `validationErrors: ["otp: Invalid or expired verification code."]`) if the user does not exist, or the OTP is incorrect, expired, or already consumed, without updating credentials or sessions.
- **FR-09:** The system SHALL mark the verified OTP record as consumed (`used_at = now()`).
- **FR-10:** The system SHALL hash `newPassword` using Argon2id and update `users.password_hash` and `users.updated_at = now()`.
- **FR-11:** If the account has `is_temporary_password = true`, the system SHALL set `is_temporary_password = false`.
- **FR-12:** The system SHALL revoke all active refresh tokens belonging to the account in PostgreSQL (`revoked_at = now()`).
- **FR-13:** The system SHALL blacklist all active access token JTIs belonging to the account in Redis with their remaining TTL.
- **FR-14:** The system SHALL return `200 OK` with a friendly confirmation message prompting the user to sign in with their new password. It SHALL NOT issue an authenticated session.

### 3.3 First-Login Temporary Password Exchange (`POST /api/v1/auth/password/temporary`)

- **FR-15:** The system SHALL accept `{ email, currentPassword, newPassword }` from an unauthenticated user on their first login without requiring an OTP.
- **FR-16:** The system SHALL verify that the user exists, is active (`is_active = true`), and has `is_temporary_password = true`.
- **FR-17:** The system SHALL reject the request with `401 Unauthorized` (`urn:unihub:error:unauthorized`) if the user does not exist, is inactive, or `currentPassword` does not match the stored Argon2id password hash.
- **FR-18:** The system SHALL reject the request with `400 Bad Request` (`urn:unihub:error:bad-request`) if the account exists and password matches but `is_temporary_password = false`.
- **FR-19:** The system SHALL reject the request with `422 Unprocessable Content` (`urn:unihub:error:validation` with `validationErrors: ["newPassword: New password must be different from the temporary password."]`) if `newPassword` equals `currentPassword`.
- **FR-20:** The system SHALL update `users.password_hash` with the Argon2id hash of `newPassword`, set `users.is_temporary_password = false`, update `users.last_login_at = now()`, and update `users.updated_at = now()`.
- **FR-21:** The system SHALL immediately generate and issue an RS256-signed JWT access token (3600s TTL), persist a newly issued refresh token in `refresh_tokens`, and return a `200 OK` response containing `accessToken`, `refreshToken`, `tokenType: "Bearer"`, `expiresIn: 3600`, and full `user` profile data.

### 3.4 Reliability & Natural State Invalidation

- **FR-22:** The password reset flow SHALL enforce strict single-use OTP consumption: once verified and consumed (`used_at = now()`), subsequent submissions with the same OTP SHALL be rejected with `422 Unprocessable Content` (`urn:unihub:error:validation`).
- **FR-23:** The temporary password exchange flow SHALL enforce single-use state transition: once exchanged, `is_temporary_password` is set to `false`, and subsequent requests with the old temporary password SHALL be rejected with `400 Bad Request` (`urn:unihub:error:bad-request`).

## 4. Business Rules

- **BR-01 (OTP Constraints):** A password-reset OTP consists of exactly 6 numeric digits (`^[0-9]{6}$`) and has a lifetime of 15 minutes. It is stored exclusively as a SHA-256 hash in `user_verification_tokens` under `token_type = 'PASSWORD_RESET_OTP'`.
- **BR-02 (Password Policy):** Any new password submitted must be between 8 and 255 characters inclusive, containing at least one uppercase letter, one lowercase letter, one digit, and one special character (`[!@#$%^&*(),.?":{}|<>]`).
- **BR-03 (Single-Use OTP):** A reset OTP may be consumed at most once. Once `used_at` is populated, subsequent verification attempts are rejected.
- **BR-04 (Global Session Invalidation on Reset):** Completing a forgot password reset immediately revokes all active refresh tokens in PostgreSQL and blacklists active access token JTIs in Redis, terminating all sessions across web and mobile clients.
- **BR-05 (Zero-Trust Transition):** Accounts provisioned under ADR-028 are locked behind `is_temporary_password = true`. Exchanging the temporary password removes this flag and records `last_login_at = now()`.
- **BR-06 (No Session Interruption for First-Login):** Successfully changing a temporary password issues a full session immediately. The user is never redirected back to the login screen.
- **BR-07 (Temporary Password Non-Reuse):** The new permanent password chosen by the user must differ from the temporary password.
- **BR-08 (Shared OTP Resend Routing):** `POST /api/v1/auth/otp/resend` routes dynamically:
  - If `user.is_active == false` -> issues `ACTIVATION_OTP` (Feature 1.1).
  - If `user.is_active == true` -> issues `PASSWORD_RESET_OTP` (Feature 1.4).
  - Prior token invalidation is strictly scoped by `token_type`.
- **BR-09 (Asynchronous Email Delivery):** Email notifications are dispatched out-of-band by the `notifications` worker via RabbitMQ after the transaction commits. Failure to send an email does not roll back token generation.
- **BR-10 (Uniform Error Protection):** In `POST /api/v1/auth/password/reset`, invalid OTP, expired OTP, already used OTP, or non-existent user all return a uniform `422 Unprocessable Content` response with `validationErrors: ["otp: Invalid or expired verification code."]`, preventing account enumeration and timing attacks.

## 5. State Transitions

### 5.1 Password-Reset OTP Lifecycle (`user_verification_tokens`)

| From State | Event / Trigger                                     | To State                                                      | Valid? | Rejection Outcome     |
| :--------- | :-------------------------------------------------- | :------------------------------------------------------------ | :----: | :-------------------- |
| _None_     | Active user calls `POST /api/v1/auth/otp/resend`    | `ACTIVE` (`used_at IS NULL`, unexpired)                       |  Yes   | N/A                   |
| `ACTIVE`   | Resend called again for same email                  | `INVALIDATED` (`used_at = now()`); new token becomes `ACTIVE` |  Yes   | N/A                   |
| `ACTIVE`   | Valid reset call `POST /api/v1/auth/password/reset` | `CONSUMED` (`used_at = now()`)                                |  Yes   | N/A                   |
| `ACTIVE`   | Incorrect OTP submitted                             | `ACTIVE` (unchanged)                                          |   No   | 422 Uniform OTP error |
| `EXPIRED`  | Reset attempt after 15 minutes                      | `EXPIRED` (unchanged)                                         |   No   | 422 Uniform OTP error |
| `CONSUMED` | Reset attempt with already-used OTP                 | `CONSUMED` (unchanged)                                        |   No   | 422 Uniform OTP error |

### 5.2 Account Credential & Flag Lifecycle (`users`)

| From State                                    | Event / Trigger                                              | To State                                                                      | Side Effects                                                    |
| :-------------------------------------------- | :----------------------------------------------------------- | :---------------------------------------------------------------------------- | :-------------------------------------------------------------- |
| `is_active=true, is_temporary_password=false` | Successful forgot password reset (`/password/reset`)         | `password_hash` updated                                                       | Revoke all refresh tokens in DB; Blacklist JTIs in Redis        |
| `is_active=true, is_temporary_password=true`  | Successful forgot password reset (`/password/reset`)         | `password_hash` updated, `is_temporary_password=false`                        | Revoke all refresh tokens in DB; Blacklist JTIs in Redis        |
| `is_active=true, is_temporary_password=true`  | Successful temporary password change (`/password/temporary`) | `password_hash` updated, `is_temporary_password=false`, `last_login_at=now()` | Issue new JWT access token + refresh token; return user profile |

## 6. Non-Functional Requirements

- **Primary Database Routing:** All reads and writes for password verification, token updates, and session revocation MUST execute against the primary PostgreSQL database to avoid replication lag anomalies (ADR-015).
- **Concurrency & Atomicity:** Verification and password update in `POST /api/v1/auth/password/reset` and `POST /api/v1/auth/password/temporary` MUST occur in a single `@Transactional` block with optimistic locking or row locking where necessary to prevent double consumption under concurrent requests.
- **Rate Limiting:** Public endpoints (`/password/reset`, `/password/temporary`, `/otp/resend`) MUST be governed by Redis Token Bucket rate limiting (`shared/ratelimit`, unauthenticated public tier: 60 req/min) with `429 Too Many Requests` and `Retry-After` header.
- **Password Security:** Password hashing MUST use Argon2id with parameters conformant with ADR-005. Raw passwords MUST NEVER be logged, cached, or returned in responses.
- **Observability:** Emit structured logs with `traceId` and masked user identity. Publish Micrometer metrics for password reset counts and failure rates.

## 7. Acceptance Criteria

### Scenario 1: Active user requests a password-reset OTP via shared resend

```gherkin
Given an active user account exists for "student.alice@university.edu"
When the user submits a POST request to "/api/v1/auth/otp/resend" with email "student.alice@university.edu"
Then a 6-digit numeric OTP is generated and stored as SHA-256 in user_verification_tokens with token_type "PASSWORD_RESET_OTP"
And an OtpRequestedEvent with purpose "PASSWORD_RESET" is published to the Transactional Outbox
And the HTTP response status is 200 OK with message "A verification code has been sent to your email."
```

### Scenario 2: Active user resends OTP, invalidating previous OTP

```gherkin
Given an active user has an unconsumed "PASSWORD_RESET_OTP"
When the user requests an OTP resend via "/api/v1/auth/otp/resend"
Then the existing OTP record is marked consumed/invalidated
And exactly one new "PASSWORD_RESET_OTP" is created with a 15-minute TTL
And a new OtpRequestedEvent is published
And the HTTP response status is 200 OK
```

### Scenario 3: Successful single-step password reset with valid OTP

```gherkin
Given an active user has an unexpired, unconsumed "PASSWORD_RESET_OTP" with code "849201"
And the user has active refresh tokens in the database
When the user submits a POST request to "/api/v1/auth/password/reset" with:
  | email       | "student.alice@university.edu" |
  | otp         | "849201"                       |
  | newPassword | "NewSecurePass2026!"           |
Then the OTP record is marked consumed (used_at = now())
And the user's password_hash is updated with the Argon2id hash of "NewSecurePass2026!"
And all active refresh tokens for the user are marked revoked in the database
And active access token JTIs for the user are added to the Redis blacklist
And the HTTP response status is 200 OK with message "Password reset successfully. Please sign in with your new password."
And no authentication tokens are issued in the response
```

### Scenario 4: Password reset fails with invalid, expired, or consumed OTP

```gherkin
Given an active user account exists
When the user submits a POST request to "/api/v1/auth/password/reset" with an incorrect, expired, or consumed OTP
Then no credentials or sessions are altered
And the HTTP response status is 422 Unprocessable Content
And the response body contains error type "urn:unihub:error:validation"
And "validationErrors" contains "otp: Invalid or expired verification code."
```

### Scenario 5: Password reset fails with weak password

```gherkin
Given an active user has a valid "PASSWORD_RESET_OTP"
When the user submits a POST request to "/api/v1/auth/password/reset" with newPassword "weak"
Then the request is rejected with 422 Unprocessable Content
And "validationErrors" specifies password policy violations
And the OTP remains unconsumed
```

### Scenario 6: First-login staff exchanges temporary password for permanent password

```gherkin
Given a user "staff.bob@unihub.edu" exists with is_active = true and is_temporary_password = true
And their temporary password is "TempPassword123!"
When the user submits a POST request to "/api/v1/auth/password/temporary" with:
  | email           | "staff.bob@unihub.edu"        |
  | currentPassword | "TempPassword123!"            |
  | newPassword     | "MyOfficialSecurePassword2026!" |
Then the user's password_hash is updated with the hash of "MyOfficialSecurePassword2026!"
And is_temporary_password is set to false
And last_login_at is updated to the current timestamp
And a new RS256 JWT access token and refresh token are generated
And the HTTP response status is 200 OK
And the response data contains "accessToken", "refreshToken", "tokenType", "expiresIn", and "user" profile
```

### Scenario 7: Temporary password exchange fails when current password is incorrect

```gherkin
Given a user has is_temporary_password = true
When the user submits a POST request to "/api/v1/auth/password/temporary" with an incorrect currentPassword
Then the request is rejected with 401 Unauthorized
And error type is "urn:unihub:error:unauthorized"
And no credentials or account flags are updated
```

### Scenario 8: Temporary password exchange fails when account does not have a temporary password

```gherkin
Given a user exists with is_active = true and is_temporary_password = false
When the user submits a POST request to "/api/v1/auth/password/temporary" with their correct current password
Then the request is rejected with 400 Bad Request
And error type is "urn:unihub:error:bad-request"
And error message indicates the account does not have a temporary password
```

### Scenario 9: Temporary password exchange fails when new password matches current password

```gherkin
Given a user has is_temporary_password = true and currentPassword is "TempPassword123!"
When the user submits "/api/v1/auth/password/temporary" with newPassword equal to "TempPassword123!"
Then the request is rejected with 422 Unprocessable Content
And "validationErrors" contains "newPassword: New password must be different from the temporary password."
```

### Scenario 10: Retried submission with already-consumed reset OTP

```gherkin
Given a password reset request has succeeded and consumed the OTP
When the client retries the request with the same OTP
Then the system rejects the request with 422 Unprocessable Content
And error.type is "urn:unihub:error:validation"
And "validationErrors" contains "otp: Invalid or expired verification code."
```

## 8. Out of Scope

- In-session authenticated password change for logged-in users knowing their old password (`PATCH /api/v1/users/me/password` — Feature 1.8).
- Initial account registration and student activation (`POST /api/v1/auth/register`, `POST /api/v1/auth/activate` — Feature 1.1).
- Account provisioning and temporary password creation by Organizers (`POST /api/v1/organizations/users` — Feature 1.5).
- Captcha or biometric verification.

## 9. Linked References

| Document                  | Relevant Section                                                                                                                  |
| :------------------------ | :-------------------------------------------------------------------------------------------------------------------------------- |
| `docs/feature-catalog.md` | Section 1.4 `password-reset`                                                                                                      |
| `auth-flows.md`           | Feature 1.4 (§8 `POST /api/v1/auth/password/reset`, §9 `POST /api/v1/auth/password/temporary`, §3 `POST /api/v1/auth/otp/resend`) |
| `docs/system-design.md`   | Section 5 Account Provisioning & Security, Self-Service Password Reset, Delegated Creation & Zero-Trust                           |
| `docs/database-schema.md` | `users`, `user_verification_tokens`, `refresh_tokens`                                                                             |
| `docs/api-standards.md`   | HTTP status codes, standard envelope `ApiResponse`, ProblemDetail RFC 7807, validation format                                     |
| `docs/adrs.md`            | ADR-005 (JWT RS256 + Redis JTI), ADR-015 (Replication Lag Routing), ADR-028 (Zero-Trust Provisioning)                             |
