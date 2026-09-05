# API Contract: User Registration & Identity Verification

> **Status:** APPROVED  
> **Spec:** `specs/user-registration-and-identity-verification/SPEC.md`  
> **Last revised:** 2026-09-05

## 1. Overview

This contract defines the three REST endpoints introduced by Feature 1.1 (`user-registration-and-identity-verification`) within the `iam` module. The endpoints live under the `/api/v1/auth` resource prefix:

1. `POST /api/v1/auth/register` — self-registration for `STUDENT` accounts.
2. `POST /api/v1/auth/activate` — OTP-based account activation (registration verification flow).
3. `POST /api/v1/auth/otp/resend` — shared OTP resend (inactive accounts: `ACTIVATION_OTP`; active accounts: `PASSWORD_RESET_OTP`).

## 2. Global Standards Applied

- **URI structure / versioning:** `/api/v1/...` URI path versioning (per `docs/api-standards.md §Versioning Strategy`).
- **Success response shape:** Standard JSON envelope with `success`, `message`, `data`, and `metadata` fields. `null` values are omitted (`@JsonInclude(NON_NULL)`). (per `docs/api-standards.md §Success Response Envelope`).
- **Error response format:** RFC 7807 Problem Details wrapped in the standard envelope (`success: false`, `error` object with `type`, `title`, `status`, `detail`, `instance`, and optional `validationErrors`; `metadata` with `traceId` and `processedBy`). (per `docs/api-standards.md §Error Response`).
- **Auth header convention:** All three endpoints in this contract are **PUBLIC** (unauthenticated); no `Authorization` header required.
- **Date/time formatting:** ISO 8601 UTC with trailing `Z` (e.g., `2026-05-03T12:00:00Z`).
- **Request field casing:** camelCase for all JSON request and response body fields.

## 3. Endpoints

### 3.1. POST /api/v1/auth/register

**Description:** Creates a new `STUDENT` account in inactive state (`is_active = false`), links the identity to `student_directory` if an active matching record exists, hashes the password using **Argon2**, generates a 6-digit numeric OTP, persists it in `user_verification_tokens` (`token_type = 'ACTIVATION_OTP'`), and publishes `OtpRequestedEvent` (purpose: `ACTIVATION`) to the transactional outbox for RabbitMQ externalization.  
**Required Role / Auth:** PUBLIC  
**Idempotency Requirement:** Not required — duplicate registration attempts with the same email are rejected with `409 Conflict` by the database unique constraint on `users.email` (`CITEXT`).

#### Request

**Headers:**

| Header             | Required | Description                                         |
| :----------------- | :------: | :-------------------------------------------------- |
| `Content-Type`     |   Yes    | Must be `application/json`                          |
| `X-Correlation-ID` |    No    | Client-generated UUID for end-to-end tracing        |
| `Accept-Language`  |    No    | Preferred locale for error messages (default: `en`) |

**Request Body:**

```json
{
  "email": "student.alice@student.hcmus.edu.vn",
  "password": "StrongPassword123!",
  "fullName": "Alice Nguyen"
}
```

**Validation Rules:**

- `email`: Required, non-blank string. Must be a valid email format conforming to RFC 5321. Max 254 characters. Case-insensitive.
- `password`: Required, non-blank string. Min 8 characters, max 255 characters. Must contain at least one uppercase letter, one lowercase letter, one digit, and one special character.
- `fullName`: Required, non-blank string. Max 200 characters.

#### Response

**Success — 201 Created:**

```json
{
  "success": true,
  "message": "Registration successful. Please check your email for the activation code.",
  "data": {
    "userId": "d290f1ee-6c54-4b01-90e6-d701748f0851"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Error Responses (RFC 7807):**

| Status | Error Type (`error.type`)       | Title                 | Condition                                                                                                  |
| :----: | :------------------------------ | :-------------------- | :--------------------------------------------------------------------------------------------------------- |
| `400`  | `urn:unihub:error:validation`   | Bad Request           | Malformed JSON syntax or unparseable HTTP payload.                                                         |
| `409`  | `urn:unihub:error:conflict`     | Conflict              | A user account with the submitted email already exists (active or inactive).                               |
| `422`  | `urn:unihub:error:validation`   | Unprocessable Entity  | Payload fails `@Valid` constraints (missing fields, invalid email format, weak password, length exceeded). |
| `429`  | `urn:unihub:error:rate-limited` | Too Many Requests     | Bucket4j Token Bucket rate limit exceeded for client IP.                                                   |
| `500`  | `urn:unihub:error:internal`     | Internal Server Error | Unexpected server error (e.g. database unreachable, outbox event persistence failure).                     |

**Error Response Example (422 Unprocessable Entity):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:validation",
    "title": "Unprocessable Entity",
    "status": 422,
    "detail": "Validation failed for 'registerRequest'. 1 error(s) found.",
    "instance": "/api/v1/auth/register",
    "validationErrors": [
      "password: Password must contain at least 8 characters, one uppercase letter, one lowercase letter, one digit, and one special character"
    ]
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Error Response Example (409 Conflict):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:conflict",
    "title": "Conflict",
    "status": 409,
    "detail": "An account with this email already exists.",
    "instance": "/api/v1/auth/register"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

#### Side Effects

- A new `users` row is inserted with `role = 'STUDENT'`, `is_active = false`, `is_temporary_password = false`, and Argon2 hashed password.
- If an active `student_directory` record matches `email`, `student_record_id` and `organization_id` are set; otherwise they are `NULL`.
- A new `user_verification_tokens` row is inserted with `token_type = 'ACTIVATION_OTP'`, HMAC-SHA256 hashed OTP, and configurable TTL (default: 15 minutes).
- An `OtpRequestedEvent` (purpose: `ACTIVATION`) is written to `event_publication` table for RabbitMQ externalization.

### 3.2. POST /api/v1/auth/activate

**Description:** Activates a `STUDENT` account by verifying the submitted 6-digit OTP against active records in `user_verification_tokens` with `token_type = 'ACTIVATION_OTP'`. On success, consumes the token (`used_at = now()`) and activates the user account (`is_active = true`).  
**Required Role / Auth:** PUBLIC  
**Idempotency Requirement:** Safe to repeat — once active, subsequent calls return `409 Conflict`.

#### Request

**Headers:**

| Header             | Required | Description                                         |
| :----------------- | :------: | :-------------------------------------------------- |
| `Content-Type`     |   Yes    | Must be `application/json`                          |
| `X-Correlation-ID` |    No    | Client-generated UUID for end-to-end tracing        |
| `Accept-Language`  |    No    | Preferred locale for error messages (default: `en`) |

**Request Body:**

```json
{
  "email": "student.alice@student.hcmus.edu.vn",
  "otp": "849201"
}
```

**Validation Rules:**

- `email`: Required, non-blank string. Valid email format. Max 254 characters.
- `otp`: Required, non-blank string. Must be exactly 6 numeric digits matching `^[0-9]{6}$`.

#### Response

**Success — 200 OK:**

```json
{
  "success": true,
  "message": "Account activated successfully. You may now log in.",
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

_(Note: `data` is omitted per `@JsonInclude(NON_NULL)`. The user proceeds to the Login screen)._

**Error Responses (RFC 7807):**

| Status | Error Type (`error.type`)       | Title                 | Condition                                                                                                      |
| :----: | :------------------------------ | :-------------------- | :------------------------------------------------------------------------------------------------------------- |
| `400`  | `urn:unihub:error:validation`   | Bad Request           | Malformed JSON syntax or unparseable HTTP payload.                                                             |
| `409`  | `urn:unihub:error:conflict`     | Conflict              | The account for the submitted email is already active (`is_active = true`).                                    |
| `422`  | `urn:unihub:error:validation`   | Unprocessable Entity  | Payload fails `@Valid` constraints (e.g. invalid email format or OTP not 6 digits).                            |
| `422`  | `urn:unihub:error:otp-invalid`  | Unprocessable Entity  | **Uniform error:** The OTP is incorrect, expired, already consumed, or no user exists for the submitted email. |
| `429`  | `urn:unihub:error:rate-limited` | Too Many Requests     | Rate limit exceeded (protects against brute-forcing OTPs).                                                     |
| `500`  | `urn:unihub:error:internal`     | Internal Server Error | Unexpected server failure.                                                                                     |

**Error Response Example (422 Invalid OTP — Uniform Response):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:otp-invalid",
    "title": "Unprocessable Entity",
    "status": 422,
    "detail": "Invalid or expired activation code.",
    "instance": "/api/v1/auth/activate"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Error Response Example (409 Conflict — Already Active):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:conflict",
    "title": "Conflict",
    "status": 409,
    "detail": "Account is already active.",
    "instance": "/api/v1/auth/activate"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

#### Side Effects

- `users.is_active` is updated to `true`, `updated_at = now()`.
- The matched `user_verification_tokens` record has `used_at = now()`, `updated_at = now()`.
- No side effects on error.

### 3.3. POST /api/v1/auth/otp/resend

**Description:** Shared OTP resend endpoint. Accepts an email address and checks the account state:

- If `user.is_active == false` (Registration flow): Invalidates prior unconsumed `ACTIVATION_OTP` tokens, creates a fresh `ACTIVATION_OTP`, and publishes `OtpRequestedEvent` (purpose: `ACTIVATION`).
- If `user.is_active == true` (Forgot Password flow - Feature 1.4): Invalidates prior unconsumed `PASSWORD_RESET_OTP` tokens, creates a fresh `PASSWORD_RESET_OTP`, and publishes `OtpRequestedEvent` (purpose: `PASSWORD_RESET`).
- Invalidation is strictly scoped to the corresponding `token_type` to prevent cross-flow token collisions.

**Required Role / Auth:** PUBLIC  
**Idempotency Requirement:** Safe to repeat — each call deterministically invalidates prior tokens of that type and generates a new one.

#### Request

**Headers:**

| Header             | Required | Description                                         |
| :----------------- | :------: | :-------------------------------------------------- |
| `Content-Type`     |   Yes    | Must be `application/json`                          |
| `X-Correlation-ID` |    No    | Client-generated UUID for end-to-end tracing        |
| `Accept-Language`  |    No    | Preferred locale for error messages (default: `en`) |

**Request Body:**

```json
{
  "email": "student.alice@student.hcmus.edu.vn"
}
```

**Validation Rules:**

- `email`: Required, non-blank string. Valid email format conforming to RFC 5321. Max 254 characters.

#### Response

**Success — 200 OK:**

```json
{
  "success": true,
  "message": "A verification code has been sent to your email.",
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Error Responses (RFC 7807):**

| Status | Error Type (`error.type`)       | Title                 | Condition                                          |
| :----: | :------------------------------ | :-------------------- | :------------------------------------------------- |
| `400`  | `urn:unihub:error:validation`   | Bad Request           | Malformed JSON syntax or unparseable HTTP payload. |
| `404`  | `urn:unihub:error:not-found`    | Not Found             | No user account exists for the submitted email.    |
| `422`  | `urn:unihub:error:validation`   | Unprocessable Entity  | Missing or malformed email address.                |
| `429`  | `urn:unihub:error:rate-limited` | Too Many Requests     | Rate limit exceeded (preventing email spam/abuse). |
| `500`  | `urn:unihub:error:internal`     | Internal Server Error | Unexpected server error.                           |

**Error Response Example (404 Not Found):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:not-found",
    "title": "Not Found",
    "status": 404,
    "detail": "No account found for this email.",
    "instance": "/api/v1/auth/otp/resend"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

#### Side Effects

- Existing active `user_verification_tokens` rows for the user matching the target `token_type` have `used_at = now()`.
- A new `user_verification_tokens` row is created with matching `token_type`, HMAC-SHA256 hashed OTP, and configurable TTL.
- A new `OtpRequestedEvent` is published to the transactional outbox (`event_publication`) with the appropriate purpose (`ACTIVATION` or `PASSWORD_RESET`).

## 4. Rate Limiting Behavior

All three endpoints are **public** (unauthenticated) and are protected by the global Bucket4j Token Bucket rate limiter at the application filter layer:

- Headers returned on every request:
  - `X-RateLimit-Limit` — configured bucket capacity.
  - `X-RateLimit-Remaining` — tokens remaining in the current window.
  - `X-RateLimit-Retry-After` — seconds until refill.
- Exceeding the rate limit returns `429 Too Many Requests` with `error.type = "urn:unihub:error:rate-limited"`.

## 5. Security & Cryptography Summary

1. **Password Hashing:** **Argon2** (`Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()`).
2. **OTP Hashing:** HMAC-SHA256 using server-side secret (`app.auth.otp.hmac-secret`), never stored plain-text.
3. **OTP TTL:** Default 15 minutes, configurable via `app.auth.otp.ttl-minutes`.
4. **Anti-Enumeration on Activation:** Uniform error message (`"Invalid or expired activation code."`) for all verification failure conditions.
5. **Worker Decoupling:** Outbox event `OtpRequestedEvent` externalized to RabbitMQ (`unihub.events::notifications`); no worker notification execution in Feature 1.1 scope.
