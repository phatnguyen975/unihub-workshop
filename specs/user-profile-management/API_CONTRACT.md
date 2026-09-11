# API Contract: User Profile Management

> **Status:** APPROVED  
> **Spec:** `specs/user-profile-management/SPEC.md`  
> **Last revised:** 2026-09-08

## 1. Overview

This contract specifies the HTTP REST interface for the **User Profile Management** feature (Domain 1: Identity & Access Management, Feature 1.8). It defines three endpoints under `/api/v1/users` that allow any authenticated user — regardless of role — to retrieve their own profile snapshot, update mutable personal fields, and perform a self-service authenticated password change (which also revokes all other active sessions as a security side-effect). All endpoints operate exclusively on the caller's own account; no cross-user access is possible.

## 2. Global Standards Applied

- **URI structure / versioning:** URI path versioning with `/api/v1/...` prefix, conforming to `docs/api-standards.md`.
- **Pagination convention:** Not applicable — no collection endpoints in this contract.
- **Success response shape:** Unified `ApiResponse<T>` envelope with `success`, `message`, `data`, and `metadata` (`processedBy`, `traceId`). Null fields dropped via non-null serialization.
- **Error response format:** RFC 7807 Problem Details wrapped inside the standard response envelope with `success = false`, `error` object (`type`, `title`, `status`, `detail`, `instance`), and `metadata`. Validation errors additionally include a `validationErrors` array per `docs/api-standards.md §Error Response`.
- **Idempotency-key convention:** Not required for any endpoint in this contract (see §5 Idempotency Behavior).
- **Auth header convention:** Mandatory `Authorization: Bearer <accessToken>` on all endpoints.
- **Date/time formatting convention:** ISO 8601 UTC string format with trailing `Z` (e.g. `2026-09-04T10:00:00Z`).

## 3. Endpoints

### GET /api/v1/users/me

**Description:** Retrieves the full profile of the authenticated caller, derived entirely from the `sub` claim in the JWT.  
**Required Role / Auth:** Authenticated (`STUDENT`, `ORGANIZER`, or `STAFF`)  
**Idempotency Requirement:** Not Required — read-only operation.

#### Request

**Headers:**

| Header          | Required | Description                                                           |
| :-------------- | :------: | :-------------------------------------------------------------------- |
| `Authorization` |   Yes    | Bearer JWT access token containing `sub`, `jti`, `exp`, `sid` claims. |

**Path Parameters:** None.

**Query Parameters:** None.

**Request Body:** None.

#### Response

**Success — 200 OK:**

```json
{
  "success": true,
  "message": "Profile retrieved successfully.",
  "data": {
    "id": "3f6e4c2a-1234-4b56-a789-0123456789ab",
    "email": "alice.nguyen@student.hcmut.edu.vn",
    "fullName": "Alice Nguyen",
    "role": "STUDENT",
    "phoneNumber": "+84912345678",
    "avatarUrl": "https://cdn.unihub.vn/avatars/alice.jpg",
    "lastLoginAt": "2026-09-07T14:30:00Z",
    "organizationId": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
    "studentRecordId": "f01e2d3c-4b5a-6789-0123-456789abcdef"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Field Descriptions (`data`):**

| Field             | Type              | Nullable | Description                                                                                               |
| :---------------- | :---------------- | :------: | :-------------------------------------------------------------------------------------------------------- |
| `id`              | UUID (string)     |    No    | Unique user identifier.                                                                                   |
| `email`           | String            |    No    | Primary authentication credential; immutable.                                                             |
| `fullName`        | String            |    No    | User's display name; max 200 characters.                                                                  |
| `role`            | String (enum)     |    No    | `STUDENT`, `ORGANIZER`, or `STAFF`. Managed by provisioning flows.                                        |
| `phoneNumber`     | String            |   Yes    | International phone number. Omitted from response when stored value is `null`.                            |
| `avatarUrl`       | String (URL)      |   Yes    | Absolute URL of the user's avatar image. Omitted from response when stored value is `null`.               |
| `lastLoginAt`     | String (ISO 8601) |   Yes    | Timestamp of the user's last successful authentication. Omitted when `null`.                              |
| `organizationId`  | UUID (string)     |   Yes    | Associated organization identifier. Non-null for `ORGANIZER` or affiliated students; omitted when `null`. |
| `studentRecordId` | UUID (string)     |   Yes    | Link to student directory record for verified students; omitted when `null`.                              |

> [!IMPORTANT]
> All nullable fields (`phoneNumber`, `avatarUrl`, `lastLoginAt`, `organizationId`, `studentRecordId`) are omitted from the JSON payload when their stored value is `null`, conforming to `@JsonInclude(JsonInclude.Include.NON_NULL)` and `docs/api-standards.md`. The example above illustrates a profile where all fields are populated.

**Error Responses:**

| Status                      | Error Type (`error.type`)       | Title                 | Condition                                                               |
| :-------------------------- | :------------------------------ | :-------------------- | :---------------------------------------------------------------------- |
| `401 Unauthorized`          | `urn:unihub:error:unauthorized` | Unauthorized          | Missing, expired, invalid-signature, or Redis-blacklisted Bearer token. |
| `500 Internal Server Error` | `urn:unihub:error:internal`     | Internal Server Error | Unexpected server failure (e.g. database unreachable).                  |

#### Side Effects

None.

#### Spec Traceability

- **Satisfies:** FR-01, FR-02, FR-03, FR-04, FR-05, FR-26, FR-27, Scenario 1, Scenario 2, Scenario 13.

### PATCH /api/v1/users/me

**Description:** Partially updates the caller's mutable profile fields (`fullName`, `phoneNumber`, `avatarUrl`). Only fields present in the request body are updated; absent fields are preserved. Returns the complete updated profile.  
**Required Role / Auth:** Authenticated (`STUDENT`, `ORGANIZER`, or `STAFF`)  
**Idempotency Requirement:** Not Required — PATCH with the same payload produces the same outcome idempotently; no retry-specific deduplication is needed.

#### Request

**Headers:**

| Header          | Required | Description                                                           |
| :-------------- | :------: | :-------------------------------------------------------------------- |
| `Authorization` |   Yes    | Bearer JWT access token containing `sub`, `jti`, `exp`, `sid` claims. |
| `Content-Type`  |   Yes    | Must be `application/json`.                                           |

**Path Parameters:** None.

**Query Parameters:** None.

**Request Body:**

```json
{
  "fullName": "Alice Thu Nguyen",
  "phoneNumber": "+84912345678",
  "avatarUrl": "https://cdn.unihub.vn/avatars/alice-new.jpg"
}
```

> At least one field must be present. All three fields are individually optional in a single request.

**Validation Rules:**

| Field         | Required? | Rules                                                                                                                                                           |
| :------------ | :-------: | :-------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `fullName`    | Optional  | If present: must be non-blank (not whitespace-only); max 200 characters; must not be `null`.                                                                    |
| `phoneNumber` | Optional  | If present and non-null: must match `^\+?[0-9]{8,15}$`; max 20 characters. If `null`: the stored phone number is cleared.                                       |
| `avatarUrl`   | Optional  | If present and non-null: must be a well-formed absolute URL (starting with `http://` or `https://`); max 2048 characters. If `null`: the stored URL is cleared. |

> **Immutable field rejection:** Any field that is not in the set `{fullName, phoneNumber, avatarUrl}` (e.g. `email`, `role`, `id`, `organizationId`, `isActive`, etc.) must not be accepted by the request. If the deserialized request body contains any such field, the system returns `422` with `validationErrors` listing the offending field(s). No stored value is altered.

#### Response

**Success — 200 OK:**

```json
{
  "success": true,
  "message": "Profile updated successfully.",
  "data": {
    "id": "3f6e4c2a-1234-4b56-a789-0123456789ab",
    "email": "alice.nguyen@student.hcmut.edu.vn",
    "fullName": "Alice Thu Nguyen",
    "role": "STUDENT",
    "phoneNumber": "+84912345678",
    "avatarUrl": "https://cdn.unihub.vn/avatars/alice-new.jpg",
    "lastLoginAt": "2026-09-07T14:30:00Z",
    "organizationId": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
    "studentRecordId": "f01e2d3c-4b5a-6789-0123-456789abcdef"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

> The response shape is identical to `GET /api/v1/users/me`, reflecting the updated state of all profile fields.

**Error Responses:**

| Status                      | Error Type (`error.type`)       | Title                 | Condition                                                                                                                                                                                                |
| :-------------------------- | :------------------------------ | :-------------------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `401 Unauthorized`          | `urn:unihub:error:unauthorized` | Unauthorized          | Missing, expired, invalid-signature, or Redis-blacklisted Bearer token.                                                                                                                                  |
| `422 Unprocessable Content` | `urn:unihub:error:validation`   | Unprocessable Content | One or more fields fail their validation constraint (blank `fullName`, invalid `phoneNumber` format, invalid `avatarUrl` URL, or immutable field sent). `validationErrors` array is included in `error`. |
| `500 Internal Server Error` | `urn:unihub:error:internal`     | Internal Server Error | Unexpected server failure.                                                                                                                                                                               |

**422 Error body example (validation failure):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:validation",
    "title": "Unprocessable Content",
    "status": 422,
    "detail": "Request validation failed.",
    "instance": "/api/v1/users/me",
    "validationErrors": [
      {
        "field": "phoneNumber",
        "message": "must match international format ^\\+?[0-9]{8,15}$"
      }
    ]
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

#### Side Effects

1. Updates `full_name`, `phone_number`, and/or `avatar_url` columns on the matching `users` record in the primary PostgreSQL database — only for fields present in the request body.
2. Sets `updated_at = NOW()` and `updated_by = <caller UUID>` on the same record.

#### Spec Traceability

- **Satisfies:** FR-06, FR-07, FR-08, FR-09, FR-10, FR-11, FR-12, FR-13, FR-26, FR-27, Scenario 3, Scenario 4, Scenario 5, Scenario 6, Scenario 7.

### PATCH /api/v1/users/me/password

**Description:** Performs a self-service authenticated password change. Verifies the caller's current password, replaces it with the new one using Argon2 hashing, then revokes all other active sessions. Returns 200 OK with a confirmation message; the caller's current session remains valid.
**Required Role / Auth:** Authenticated (`STUDENT`, `ORGANIZER`, or `STAFF`)
**Idempotency Requirement:** Not Required — see §5 Idempotency Behavior.

#### Request

**Headers:**

| Header          | Required | Description                                                           |
| :-------------- | :------: | :-------------------------------------------------------------------- |
| `Authorization` |   Yes    | Bearer JWT access token containing `sub`, `jti`, `exp`, `sid` claims. |
| `Content-Type`  |   Yes    | Must be `application/json`.                                           |

**Path Parameters:** None.

**Query Parameters:** None.

**Request Body:**

```json
{
  "currentPassword": "Current@Pass1",
  "newPassword": "New@Secure2!"
}
```

**Validation Rules:**

| Field             | Required? | Rules                                                                                                                                                                                               |
| :---------------- | :-------: | :-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `currentPassword` |    Yes    | Must not be blank. Must match the caller's stored Argon2 password hash. Verification failure → `422`.                                                                                               |
| `newPassword`     |    Yes    | Must not be blank. Must differ from `currentPassword`. Must be 8–255 characters with at least one uppercase letter, one lowercase letter, one digit, and one special character. Violations → `422`. |

#### Response

**Success — 200 OK:**

```json
{
  "success": true,
  "message": "Password changed successfully.",
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

> The `data` field is `null` (`ApiResponse<Void>`) and is omitted from the serialized JSON payload per the platform's non-null serialization convention (`@JsonInclude(JsonInclude.Include.NON_NULL)`). The `message` field provides the human-readable confirmation for frontend Toast notifications.

**Error Responses:**

| Status                      | Error Type (`error.type`)              | Title                 | Condition                                                                                                                                                                                                        |
| :-------------------------- | :------------------------------------- | :-------------------- | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `401 Unauthorized`          | `urn:unihub:error:unauthorized`        | Unauthorized          | Missing, expired, invalid-signature, or Redis-blacklisted Bearer token.                                                                                                                                          |
| `422 Unprocessable Content` | `urn:unihub:error:validation`          | Unprocessable Content | Any of: `currentPassword` does not match stored hash; `newPassword` equals `currentPassword`; `newPassword` fails strength requirements; either field is blank. `validationErrors` array is included in `error`. |
| `503 Service Unavailable`   | `urn:unihub:error:service-unavailable` | Service Unavailable   | Redis cluster is unreachable (fail-closed per ADR-010). No password hash updated, no sessions revoked. Response includes `Retry-After` header (configurable via `app.http.retry-after-seconds`, default 30 s).   |
| `500 Internal Server Error` | `urn:unihub:error:internal`            | Internal Server Error | Unexpected server failure (e.g. database unreachable during the update).                                                                                                                                         |

**422 Error body example (wrong current password):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:validation",
    "title": "Unprocessable Content",
    "status": 422,
    "detail": "Request validation failed.",
    "instance": "/api/v1/users/me/password",
    "validationErrors": [
      {
        "field": "currentPassword",
        "message": "Current password is incorrect."
      }
    ]
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

#### Side Effects

Upon a **successful** request (all validations pass, Redis is reachable):

1. Updates `password_hash` on the caller's `users` record in the primary PostgreSQL database to the Argon2 hash of `newPassword`.
2. Sets `updated_at = NOW()` and `updated_by = <caller UUID>` on the same `users` record.
3. Sets `revoked_at = NOW()` on all `refresh_tokens` records for the caller where `id != sid` (the caller's current session) and `revoked_at IS NULL`.
4. Writes the `jti` of each revoked remote session's active access token to the Redis JTI blacklist (key `jti:blacklisted:<jti>`) with TTL = `exp - NOW()` for that token.
5. The caller's current session (`sid`) and its associated access token remain unmodified in both PostgreSQL and Redis.

> [!CAUTION]
> Steps 1–4 form an atomic unit. If Redis is unreachable (step 4 cannot proceed), the entire operation is aborted before step 1 executes. The system does **not** update the password hash and then fail to blacklist sessions (fail-closed per ADR-010, FR-23, BR-06).

#### Spec Traceability

- **Satisfies:** FR-14, FR-15, FR-16, FR-17, FR-18, FR-19, FR-20, FR-21, FR-22, FR-23, FR-24, FR-25, FR-26, FR-27, Scenario 8, Scenario 9, Scenario 10, Scenario 11, Scenario 12.

## 4. Rate Limiting Behavior

All three endpoints are subject to the platform's standard **Default Tier** Bucket4j Token Bucket filter backed by centralized Redis counters (ADR-019):

- **Tier Classification:** Default Tier (all endpoints under `/api/v1/users`).
- **Bucket Keying:** Keyed by authenticated user identifier (`jwt.sub`).
- **Bucket Configuration:** Configuration-driven via `rate-limit.default-tier` in `application.yml` (default: capacity 100 tokens, refill 100 tokens per 60 seconds).
- **On Limit Exceeded:** Returns HTTP `429 Too Many Requests` with RFC 7807 problem type `urn:unihub:error:rate-limited`, headers `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Retry-After`, and `Retry-After`.
- **Fail-Open Policy:** If Redis is unavailable for rate-limit checks, the filter allows the request through and emits telemetry (ADR-019). This is strictly distinct from the fail-closed Redis policy on the password-change blacklist write (FR-23, BR-06).

### Endpoint Routing Summary

| Path                              | Auth | Role                      | Rate Limit Tier | Notes              |
| :-------------------------------- | :--: | :------------------------ | :-------------- | :----------------- |
| `GET /api/v1/users/me`            | Yes  | Any (`STUDENT/ORG/STAFF`) | Default (100)   | Keyed by `jwt.sub` |
| `PATCH /api/v1/users/me`          | Yes  | Any (`STUDENT/ORG/STAFF`) | Default (100)   | Keyed by `jwt.sub` |
| `PATCH /api/v1/users/me/password` | Yes  | Any (`STUDENT/ORG/STAFF`) | Default (100)   | Keyed by `jwt.sub` |

## 5. Idempotency Behavior

- **GET /api/v1/users/me:** Naturally safe and idempotent (read-only). No `Idempotency-Key` header.
- **PATCH /api/v1/users/me:** Sending the same request body repeatedly produces the same final state. No harmful duplicate side-effects. No `Idempotency-Key` header required.
- **PATCH /api/v1/users/me/password:** Not classically idempotent — a second call with the same `currentPassword` fails with `422` because the hash was already changed by the first call. However, the operation produces no harmful duplicates: the second call fails safely at the verification step before any mutation occurs. No `Idempotency-Key` header is required. This is an intentional deviation from the general mutation guidance in `docs/api-standards.md` because the second call cannot produce a double-charge or double-booking scenario; it simply fails safely.

## 6. Cross-Endpoint Consistency Notes

- **Response envelope:** All three endpoints use the identical `ApiResponse<T>` envelope (`success`, `message`, `data`, `metadata`) with no custom top-level keys, consistent with every other endpoint in the `iam` module.
- **Error type URN scheme:** All error representations use the unified `urn:unihub:error:*` URN scheme (`unauthorized`, `validation`, `service-unavailable`, `internal`), consistent with `active-session-management` and all other feature contracts in this project.
- **Profile response shape reuse:** The `data` object returned by `GET /api/v1/users/me` and `PATCH /api/v1/users/me` (on success) is structurally identical — same field names, same types, same non-null serialization behavior. This is a deliberate consistency decision so clients can use one parser for both responses.
- **Non-null serialization:** Nullable profile fields (`phoneNumber`, `avatarUrl`, `lastLoginAt`, `organizationId`, `studentRecordId`) are omitted from the JSON payload (not serialized as `null`) in both the `GET` and `PATCH` success responses. This is consistent with existing `UserProfileResponse` DTO behavior established in Feature 1.7.
- **`Retry-After` header on 503:** The `PATCH /api/v1/users/me/password` 503 response includes a `Retry-After` header, consistent with the `DELETE /api/v1/auth/sessions/{id}` contract in `active-session-management`.
