# API Contract: User Registration & Identity Verification

> **Status:** APPROVED  
> **Spec:** `specs/user-registration-and-identity-verification/SPEC.md`  
> **Last revised:** 2026-09-03

## 1. Overview

This contract defines the three REST endpoints introduced by Feature 1.1 (`user-registration-and-identity-verification`) within the `iam` module. The endpoints live under the `/api/v1/auth` resource prefix, consistent with the existing `AuthController` pattern established in Features 1.2 and 1.3.

The three endpoints are:

1. `POST /api/v1/auth/register` — self-registration for `STUDENT` accounts.
2. `POST /api/v1/auth/activate` — OTP-based account activation (registration verification flow only).
3. `POST /api/v1/auth/otp/resend` — shared OTP resend (registration context: inactive accounts; password-reset context: Feature 1.4).

## 2. Global Standards Applied

- **URI structure / versioning:** `/api/v1/...` URI path versioning (per `docs/api-standards.md §Versioning Strategy`).
- **Success response shape:** Standard JSON envelope with `success`, `message`, `data`, and `metadata` fields. `null` values are omitted (`@JsonInclude(NON_NULL)`). (per `docs/api-standards.md §Success Response Envelope`).
- **Error response format:** RFC 7807 Problem Details embedded in the standard error envelope (`success: false`, `error` object with `type`, `title`, `status`, `detail`, `instance`; `data` absent). Content-Type: `application/problem+json`. (per `docs/api-standards.md §Error Response`).
- **Auth header convention:** `Authorization: Bearer <token>` for authenticated endpoints. All three endpoints in this contract are **PUBLIC** (unauthenticated).
- **Idempotency-key convention:** The `Idempotency-Key` header is required on business-critical mutating POST endpoints. See per-endpoint decisions below.
- **Date/time formatting:** ISO 8601 UTC with trailing `Z` (e.g., `2026-05-03T12:00:00Z`) (per `docs/api-standards.md §Date/Time & Time Zone`).
- **Request field casing:** camelCase for all JSON request and response body fields.
- **Pagination convention:** Not applicable — no collection endpoints in this contract.

## 3. Endpoints

### POST /api/v1/auth/register

**Description:** Creates a new `STUDENT` account in inactive state, links the identity to the student directory if a matching record exists, generates an OTP, and triggers an email notification for account activation.  
**Required Role / Auth:** PUBLIC — no `Authorization` header required.  
**Idempotency Requirement:** Not Required — a duplicate call with the same email is rejected with `409 Conflict` by the unique constraint on `users.email`, making the endpoint naturally safe without an idempotency key. Retries after a `409` must not be replayed; the client should redirect to the login or OTP resend flow.

#### Request

**Headers:**

| Header             | Required | Description                                         |
| :----------------- | :------- | :-------------------------------------------------- |
| `Content-Type`     | Yes      | Must be `application/json`                          |
| `X-Correlation-ID` | No       | Client-generated UUID for end-to-end tracing        |
| `Accept-Language`  | No       | Preferred locale for error messages (default: `en`) |

**Path Parameters:** None.

**Query Parameters:** None.

**Request Body:**

```json
{
  "email": "string — the user's email address",
  "password": "string — the desired account password",
  "fullName": "string — the user's display name"
}
```

**Validation Rules:**

- `email` — Required. Must be a valid email address format (RFC 5321). Max 254 characters. Case-insensitive.
- `password` — Required. Min 8 characters, max 255 characters. Must contain at least one uppercase letter, one lowercase letter, one digit, and one special character.
- `fullName` — Required. Non-blank string. Max 200 characters.

#### Response

**Success — 201 Created:**

```json
{
  "success": true,
  "message": "Registration successful. Please check your email for the activation code.",
  "data": {
    "userId": "uuid — the newly created user's id"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Error Responses:**

| Status | Error Type (`error.type`)     | Condition                                                                              |
| :----- | :---------------------------- | :------------------------------------------------------------------------------------- |
| 400    | `urn:unihub:error:validation` | Request body is missing required fields, fields fail format/length validation rules.   |
| 409    | `urn:unihub:error:conflict`   | A user account with the submitted email already exists (active or inactive).           |
| 500    | `urn:unihub:error:internal`   | Unexpected server-side failure (e.g., database unreachable, event publication failed). |

#### Side Effects

- A new `users` row is created with `role = STUDENT`, `is_active = false`. `student_record_id` is populated if a matching `student_directory` record is found by email.
- A new `password_reset_tokens` row is created with the hashed OTP and a future `expires_at`.
- A domain event (`RegistrationOtpRequestedEvent` or equivalent) is published to the Spring Modulith transactional outbox, carrying the user's email and plain-text OTP for delivery by the `notifications` worker. If outbox publication fails, the entire transaction is rolled back — no `users` or `password_reset_tokens` record is persisted.

#### Spec Traceability

- **Satisfies:** FR-01, FR-02, FR-03, FR-04, FR-05, FR-06, FR-07, FR-08, FR-09, FR-10
- **Scenarios:** Scenario 1, Scenario 2, Scenario 3, Scenario 4, Scenario 5

### POST /api/v1/auth/activate

**Description:** Activates a `STUDENT` account by verifying the submitted 6-digit OTP against the stored hash for the given email. This endpoint is specific to the **registration verification flow** — distinct from the password-reset OTP verification endpoint introduced by Feature 1.4.  
**Required Role / Auth:** PUBLIC — no `Authorization` header required.  
**Idempotency Requirement:** Not Required — the endpoint is naturally safe; repeated calls after the account is active return `409 Conflict` and produce no side effects.

#### Request

**Headers:**

| Header             | Required | Description                                         |
| :----------------- | :------- | :-------------------------------------------------- |
| `Content-Type`     | Yes      | Must be `application/json`                          |
| `X-Correlation-ID` | No       | Client-generated UUID for end-to-end tracing        |
| `Accept-Language`  | No       | Preferred locale for error messages (default: `en`) |

**Path Parameters:** None.

**Query Parameters:** None.

**Request Body:**

```json
{
  "email": "string — the email address of the account to activate",
  "otp": "string — the 6-digit numeric OTP received via email"
}
```

**Validation Rules:**

- `email` — Required. Valid email format. Max 254 characters.
- `otp` — Required. Must be exactly 6 numeric digits (`[0-9]{6}`).

#### Response

**Success — 200 OK:**

```json
{
  "success": true,
  "message": "Account activated successfully. You may now log in.",
  "data": null,
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

> **Note:** `data` is `null` (omitted per `@JsonInclude(NON_NULL)`) — account activation does not return user data or tokens; the client must call the login endpoint separately.

**Error Responses:**

| Status | Error Type (`error.type`)      | Condition                                                                                                                                                                                                                      |
| :----- | :----------------------------- | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 400    | `urn:unihub:error:validation`  | Request body is missing required fields or `otp` does not match `[0-9]{6}` format.                                                                                                                                             |
| 409    | `urn:unihub:error:conflict`    | The account for the submitted email is already active (`is_active = true`).                                                                                                                                                    |
| 422    | `urn:unihub:error:otp-invalid` | The OTP is incorrect, expired, or already consumed. The error detail message is **uniform** across all three sub-cases to prevent state enumeration. No user record exists for the submitted email also falls under this case. |
| 500    | `urn:unihub:error:internal`    | Unexpected server-side failure.                                                                                                                                                                                                |

#### Side Effects

- On success: `users.is_active` is set to `true` and `password_reset_tokens.used_at` is set to the current timestamp for the consumed token.
- No side effects on any error response.

#### Spec Traceability

- **Satisfies:** FR-11, FR-12, FR-13, FR-14, FR-15, FR-16
- **Scenarios:** Scenario 6, Scenario 7, Scenario 8, Scenario 9, Scenario 10

### POST /api/v1/auth/otp/resend

**Description:** Invalidates existing unconsumed OTP tokens for the given email and issues a fresh OTP via email. This endpoint is shared between the registration flow (inactive account, this feature) and the password-reset flow (active account, Feature 1.4). This contract covers the registration context; Feature 1.4's contract extends the behavior for the active-account context.  
**Required Role / Auth:** PUBLIC — no `Authorization` header required.  
**Idempotency Requirement:** Not Required — repeated resend requests are safe by design; each call invalidates prior tokens and creates a new one.

#### Request

**Headers:**

| Header             | Required | Description                                         |
| :----------------- | :------- | :-------------------------------------------------- |
| `Content-Type`     | Yes      | Must be `application/json`                          |
| `X-Correlation-ID` | No       | Client-generated UUID for end-to-end tracing        |
| `Accept-Language`  | No       | Preferred locale for error messages (default: `en`) |

**Path Parameters:** None.

**Query Parameters:** None.

**Request Body:**

```json
{
  "email": "string — the email address for which to resend the OTP"
}
```

**Validation Rules:**

- `email` — Required. Valid email format. Max 254 characters.

#### Response

**Success — 200 OK:**

```json
{
  "success": true,
  "message": "A new activation code has been sent to your email.",
  "data": null,
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Error Responses:**

| Status | Error Type (`error.type`)     | Condition                                                                                                                                                                                                                            |
| :----- | :---------------------------- | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 400    | `urn:unihub:error:validation` | Request body is missing `email` or `email` fails format validation.                                                                                                                                                                  |
| 422    | `urn:unihub:error:state`      | No inactive account exists for the submitted email — either no account exists at all, or the account is already active. The error detail does not distinguish between "not found" and "already active" to prevent email enumeration. |
| 500    | `urn:unihub:error:internal`   | Unexpected server-side failure.                                                                                                                                                                                                      |

#### Side Effects

- All existing unconsumed `password_reset_tokens` rows for the user have `used_at` set to the current timestamp.
- A new `password_reset_tokens` row is created with a fresh hashed OTP and `expires_at`.
- A domain event is published to the transactional outbox carrying the new plain-text OTP for email delivery. If outbox publication fails, the entire transaction is rolled back.

#### Spec Traceability

- **Satisfies:** FR-17, FR-18, FR-19
- **Scenarios:** Scenario 11, Scenario 12, Scenario 13

## 4. Rate Limiting Behavior

All three endpoints in this contract are **public** (unauthenticated) and therefore subject to the global Bucket4j Token Bucket rate limiter enforced at the application filter layer (per `docs/system-design.md §6 Traffic Spike Management` and `docs/api-standards.md §Standard Response Headers`).

When the rate limit is exceeded, the backend returns `429 Too Many Requests` with the following headers:

- `X-RateLimit-Limit` — configured bucket capacity.
- `X-RateLimit-Remaining` — tokens remaining in the current window.
- `X-RateLimit-Retry-After` — seconds until the next token becomes available.

The specific bucket capacity and refill rate are deployment configuration concerns and are not specified in this contract.

## 5. Idempotency Behavior

No endpoint in this contract requires the `Idempotency-Key` header. The rationale per endpoint:

- **POST /api/v1/auth/register** — Naturally non-idempotent by design. Duplicate submissions are rejected at `409 Conflict` before any write occurs. Adding an idempotency key would provide no additional safety guarantee.
- **POST /api/v1/auth/activate** — Safe to repeat: once the account is active, subsequent calls return `409 Conflict` with no further state changes.
- **POST /api/v1/auth/otp/resend** — Safe to repeat: each call performs a deterministic operation (invalidate existing, create new). No financial or capacity consequence to repeated calls.

## 6. Cross-Endpoint Consistency Notes

- **Error type URNs** follow the established project pattern (`urn:unihub:error:<category>`) used across all existing contracts (1.2, 1.3). New types introduced by this contract: `urn:unihub:error:otp-invalid` (for activation failures) — this type will be reused by Feature 1.4 for its own OTP verification endpoint, ensuring consistency.
- **`data: null` on 200 OK with no resource to return** — consistent with the logout endpoint in Feature 1.2 (`204 No Content`), except here `200 OK` with a meaningful `message` is preferred for client UX (activation confirmation message). This is a deliberate deviation from `204` for the two confirmation-style endpoints (`/activate`, `/otp/resend`).
- **`userId` field name in the register response** — consistent with the `id` fields returned in other resource-creation responses across the codebase. The `userId` name is chosen (over bare `id`) to make the response self-describing for the registration context.
