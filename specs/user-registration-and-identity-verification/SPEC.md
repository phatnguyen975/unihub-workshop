# Spec: User Registration & Identity Verification

> **Status:** APPROVED  
> **Domain:** 1 — Identity & Access Management  
> **Owner/area:** `iam` module (Spring Modulith), `notifications` worker  
> **Last revised:** 2026-09-03 — first version

## 1. Overview

This feature enables a new user with the `STUDENT` role to self-register on the UniHub platform. Upon submitting their registration details, the system creates the account in an **inactive** state (`is_active = false`) and immediately attempts to cross-reference the submitted email against the `student_directory` table to establish a verified identity link (`student_record_id`). A 6-digit numeric One-Time Password (OTP) is generated, its hash stored in the `password_reset_tokens` table with a defined expiry TTL, and a domain event is published so the `notifications` worker delivers the OTP to the user's email. A dedicated endpoint lets the user submit that OTP to activate their account (`is_active = true`). A shared OTP resend endpoint (also used by the password-reset flow in Feature 1.4) allows requesting a fresh OTP when the original has expired or been lost. This is the **only** self-service account-creation path; all other account types (`ORGANIZER`, `STAFF`) are provisioned by an `ORGANIZER` and are out of scope here.

## 2. Actors & Preconditions

| Actor                | Precondition                                                                                                                                   |
| :------------------- | :--------------------------------------------------------------------------------------------------------------------------------------------- |
| Unauthenticated user | No prior account exists for the submitted email address in the `users` table. The platform must be reachable.                                  |
| Unverified user      | A `users` record exists for the email with `is_active = false` and an unexpired, unconsumed OTP exists in `password_reset_tokens`.             |
| Unverified user      | For the OTP resend path: a `users` record with `is_active = false` exists for the submitted email, regardless of whether an OTP is still live. |

## 3. Functional Requirements

### 3.1 Registration

- **FR-01:** The system SHALL create a new `STUDENT` user record when a valid registration payload (email, password, full name) is submitted and no existing user shares the same email.
- **FR-02:** The system SHALL set `is_active = false` and `role = STUDENT` on the newly created user record.
- **FR-03:** The system SHALL check the `student_directory` table for a record whose email (case-insensitive) matches the submitted email. If a match is found, the system SHALL populate `users.student_record_id` with that directory record's `id`. If no match is found, the system SHALL leave `student_record_id` as `NULL`.
- **FR-04:** The system SHALL generate a cryptographically random 6-digit numeric OTP, hash it using HMAC-SHA256 with a server-side secret, and persist the hash in `password_reset_tokens` with an expiry TTL of 15 minutes and a reference to the new user.
- **FR-05:** The system SHALL publish a domain event carrying the user's email address and the plain-text OTP so the `notifications` worker can deliver it via email.
- **FR-06:** The system SHALL reject the registration with an appropriate conflict error when the submitted email already exists in the `users` table, regardless of the existing account's `is_active` status.
- **FR-07:** The system SHALL reject the registration when the submitted email fails standard format validation (RFC 5321, max 254 chars).
- **FR-08:** The system SHALL reject the registration when the submitted password does not meet complexity requirements (min 8 chars, max 255 chars, at least one uppercase letter, one lowercase letter, one digit, one special character).
- **FR-09:** The system SHALL store the user's password as a hash; the plain-text password must never be persisted or included in any response or event payload.
- **FR-10:** The system SHALL set the new user's `organization_id` to match the `student_directory` record if one is found. If no directory match is found, `organization_id` SHALL be `NULL`.

### 3.2 Account Activation via OTP

- **FR-11:** The system SHALL set `is_active = true` on a user record and mark the OTP as consumed (`used_at = now()`) when a valid, unexpired, unconsumed OTP is submitted for that user's email.
- **FR-12:** The system SHALL reject OTP verification when the submitted OTP does not match the active (unexpired, unconsumed) token for the given email.
- **FR-13:** The system SHALL reject OTP verification when the OTP has expired (i.e., `expires_at < now()`).
- **FR-14:** The system SHALL reject OTP verification when the OTP has already been consumed (i.e., `used_at IS NOT NULL`).
- **FR-15:** The system SHALL reject OTP verification when no `users` record exists for the submitted email.
- **FR-16:** The system SHALL reject OTP verification when the user account is already active (`is_active = true`).

### 3.3 OTP Resend

- **FR-17:** The system SHALL invalidate all existing unconsumed OTP tokens for the user (by setting `used_at = now()` on each) before generating a new OTP, when a valid resend request is received.
- **FR-18:** The system SHALL generate, persist, and publish a fresh OTP (same mechanism as FR-04/FR-05) when a resend request is received for an email address that has an inactive account.
- **FR-19:** The system SHALL reject the OTP resend when no inactive user account exists for the submitted email (account not found, or already active).

## 4. Business Rules

- **BR-01:** Email must conform to standard format (RFC 5321) and must not exceed 254 characters. The `users.email` column is `CITEXT` (case-insensitive); uniqueness is enforced at the database level. Source: `docs/database-schema.md §users`.
- **BR-02:** Password must be at minimum 8 characters and at most 255 characters, and must contain at least one uppercase letter, one lowercase letter, one digit, and one special character. Source: `docs/feature-catalog.md §1.1 Key Constraints`.
- **BR-03:** The OTP is a 6-digit numeric string. It is stored only as an HMAC-SHA256 hash in `password_reset_tokens.token_hash`; the plain-text OTP is never persisted. Source: `docs/feature-catalog.md §1.1 Key Constraints`.
- **BR-04:** OTP storage is direct database only — Redis is **not** used for OTP storage in this flow. Source: `docs/feature-catalog.md §1.1 Key Constraints`.
- **BR-05:** The `student_record_id` link is established at registration time by an exact email match (case-insensitive) against `student_directory.email`. A user with `student_record_id = NULL` may log in and update their profile but is **blocked** from registering for any workshop. Source: `docs/database-schema.md §users note`.
- **BR-06:** OTP tokens are single-use. Once consumed (verified), `used_at` is set and the token must never be accepted again. Source: `docs/database-schema.md §password_reset_tokens CAUTION`.
- **BR-07:** The `role` for self-registered users is always `STUDENT`. No other role may be self-assigned. Source: `docs/feature-catalog.md §1.1`.
- **BR-08:** A user account with `is_active = false` must not be able to authenticate (login). The login flow (Feature 1.2) enforces this gate. Source: `docs/feature-catalog.md §1.2`.
- **BR-09:** The password hash algorithm must be Argon2. Source: `docs/database-schema.md §users`.
- **BR-10:** All audit fields (`created_at`, `updated_at`) must be populated on the new `users` and `password_reset_tokens` records. The `created_by` and `updated_by` fields are `NULL` for self-registered accounts (no authenticated actor). Source: `docs/database-schema.md §Overview`.
- **BR-11:** The OTP resend endpoint is shared with the password-reset flow (Feature 1.4). This spec governs only the behaviour when the target account has `is_active = false` (registration context). Feature 1.4 governs the active-account context.

## 5. State Transitions

This feature governs the `is_active` lifecycle of the `users` entity for `STUDENT` accounts.

| From State                     | Event / Trigger                      | To State                       | Valid? | Rejection Behavior (if invalid)                            |
| :----------------------------- | :----------------------------------- | :----------------------------- | :----: | :--------------------------------------------------------- |
| _(not exists)_                 | Successful registration submission   | `is_active = false` (INACTIVE) |   ✅   | —                                                          |
| _(not exists)_                 | Registration with duplicate email    | _(unchanged)_                  |   ❌   | `409 Conflict` — email already registered                  |
| `is_active = false` (INACTIVE) | Valid OTP submission                 | `is_active = true` (ACTIVE)    |   ✅   | —                                                          |
| `is_active = false` (INACTIVE) | Invalid / expired / already-used OTP | `is_active = false` (INACTIVE) |   ❌   | `422 Unprocessable Entity` — OTP invalid or expired        |
| `is_active = false` (INACTIVE) | OTP resend request                   | `is_active = false` (INACTIVE) |   ✅   | — (previous OTPs invalidated, new OTP issued)              |
| `is_active = true` (ACTIVE)    | OTP verification attempt             | _(unchanged)_                  |   ❌   | `409 Conflict` — account already active                    |
| `is_active = true` (ACTIVE)    | OTP resend attempt                   | _(unchanged)_                  |   ❌   | `422 Unprocessable Entity` — no inactive account for email |

OTP token state transitions in `password_reset_tokens`:

| From State            | Event / Trigger              | To State                        | Valid? | Rejection Behavior (if invalid)               |
| :-------------------- | :--------------------------- | :------------------------------ | :----: | :-------------------------------------------- |
| _(not exists)_        | Registration / OTP resend    | `used_at = NULL` (ACTIVE)       |   ✅   | —                                             |
| `used_at = NULL`      | Valid OTP submission         | `used_at = now()` (CONSUMED)    |   ✅   | —                                             |
| `used_at IS NOT NULL` | Any OTP verification attempt | _(unchanged)_                   |   ❌   | `422 Unprocessable Entity` — OTP already used |
| `expires_at < now()`  | OTP verification attempt     | _(unchanged)_                   |   ❌   | `422 Unprocessable Entity` — OTP expired      |
| `used_at = NULL`      | OTP resend                   | `used_at = now()` (INVALIDATED) |   ✅   | —                                             |

## 6. Non-Functional Requirements

- **Performance:** Registration and OTP verification endpoints must complete primary database operations within the system's standard request latency envelope. Email delivery is decoupled via the outbox/event pattern and must not block the HTTP response.
- **Concurrency:** Concurrent registration attempts with the same email must result in exactly one account creation; the database `UNIQUE` constraint on `users.email` (CITEXT) is the ultimate enforcement gate.
- **Idempotency:** Registration is **not** idempotent — a second call with the same email must return `409 Conflict`. OTP resend is safe to repeat — each call invalidates prior tokens and issues a new one. OTP verification is effectively idempotent on the first successful call; subsequent calls on an already-active account return `409 Conflict`.
- **Rate-limiting:** These endpoints are public (unauthenticated) and must be covered by the global Bucket4j rate limiter on the application filter layer to prevent brute-force OTP guessing and registration spam.
- **Security:**
  - Passwords must never appear in logs, events, or responses.
  - OTP plain-text must never be persisted; only the hash is stored in the database.
  - OTP verification error responses must use a uniform message for all failed-validation cases (incorrect OTP, expired OTP, already used OTP) to prevent distinguishing between these states, reducing enumeration risk.
  - The hash algorithm for OTPs in `password_reset_tokens.token_hash` must be HMAC-SHA256 with a server-side secret to prevent offline brute-force attacks.
- **Reliability / Failure modes:** If the domain event publication to the outbox fails, the entire registration transaction must roll back atomically — the user record and OTP token must not be persisted without a corresponding event in the outbox. Spring Modulith's transactional outbox provides this guarantee.
- **Observability:** Each registration attempt, OTP verification attempt, and resend attempt must produce a structured log entry (per `docs/api-standards.md §API Logging`) that includes the correlation ID but **never** includes the email address or any credential.

## 7. Acceptance Criteria

### Scenario 1: Successful registration with matched student directory entry

```gherkin
Given no user account exists for "alice@university.edu"
And a student directory record exists with email "alice@university.edu"
When an unauthenticated client submits a valid registration payload
  (email: "alice@university.edu", password: "Str0ng!Pass", fullName: "Alice Smith")
Then the system creates a new user with role STUDENT and is_active = false
And student_record_id is populated with the matched directory record's id
And a password_reset_tokens record is created with used_at = NULL and a future expires_at
And a domain event carrying the OTP is published to the notifications worker
And the HTTP response is 201 Created containing the new user's id
```

_(Traces to: FR-01, FR-02, FR-03, FR-04, FR-05)_

### Scenario 2: Successful registration without student directory match

```gherkin
Given no user account exists for "bob@external.com"
And no student directory record exists with email "bob@external.com"
When an unauthenticated client submits a valid registration payload
  (email: "bob@external.com", password: "Str0ng!Pass", fullName: "Bob Jones")
Then the system creates a new user with role STUDENT, is_active = false, and student_record_id = NULL
And a domain event carrying the OTP is published
And the HTTP response is 201 Created containing the new user's id
```

_(Traces to: FR-01, FR-02, FR-03, FR-04, FR-05)_

### Scenario 3: Registration rejected — duplicate email

```gherkin
Given a user account already exists for "alice@university.edu"
When an unauthenticated client submits a registration payload with email "alice@university.edu"
Then the system does not create any new user record or OTP token
And the HTTP response is 409 Conflict
```

_(Traces to: FR-06)_

### Scenario 4: Registration rejected — invalid email format

```gherkin
Given no user account exists for "not-an-email"
When an unauthenticated client submits email "not-an-email"
Then the HTTP response is 400 Bad Request with a validation error on the email field
```

_(Traces to: FR-07)_

### Scenario 5: Registration rejected — weak password

```gherkin
Given no user account exists for "carol@university.edu"
When an unauthenticated client submits password "password"
Then the HTTP response is 400 Bad Request with a validation error on the password field
```

_(Traces to: FR-08)_

### Scenario 6: Successful OTP verification (account activation)

```gherkin
Given a user "alice@university.edu" exists with is_active = false
And an unconsumed, unexpired OTP "123456" exists for that user
When the user submits the correct OTP "123456" and email "alice@university.edu" to the account activation endpoint
Then is_active is set to true on the user record
And the OTP token's used_at is set to the current timestamp
And the HTTP response is 200 OK
```

_(Traces to: FR-11)_

### Scenario 7: OTP verification rejected — expired OTP

```gherkin
Given a user "alice@university.edu" exists with is_active = false
And an OTP exists for that user with expires_at in the past and used_at = NULL
When the user submits any OTP value to the account activation endpoint
Then is_active remains false and used_at remains NULL
And the HTTP response is 422 Unprocessable Entity
```

_(Traces to: FR-13)_

### Scenario 8: OTP verification rejected — already consumed OTP

```gherkin
Given a user "alice@university.edu" exists with is_active = false
And an OTP token exists for that user with used_at IS NOT NULL
When the user submits that OTP to the account activation endpoint
Then is_active remains false
And the HTTP response is 422 Unprocessable Entity
```

_(Traces to: FR-14)_

### Scenario 9: OTP verification rejected — incorrect OTP value

```gherkin
Given a user "alice@university.edu" exists with is_active = false
And an unconsumed, unexpired OTP exists for that user
When the user submits an incorrect OTP value "000000"
Then is_active remains false
And the HTTP response is 422 Unprocessable Entity
```

_(Traces to: FR-12)_

### Scenario 10: OTP verification rejected — account already active

```gherkin
Given a user "alice@university.edu" exists with is_active = true
When the user submits any OTP to the account activation endpoint
Then is_active remains true and the HTTP response is 409 Conflict
```

_(Traces to: FR-16)_

### Scenario 11: Successful OTP resend

```gherkin
Given a user "alice@university.edu" exists with is_active = false
And an unconsumed OTP token exists for that user
When the user requests an OTP resend for email "alice@university.edu"
Then the existing unconsumed OTP token's used_at is set to now()
And a new OTP token is created with a fresh expires_at and used_at = NULL
And a new domain event carrying the new OTP is published to the notifications worker
And the HTTP response is 200 OK
```

_(Traces to: FR-17, FR-18)_

### Scenario 12: OTP resend rejected — no account or already active

```gherkin
Given no inactive user account exists for "unknown@university.edu"
When a resend request is submitted for "unknown@university.edu"
Then no OTP is generated or published
And the HTTP response is 422 Unprocessable Entity
```

_(Traces to: FR-19)_

## 8. Out of Scope

- **Login / session issuance after activation** — Activating an account does not issue a JWT or session. The user must separately call the login endpoint (Feature 1.2).
- **Password reset for already-active accounts** — Governed entirely by Feature 1.4. The resend endpoint is shared, but its active-account branch is Feature 1.4's responsibility.
- **`ORGANIZER` and `STAFF` account creation** — Both roles are provisioned by an existing `ORGANIZER` (Feature 1.5). No self-registration path exists for these roles.
- **Email uniqueness validation against the student directory** — The `student_directory` lookup is for identity linking only. A registration is not rejected just because the email is absent from the directory.
- **Workshop registration eligibility enforcement** — The system stores `student_record_id = NULL` for unmatched users, but enforcing the resulting block at the workshop registration endpoint is Feature 3.x's responsibility.
- **Social / SSO login** — No OAuth 2.0 or institutional SSO in scope; all authentication is email/password.
- **Account deactivation** — Disabling active accounts is an organizer-level action (Feature 1.8) and is not part of the self-registration flow.
- **OTP delivery channel other than email** — SMS or push OTP delivery is not in scope.
- **Phone number or avatar during registration** — Only `email`, `password`, and `fullName` are submitted at registration time. Profile enrichment belongs to Feature 1.8.

## 9. Linked References

| Document                  | Section                                                                                     |
| :------------------------ | :------------------------------------------------------------------------------------------ |
| `docs/feature-catalog.md` | §1.1 — feature description, key constraints, ADR refs                                       |
| `docs/system-design.md`   | §5 Account Provisioning & Security — provisioning model, multi-layer enforcement            |
| `docs/database-schema.md` | §`users` — column specs, `student_record_id` note, `is_active` default                      |
| `docs/database-schema.md` | §`password_reset_tokens` — column specs, single-use caution, TTL indexing                   |
| `docs/database-schema.md` | §`student_directory` — column specs, email uniqueness per `organization_id`                 |
| `docs/database-schema.md` | §Overview — audit fields, TTL cleanup policy, no hard-delete                                |
| `docs/adrs.md`            | ADR-005 — JWT RS256 (informs `is_active` gate at login)                                     |
| `docs/adrs.md`            | ADR-006 — RBAC model (role assignment at registration)                                      |
| `docs/adrs.md`            | ADR-027 — Pluggable Notification Architecture (OTP email delivery via event)                |
| `docs/adrs.md`            | ADR-028 — Zero-Trust Provisioning (separates self-registration from delegated provisioning) |
| `docs/api-standards.md`   | §HTTP Status Codes, §Mandatory Headers, §Standard JSON Payloads, §API Logging               |
