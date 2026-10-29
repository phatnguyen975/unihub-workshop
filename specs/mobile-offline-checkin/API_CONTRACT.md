# API Contract: Mobile Offline Check-in — Workshop Data Provisioning

> **Status:** APPROVED  
> **Spec:** `specs/mobile-offline-checkin/SPEC.md`  
> **Last revised:** 2026-10-29

## 1. Overview

This contract defines the single REST endpoint exposed by the `workshops` module in `apps/backend/` for Feature 6.1 (`mobile-offline-checkin`). The endpoint provides Check-in Staff with a paginated, filterable list of workshops they are personally assigned to, enabling client-side caching before going offline. The full behavioral specification is in `specs/mobile-offline-checkin/SPEC.md`; this document specifies the exact wire format.

## 2. Global Standards Applied

- **URI structure / versioning:** `/api/v1/` prefix, resource noun in plural form, following `docs/api-standards.md §Versioning`.
- **Pagination convention:** Keyset cursor pagination via the `shared/pagination` library (`CursorPage<T>` → `PageResponse<T>`). Cursor is an opaque Base64-encoded token. Response includes `cursor.nextCursor` (nullable) and `cursor.hasMore` (boolean). Default page size: 20. Maximum page size: 100.
- **Success response shape:** Standard `ApiResponse<T>` envelope — `{ "success": true, "message": string, "data": <T>, "metadata": { "processedBy": string, "traceId": string } }`. All null values are omitted via `@JsonInclude(JsonInclude.Include.NON_NULL)`.
- **Error response format:** RFC 7807 Problem Details wrapped in the standard `ApiResponse` error envelope — `{ "success": false, "error": { "type": "urn:unihub:error:*", "title": string, "status": int, "detail": string, "instance": string, "validationErrors"?: string[] }, "metadata": { "processedBy": string, "traceId": string } }`. Response uses `Content-Type: application/problem+json`.
- **Idempotency-key convention:** Not applicable — this is a read-only `GET` endpoint.
- **Auth header convention:** `Authorization: Bearer <JWT>` (RS256, asymmetric). JWT validated by the shared security filter chain. JTI revocation checked against Redis.
- **Date/time formatting convention:** ISO-8601 UTC — `yyyy-MM-dd'T'HH:mm:ss'Z'` (e.g. `2026-11-15T09:00:00Z`).

## 3. Endpoints

### GET /api/v1/workshops/assigned

**Description:** Returns a keyset-paginated list of workshops the authenticated STAFF member is explicitly assigned to, filtered by optional criteria, sorted by `starts_at ASC` with `id ASC` as tie-breaker.  
**Required Role / Auth:** `AUTHENTICATED | STAFF` — Valid Bearer JWT with `role = STAFF`. Any other role or missing token is rejected.  
**Idempotency Requirement:** Not Required — `GET` requests are naturally idempotent; no state mutation occurs.

#### Request

**Headers:**

| Header          | Required | Description                                                  |
| :-------------- | :------- | :----------------------------------------------------------- |
| `Authorization` | Yes      | `Bearer <JWT>` — RS256-signed JWT issued by the auth service |

**Path Parameters:** None.

**Query Parameters:**

| Parameter | Type    | Required | Default | Constraints                                     | Description                                                                              |
| :-------- | :------ | :------- | :------ | :---------------------------------------------- | :--------------------------------------------------------------------------------------- |
| `cursor`  | string  | No       | —       | Opaque Base64 token                             | Keyset cursor returned by a previous call. Omit for the first page.                      |
| `size`    | integer | No       | `20`    | 1 ≤ value ≤ 100; values < 1 or > 100 return 400 | Number of items per page.                                                                |
| `status`  | string  | No       | —       | `PUBLISHED` or `COMPLETED` only                 | Filter by workshop lifecycle status. `CANCELED` is not a valid value for this parameter. |
| `from`    | string  | No       | —       | ISO-8601 UTC timestamp                          | Return only workshops where `starts_at >= from`.                                         |
| `to`      | string  | No       | —       | ISO-8601 UTC timestamp                          | Return only workshops where `starts_at <= to`.                                           |
| `q`       | string  | No       | —       | Max 200 characters                              | Case-insensitive keyword search against workshop `title`, `slug`, and `location`.        |

**Request Body:** None.

**Validation Rules:**

- `cursor` — If provided, must be a valid Base64-encoded keyset token produced by this endpoint. If malformed or if the embedded filter fingerprint does not match the current request's active filters, the server returns `400 Bad Request`.
- `size` — If omitted, defaults to `20`. If `< 1` or `> 100`, returns `400 Bad Request`.
- `status` — If provided, must be exactly `PUBLISHED` or `COMPLETED` (case-sensitive). Any other value, including `CANCELED`, returns `400 Bad Request`.
- `from` — If provided, must be a valid ISO-8601 UTC timestamp string. Invalid format returns `400 Bad Request`.
- `to` — If provided, must be a valid ISO-8601 UTC timestamp string. Invalid format returns `400 Bad Request`.
- `from` + `to` — If both are provided, `from` MUST be ≤ `to`. Violation returns `400 Bad Request`.
- `q` — If provided, must not exceed 200 characters. Exceeding returns `400 Bad Request`. Empty string is treated as absent (no filter applied).

#### Response

**Success — 200 OK:**

```json
{
  "success": true,
  "message": "Assigned workshops retrieved successfully",
  "data": {
    "items": [
      {
        "id": "b2a3f0b8-22f9-4b6a-9a54-1a8d8d1f20f4",
        "title": "Python for Data Science",
        "slug": "python-for-data-science-2026",
        "description": "An intensive hands-on workshop covering pandas, matplotlib, and scikit-learn.",
        "coverImageUrl": "https://storage.unihub.io/workshops/covers/python-ds.jpg",
        "location": "Building A, Room 301",
        "startsAt": "2026-11-15T09:00:00Z",
        "endsAt": "2026-11-15T12:00:00Z",
        "status": "PUBLISHED",
        "organizationName": "Faculty of Computer Science",
        "assignedAt": "2026-11-01T14:22:10Z"
      }
    ],
    "cursor": {
      "nextCursor": "eyJpZCI6ImIyYTNmMGI4LTIyZjktNGI2YS05YTU0LTFhOGQ4ZDFmMjBmNCIsInNvcnRGaWVsZCI6InN0YXJ0c0F0IiwicHJpbWFyeVZhbHVlIjoiMjAyNi0xMS0xNVQwOTowMDowMFoiLCJzZWNvbmRhcnlWYWx1ZSI6bnVsbCwiZmlsdGVyRmluZ2VycHJpbnQiOiJhYmNkMTIzNCJ9",
      "hasMore": true
    }
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Response Item Fields (`StaffAssignedWorkshopDto`):**

| Field              | Type                  | Nullable | Description                                                                               |
| :----------------- | :-------------------- | :------- | :---------------------------------------------------------------------------------------- |
| `id`               | string (UUID)         | No       | Workshop unique identifier (UUIDv4)                                                       |
| `title`            | string                | No       | Workshop title                                                                            |
| `slug`             | string                | No       | Workshop URL-safe slug                                                                    |
| `description`      | string                | **Yes**  | Workshop description; omitted when `null` per `@JsonInclude(NON_NULL)`                    |
| `coverImageUrl`    | string (URL)          | **Yes**  | Absolute URL to cover image in MinIO/S3; omitted when `null` per `@JsonInclude(NON_NULL)` |
| `location`         | string                | No       | Physical venue/room                                                                       |
| `startsAt`         | string (ISO-8601 UTC) | No       | Workshop start timestamp                                                                  |
| `endsAt`           | string (ISO-8601 UTC) | No       | Workshop end timestamp                                                                    |
| `status`           | string                | No       | Workshop lifecycle status — `PUBLISHED` or `COMPLETED` (never `CANCELED`)                 |
| `organizationName` | string                | No       | Name of the organizing entity/faculty (from `organizations.name`)                         |
| `assignedAt`       | string (ISO-8601 UTC) | No       | Timestamp when the staff member was assigned to this workshop                             |

**Cursor Object (`cursor`):**

| Field        | Type            | Nullable | Description                                                                           |
| :----------- | :-------------- | :------- | :------------------------------------------------------------------------------------ |
| `nextCursor` | string (Base64) | **Yes**  | Opaque keyset cursor for the next page; `null` (or omitted) when `hasMore` is `false` |
| `hasMore`    | boolean         | No       | `true` if there are more pages to retrieve, `false` when this is the last page        |

**Error Responses:**

| Status                      | Error `type` (URN)                     | Title                 | Condition                                                                                                                  |
| :-------------------------- | :------------------------------------- | :-------------------- | :------------------------------------------------------------------------------------------------------------------------- |
| `400 Bad Request`           | `urn:unihub:error:bad-request`         | Bad Request           | `status` parameter contains an invalid value (e.g. `CANCELED`, or any unrecognized status).                                |
| `400 Bad Request`           | `urn:unihub:error:bad-request`         | Bad Request           | `from` or `to` is not a valid ISO-8601 UTC timestamp string.                                                               |
| `400 Bad Request`           | `urn:unihub:error:bad-request`         | Bad Request           | `from` is after `to` when both are provided (`from > to`).                                                                 |
| `400 Bad Request`           | `urn:unihub:error:bad-request`         | Bad Request           | `size` is less than 1 or greater than 100.                                                                                 |
| `400 Bad Request`           | `urn:unihub:error:bad-request`         | Bad Request           | `q` exceeds 200 characters.                                                                                                |
| `400 Bad Request`           | `urn:unihub:error:bad-request`         | Bad Request           | `cursor` token is malformed (Base64 decode failure or invalid payload structure).                                          |
| `400 Bad Request`           | `urn:unihub:error:bad-request`         | Bad Request           | `cursor` filter fingerprint mismatch (the cursor was minted for a different set of filter parameters).                     |
| `401 Unauthorized`          | `urn:unihub:error:unauthorized`        | Unauthorized          | Missing `Authorization` header, invalid token format, expired JWT, invalid signature, or JTI revoked in Redis.             |
| `403 Forbidden`             | `urn:unihub:error:forbidden`           | Forbidden             | Authenticated user's role is not `STAFF` (`jwt.role != STAFF`).                                                            |
| `429 Too Many Requests`     | `urn:unihub:error:rate-limited`        | Too Many Requests     | Caller has exceeded the 100 requests / 60 s token-bucket quota keyed on `jwt.sub`. Includes `Retry-After` header.          |
| `500 Internal Server Error` | `urn:unihub:error:internal`            | Internal Server Error | Unexpected server-side failure. Technical stack trace is suppressed.                                                       |
| `503 Service Unavailable`   | `urn:unihub:error:service-unavailable` | Service Unavailable   | Primary PostgreSQL database is unreachable. Endpoint does NOT fall back to replica (BR-03). Includes `Retry-After` header. |

**Error Response Examples:**

_1. Validation Error (400 Bad Request — Invalid Parameter):_

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:bad-request",
    "title": "Bad Request",
    "status": 400,
    "detail": "The 'status' parameter must be 'PUBLISHED' or 'COMPLETED'.",
    "instance": "/api/v1/workshops/assigned",
    "validationErrors": ["status: must be one of [PUBLISHED, COMPLETED]"]
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

_2. Filter Fingerprint Mismatch (400 Bad Request — Invalid Cursor):_

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:bad-request",
    "title": "Bad Request",
    "status": 400,
    "detail": "Cursor filter fingerprint mismatch. The pagination cursor was issued for a different filter set.",
    "instance": "/api/v1/workshops/assigned"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

_3. Unauthorized (401 Unauthorized):_

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:unauthorized",
    "title": "Unauthorized",
    "status": 401,
    "detail": "Authentication is required to access this resource.",
    "instance": "/api/v1/workshops/assigned"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

_4. Forbidden (403 Forbidden):_

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:forbidden",
    "title": "Forbidden",
    "status": 403,
    "detail": "You do not have permission to access this resource.",
    "instance": "/api/v1/workshops/assigned"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

#### Side Effects

None. This is a read-only endpoint. No records are created, modified, or deleted. No events are published to RabbitMQ. No Elasticsearch index is updated. No Redis keys are written (rate-limit tokens are consumed atomically but that is an infrastructure side effect, not a domain side effect).

#### Spec Traceability

- **Satisfies:** FR-01, FR-02, FR-03, FR-04, FR-05, FR-06, FR-07, FR-08, FR-09, FR-10, FR-11, FR-12, FR-13, FR-14, FR-15, FR-16, FR-17, FR-18, FR-19, FR-20, FR-21
- **Acceptance Criteria:** Scenarios 1 through 14

## 4. Rate Limiting Behavior

This endpoint is subject to the standard per-authenticated-user token-bucket policy, enforced by **Bucket4j** backed by **Redis**:

- **Quota:** 100 requests per 60-second rolling window.
- **Key:** `jwt.sub` (authenticated user ID).
- **Response on limit exceeded:** `429 Too Many Requests` with `Retry-After` header indicating the seconds until the next token is available.
- **Redis unavailability:** If Redis is unreachable, rate limiting fails **open** — the request is allowed through and rate limiting is skipped. This matches the NFR requirement: a staff member pre-caching data before going offline must not be blocked by an infrastructure failure.

## 5. Idempotency Behavior

Not applicable. `GET /api/v1/workshops/assigned` is a read-only endpoint. It produces no mutations and carries no retry risk that would require idempotency key tracking. Repeated identical calls return the same logical result (subject to underlying data changes).

## 6. Cross-Endpoint Consistency Notes

- **Field naming consistency with existing workshop endpoints:** The `id`, `title`, `slug`, `description`, `coverImageUrl`, `location`, `startsAt`, `endsAt`, `status`, `organizationName` fields use the same camelCase names as the workshop resource fields already established in the `WorkshopApi.java` contract for the `workshops` module. The `assignedAt` field is additive, specific to this staff-scoped view.
- **Cursor shape consistency:** The `cursor` object (`nextCursor`, `hasMore`) matches the `PageResponse<T>` shape produced by `shared/pagination`'s `CursorPage<T>` wrapper, consistent with all other keyset-paginated endpoints in the system.
- **ApiResponse envelope consistency:** The outer `{ success, message, data, metadata: { processedBy, traceId } }` envelope is identical across all endpoints in the system, as implemented by `ApiResponse.java` and `GlobalResponseBodyAdvice.java`.
- **Error shape consistency:** The RFC 7807 Problem Details wrapped inside the `ApiResponse` error envelope (`{ success: false, error: { type, title, status, detail, instance, validationErrors }, metadata }`) matches `docs/api-standards.md §Error Response` and `GlobalExceptionHandler.java`.
