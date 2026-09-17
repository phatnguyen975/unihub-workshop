# Spec: User Registration & Identity Verification

> **Status:** APPROVED  
> **Domain:** 1 — Identity & Access Management  
> **Owner/area:** `iam` module (Spring Modulith Core Monolith)  
> **Last revised:** 2026-09-05 — first version

## 1. Overview

This feature enables a new user with the `STUDENT` role to self-register on the UniHub platform. Upon submitting their registration details, the system creates the account in an **inactive** state (`is_active = false`) and cross-references the submitted email against the `student_directory` table (requiring `status = 'ACTIVE'`) to establish a verified identity link (`users.student_record_id`).

A 6-digit numeric One-Time Password (OTP) is generated, its HMAC-SHA256 hash is persisted in the `user_verification_tokens` table with a configurable expiry TTL (default: 15 minutes, configurable via `app.auth.otp.ttl-minutes` or `.env`), and `token_type = 'ACTIVATION_OTP'`. The system publishes an `OtpRequestedEvent` to Spring Modulith's transactional outbox (`event_publication` table) externalized to RabbitMQ (`unihub.events::notifications`) so the `notifications` worker can deliver the code via email (actual worker-side email delivery is decoupled and owned by a separate worker feature).

A dedicated verification endpoint (`POST /api/v1/auth/activate`) allows the user to submit the 6-digit OTP to activate their account (`is_active = true`). A shared OTP resend endpoint (`POST /api/v1/auth/otp/resend`) handles requesting fresh verification codes:

- For inactive accounts (`is_active = false`), it invalidates previous active `ACTIVATION_OTP` tokens and issues a fresh `ACTIVATION_OTP`.
- For active accounts (`is_active = true`), it delegates to the forgot-password flow (Feature 1.4) by invalidating previous active `PASSWORD_RESET_OTP` tokens and issuing a fresh `PASSWORD_RESET_OTP`.

This is the **only** self-service account-creation path; all other account types (`ORGANIZER`, `STAFF`) are provisioned by an `ORGANIZER` (Feature 1.5) and are out of scope here.

## 2. Actors & Preconditions

| Actor                                    | Precondition                                                                                                                                                       |
| :--------------------------------------- | :----------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Unauthenticated user (Guest)**         | No prior account exists for the submitted email address in the `users` table. The platform is reachable.                                                           |
| **Unverified student**                   | A `users` record exists with `is_active = false`, and an unexpired, unconsumed OTP exists in `user_verification_tokens` with `token_type = 'ACTIVATION_OTP'`.      |
| **Unverified student / Registered user** | For the OTP resend endpoint: A `users` record exists for the submitted email (either `is_active = false` for activation or `is_active = true` for password reset). |

## 3. Functional Requirements

### 3.1 Registration

- **FR-01:** The system SHALL create a new `STUDENT` user record when a valid registration payload (email, password, full name) is submitted and no existing user shares the same email (case-insensitive `CITEXT`).
- **FR-02:** The system SHALL set `is_active = false`, `is_temporary_password = false`, and `role = STUDENT` on the newly created user record.
- **FR-03:** The system SHALL check the `student_directory` table for an active record (`status = 'ACTIVE'`) whose email matches the normalized submitted email (case-insensitive).
  - If an active match is found: The system SHALL populate `users.student_record_id` with that directory record's `id`, and `users.organization_id` with `student_directory.organization_id`.
  - If no match is found or the directory record is `INACTIVE`: The system SHALL set `users.student_record_id = NULL` and `users.organization_id = NULL`.
- **FR-04:** The system SHALL generate a cryptographically secure 6-digit numeric OTP (`[0-9]{6}`), hash it using HMAC-SHA256 with a server-side secret key, and persist the hash in `user_verification_tokens` with `token_type = 'ACTIVATION_OTP'`, a configurable TTL (default: 15 minutes), and a foreign key reference to the new user.
- **FR-05:** The system SHALL publish an `OtpRequestedEvent` (carrying `userId`, `email`, plain-text `otp`, and `purpose = 'ACTIVATION'`) to Spring Modulith's transactional outbox table (`event_publication`) externalized to RabbitMQ exchange `unihub.events::notifications`.
- **FR-06:** The system SHALL reject registration with a `409 Conflict` error when the submitted email already exists in the `users` table, regardless of the existing account's `is_active` status.
- **FR-07:** The system SHALL reject registration with a `422 Unprocessable Content` validation error when the submitted payload fails field constraints (e.g. missing fields, blank name, invalid email format per RFC 5321 after trimming surrounding whitespace, or email length > 254 chars).
- **FR-08:** The system SHALL reject registration with a `422 Unprocessable Content` validation error when the password does not satisfy complexity requirements (min 8 chars, max 255 chars, at least one uppercase letter, one lowercase letter, one digit, and one special character).
- **FR-09:** The system SHALL hash user passwords using **Argon2** (`Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()`). Plain-text passwords must never be logged, persisted, or leaked.
- **FR-10:** Registration requests with malformed JSON syntax or unreadable body SHALL be rejected with `400 Bad Request`. Requests with unsupported payload Content-Type SHALL be rejected with `415 Unsupported Media Type`.

### 3.2 Account Activation via OTP

- **FR-11:** The system SHALL set `is_active = true` and mark the OTP token consumed (`used_at = now()`, `updated_at = now()`) in `user_verification_tokens` when a valid, unexpired, unconsumed OTP of `token_type = 'ACTIVATION_OTP'` is submitted for an inactive account.
- **FR-12:** The system SHALL reject OTP verification with `404 Not Found` (`urn:unihub:error:not-found`) when no user record exists for the submitted email address.
- **FR-13:** The system SHALL reject OTP verification with a `422 Unprocessable Content` error (`urn:unihub:error:validation`, including `validationErrors: ["otp: Invalid or expired activation code."]`) using a **uniform error detail message** (`"Invalid or expired activation code."`) whenever:
  - The submitted OTP does not match the stored HMAC-SHA256 hash.
  - The active OTP has expired (`expires_at < now()`).
  - The OTP has already been consumed (`used_at IS NOT NULL`).
- **FR-14:** The system SHALL reject OTP verification with `409 Conflict` (`urn:unihub:error:conflict`) when the user account is already active (`is_active = true`).
- **FR-15:** The system SHALL reject OTP verification with `422 Unprocessable Content` (`urn:unihub:error:validation`, with `validationErrors`) when the request payload fails validation (e.g. invalid email format or OTP string not conforming to `^[0-9]{6}$` after trimming surrounding whitespace). Requests with malformed JSON syntax SHALL be rejected with `400 Bad Request`. Requests with unsupported payload Content-Type SHALL be rejected with `415 Unsupported Media Type`.

### 3.3 Shared OTP Resend

- **FR-16:** The system SHALL provide a shared resend endpoint (`POST /api/v1/auth/otp/resend`) accepting `{ "email": "..." }`.
- **FR-17:** When the submitted email belongs to an inactive account (`users.is_active = false`):
  - The system SHALL invalidate all existing unconsumed OTP tokens for that user **scoped to `token_type = 'ACTIVATION_OTP'`** (by setting `used_at = now()`).
  - The system SHALL generate a fresh 6-digit numeric OTP, persist its HMAC-SHA256 hash in `user_verification_tokens` with `token_type = 'ACTIVATION_OTP'` and the configured TTL.
  - The system SHALL publish an `OtpRequestedEvent` with `purpose = 'ACTIVATION'` to the transactional outbox.
- **FR-18:** When the submitted email belongs to an active account (`users.is_active = true`):
  - The system SHALL invalidate all existing unconsumed OTP tokens for that user **scoped to `token_type = 'PASSWORD_RESET_OTP'`** (by setting `used_at = now()`).
  - The system SHALL generate a fresh 6-digit numeric OTP, persist its HMAC-SHA256 hash in `user_verification_tokens` with `token_type = 'PASSWORD_RESET_OTP'` and the configured TTL.
  - The system SHALL publish an `OtpRequestedEvent` with `purpose = 'PASSWORD_RESET'` to the transactional outbox.
- **FR-19:** The system SHALL reject the resend request with `404 Not Found` (`urn:unihub:error:not-found`) when no user account exists with the submitted email.
- **FR-20:** The system SHALL reject the resend request with `422 Unprocessable Content` (`urn:unihub:error:validation`) when the email is missing or malformed (after trimming surrounding whitespace). Requests with malformed JSON syntax SHALL be rejected with `400 Bad Request`. Requests with unsupported payload Content-Type SHALL be rejected with `415 Unsupported Media Type`.

## 4. Business Rules

- **BR-01:** Email must conform to RFC 5321 and must not exceed 254 characters. Stored in PostgreSQL as `CITEXT` (case-insensitive); uniqueness is strictly enforced by `uq_users_email`.
- **BR-02:** Passwords must be at least 8 and at most 255 characters, containing at least one uppercase letter, one lowercase letter, one digit, and one special character.
- **BR-03:** Passwords must be hashed using **Argon2** (`Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()`). BCrypt is strictly prohibited.
- **BR-04:** The OTP is a 6-digit numeric string (`^[0-9]{6}$`). The plain-text OTP is never stored in the database; only its HMAC-SHA256 hash is persisted in `user_verification_tokens.token_hash`.
- **BR-05:** OTP storage is direct PostgreSQL database only — Redis is **not** used for OTP storage in this flow.
- **BR-06:** The OTP TTL defaults to 15 minutes, but MUST be configurable via environment variables / application configuration (`app.auth.otp.ttl-minutes`).
- **BR-07:** The `student_record_id` link is established at registration by an exact email match against `student_directory.email` where `status = 'ACTIVE'`. Users with `student_record_id = NULL` can log in and update their profile, but are blocked from registering for workshops.
- **BR-08:** OTP tokens are single-use. Once verified, `used_at` is set to `now()`. Invalidation and lookups MUST always be scoped by `token_type` (`ACTIVATION_OTP` vs `PASSWORD_RESET_OTP`) to prevent cross-flow token conflicts.
- **BR-09:** The role for self-registered users is always `STUDENT`. No other role can be self-assigned.
- **BR-10:** A user account with `is_active = false` cannot authenticate (login). The login flow (Feature 1.2) enforces this gate.
- **BR-11:** All audit fields (`created_at`, `updated_at`) must be populated. The `created_by` and `updated_by` fields are `NULL` for self-registered users.

## 5. State Transitions

### User Account State Transitions (`users.is_active`):

| From State          | Event / Trigger                      | To State            | Valid? | HTTP Status & Error Code                                    |
| :------------------ | :----------------------------------- | :------------------ | :----: | :---------------------------------------------------------- |
| _(None)_            | Valid registration submitted         | `is_active = false` |   ✅   | `201 Created`                                               |
| _(None)_            | Registration with duplicate email    | _(Unchanged)_       |   ❌   | `409 Conflict` (`urn:unihub:error:conflict`)                |
| `is_active = false` | Valid `ACTIVATION_OTP` submitted     | `is_active = true`  |   ✅   | `200 OK`                                                    |
| `is_active = false` | Invalid / expired / consumed OTP     | `is_active = false` |   ❌   | `422 Unprocessable Content` (`urn:unihub:error:validation`) |
| `is_active = false` | Resend OTP request                   | `is_active = false` |   ✅   | `200 OK` (New `ACTIVATION_OTP` issued)                      |
| `is_active = true`  | Activation attempt on active account | `is_active = true`  |   ❌   | `409 Conflict` (`urn:unihub:error:conflict`)                |
| `is_active = true`  | Resend OTP request                   | `is_active = true`  |   ✅   | `200 OK` (New `PASSWORD_RESET_OTP` issued)                  |

### Verification Token State Transitions (`user_verification_tokens`):

| From State            | Event / Trigger                  | To State                        | Valid? | Rejection Behavior                                          |
| :-------------------- | :------------------------------- | :------------------------------ | :----: | :---------------------------------------------------------- |
| _(None)_              | Registration / Resend request    | `used_at = NULL` (ACTIVE)       |   ✅   | —                                                           |
| `used_at = NULL`      | Valid OTP submission             | `used_at = now()` (CONSUMED)    |   ✅   | —                                                           |
| `used_at IS NOT NULL` | Any OTP verification attempt     | _(Unchanged)_                   |   ❌   | `422 Unprocessable Content` (`urn:unihub:error:validation`) |
| `expires_at < now()`  | Expired OTP verification attempt | _(Unchanged)_                   |   ❌   | `422 Unprocessable Content` (`urn:unihub:error:validation`) |
| `used_at = NULL`      | OTP resend for same `token_type` | `used_at = now()` (INVALIDATED) |   ✅   | —                                                           |

## 6. Non-Functional Requirements

- **Performance:** Registration, OTP activation, and resend operations must complete primary database writes within standard latency (< 100ms). Asynchronous email notification is offloaded to RabbitMQ via the outbox pattern and does not block HTTP responses.
- **Concurrency & Atomicity:** Database unique constraint on `users.email` (`CITEXT`) protects against race conditions during concurrent registrations. If outbox event publication fails, the entire transaction rolls back atomically.
- **Rate Limiting:** All three endpoints are public (unauthenticated) and must be protected by the dedicated Bucket4j `auth-tier` Token Bucket rate limiter (default: capacity 20 tokens, refill 20 tokens per 60s per client IP, returning `429 Too Many Requests` with `X-RateLimit-*` headers when exceeded) to prevent brute-forcing OTPs, Argon2 CPU exhaustion, and spam registrations.
- **Security & Anti-Enumeration:**
  - Passwords and plain-text OTPs must never appear in application logs or HTTP responses.
  - OTP verification error responses use a uniform message (`"Invalid or expired activation code."`) across all OTP-level failure causes (wrong code, expired, already consumed) to prevent timing attacks and OTP brute-forcing. User-not-found on `/activate` intentionally returns `404 Not Found` to surface a clear error path without leaking OTP state.
  - OTP hashing uses HMAC-SHA256 with a server-side secret key.
- **Observability:** Structured JSON logging compatible with ELK/Prometheus emitting `traceId`, `http.method`, `http.uri`, `http.status`, and `duration_ms` without logging sensitive credentials or emails.

## 7. Acceptance Criteria

### Scenario 1: Successful registration with matched active student directory entry

```gherkin
Given no user account exists for "student.alice@student.hcmus.edu.vn"
And an active student directory record exists with email "student.alice@student.hcmus.edu.vn"
When an unauthenticated client submits a valid registration payload
  (email: "student.alice@student.hcmus.edu.vn", password: "StrongPassword123!", fullName: "Alice Nguyen")
Then the system creates a new user with role STUDENT, is_active = false, is_temporary_password = false
And password_hash is an Argon2 encoded string
And student_record_id is linked to the matched directory record's id
And organization_id is set to the student directory record's organization_id
And a user_verification_tokens record is created with token_type = 'ACTIVATION_OTP', used_at = NULL, and expires_at = now() + configured TTL
And an OtpRequestedEvent (purpose: 'ACTIVATION') is persisted to event_publication for RabbitMQ externalization
And the HTTP response is 201 Created with JSON envelope containing data.userId
```

### Scenario 2: Successful registration without student directory match

```gherkin
Given no user account exists for "guest.bob@gmail.com"
And no student directory record exists for "guest.bob@gmail.com"
When an unauthenticated client submits a valid registration payload
  (email: "guest.bob@gmail.com", password: "StrongPassword123!", fullName: "Bob Jones")
Then the system creates a new user with role STUDENT, is_active = false, student_record_id = NULL, and organization_id = NULL
And a user_verification_tokens record is created with token_type = 'ACTIVATION_OTP'
And the HTTP response is 201 Created containing data.userId
```

### Scenario 3: Registration rejected — duplicate email

```gherkin
Given a user account already exists for "student.alice@student.hcmus.edu.vn"
When an unauthenticated client submits registration with email "student.alice@student.hcmus.edu.vn"
Then no new user or verification token is created
And the HTTP response is 409 Conflict with error.type = "urn:unihub:error:conflict"
```

### Scenario 4: Registration rejected — weak password

```gherkin
Given no user account exists for "student.carol@student.hcmus.edu.vn"
When an unauthenticated client submits registration with password "weakpass"
Then the HTTP response is 422 Unprocessable Content with error.type = "urn:unihub:error:validation" and validationErrors mentioning password
```

### Scenario 5: Successful account activation via OTP

```gherkin
Given a user "student.alice@student.hcmus.edu.vn" exists with is_active = false
And an unconsumed, unexpired user_verification_tokens record exists with token_type = 'ACTIVATION_OTP' and OTP "849201"
When the user submits email "student.alice@student.hcmus.edu.vn" and otp "849201" to /api/v1/auth/activate
Then the user record has is_active set to true
And the token's used_at is set to current timestamp
And the HTTP response is 200 OK with message "Account activated successfully. You may now log in."
```

### Scenario 6: Activation rejected — invalid, expired, or already consumed OTP

```gherkin
Given a user "student.alice@student.hcmus.edu.vn" exists with is_active = false
When the user submits an incorrect, expired, or previously consumed OTP to /api/v1/auth/activate
Then is_active remains false
And the HTTP response is 422 Unprocessable Content with error.type = "urn:unihub:error:validation", uniform detail "Invalid or expired activation code.", and validationErrors containing "otp: Invalid or expired activation code."
```

### Scenario 7: Activation rejected — account already active

```gherkin
Given a user "student.alice@student.hcmus.edu.vn" exists with is_active = true
When the user submits any OTP to /api/v1/auth/activate
Then the HTTP response is 409 Conflict with error.type = "urn:unihub:error:conflict" and detail "Account is already active."
```

### Scenario 8: Successful OTP resend for inactive account

```gherkin
Given a user "student.alice@student.hcmus.edu.vn" exists with is_active = false
And an unconsumed user_verification_tokens record exists with token_type = 'ACTIVATION_OTP'
When the user requests OTP resend to /api/v1/auth/otp/resend with email "student.alice@student.hcmus.edu.vn"
Then the previous unconsumed ACTIVATION_OTP token has used_at set to now()
And a new user_verification_tokens record is created with token_type = 'ACTIVATION_OTP' and used_at = NULL
And a new OtpRequestedEvent (purpose: 'ACTIVATION') is published to event_publication
And the HTTP response is 200 OK with message "A verification code has been sent to your email."
```

### Scenario 9: Successful OTP resend for active account (Delegated to Password Reset)

```gherkin
Given a user "student.alice@student.hcmus.edu.vn" exists with is_active = true
When the user requests OTP resend to /api/v1/auth/otp/resend with email "student.alice@student.hcmus.edu.vn"
Then any previous unconsumed PASSWORD_RESET_OTP token has used_at set to now()
And a new user_verification_tokens record is created with token_type = 'PASSWORD_RESET_OTP' and used_at = NULL
And a new OtpRequestedEvent (purpose: 'PASSWORD_RESET') is published to event_publication
And the HTTP response is 200 OK with message "A verification code has been sent to your email."
```

### Scenario 10: OTP resend rejected — user not found

```gherkin
Given no user account exists for "unknown@university.edu"
When a resend request is submitted for "unknown@university.edu"
Then no OTP is generated or published
And the HTTP response is 404 Not Found with error.type = "urn:unihub:error:not-found"
```

## 8. Out of Scope

- **Worker-Side Notification Handling** — Email rendering and SMTP delivery in the `notifications` worker is owned by a separate notifications feature. Feature 1.1 only publishes `OtpRequestedEvent` to the outbox for RabbitMQ externalization.
- **Login / session issuance after activation** — Activating an account does not issue a JWT or session. The user separately logs in via `POST /api/v1/auth/login` (Feature 1.2).
- **Password Reset Execution** — Actually verifying the reset OTP and updating the password is in Feature 1.4 (`POST /api/v1/auth/password/reset`).
- **`ORGANIZER` and `STAFF` account creation** — Provisioned by an `ORGANIZER` in Feature 1.5.
- **Workshop registration eligibility enforcement** — Blocking users with `student_record_id = NULL` from registering for workshops is enforced by Feature 3.x.

## 9. Linked References

| Document                  | Section                                                                                                               |
| :------------------------ | :-------------------------------------------------------------------------------------------------------------------- |
| `docs/feature-catalog.md` | §1.1 — feature description, key constraints, DB tables, ADR refs                                                      |
| `docs/system-design.md`   | §5 Account Provisioning & Security (Self-Service Student Registration & OTP Activation)                               |
| `docs/database-schema.md` | §`users`, §`user_verification_tokens`, §`student_directory`                                                           |
| `docs/api-standards.md`   | §HTTP Status Codes, §Mandatory Headers, §Standard JSON Payloads, §API Logging                                         |
| `docs/adrs.md`            | ADR-005 (JWT RS256), ADR-006 (RBAC), ADR-027 (Pluggable Notification Architecture), ADR-028 (Zero-Trust Provisioning) |
