# API Contract: Workshop Creation and Draft Management

> **Status:** APPROVED  
> **Spec:** `specs/workshop-creation-and-draft-management/SPEC.md`  
> **Last revised:** 2026-09-25

## 1. Overview

This contract defines the five endpoints exposed by the `workshops` module for Feature 2.1 — Workshop Creation and Draft Management:

1. `POST /api/v1/admin/workshops/media/upload` — Uploading workshop media assets (cover images, speaker portraits)
2. `POST /api/v1/admin/workshops/drafts` — Saving a workshop as a draft (unified create or update)
3. `GET /api/v1/admin/workshops/{id}` — Retrieving workshop details for viewing or edit form hydration
4. `DELETE /api/v1/admin/workshops/{id}` — Permanently deleting a draft
5. `GET /api/v1/admin/workshops` — Retrieving a paginated, filterable list of workshops owned by the caller's organization

All endpoints are strictly ORGANIZER-only. This contract traces directly to `SPEC.md` for this feature and conforms in full to `docs/api-standards.md`.

## 2. Global Standards Applied

- **URI structure / versioning:** All endpoints are prefixed with `/api/v1/`. Resources are named with plural nouns following RESTful conventions. _(Source: `docs/api-standards.md`)_
- **Pagination convention:** `GET /api/v1/admin/workshops` uses keyset cursor pagination (`cursor`, `size` with default 20, max 100) per `docs/api-standards.md`. Responses return `{ "items": [...], "cursor": { "nextCursor": "...", "hasMore": boolean } }`. Cursors are bound to the filter set (`status`, `priceType`, `q`, `from`, `to`) that produced them; a mismatch is rejected with `400 Bad Request`. Single-resource endpoints do not paginate.
- **Success response shape:** Unified `ApiResponse<T>` envelope — `{ "success": true, "message": "...", "data": { ... }, "metadata": { "processedBy": "...", "traceId": "..." } }`. `204 No Content` responses carry no body. Null fields dropped via non-null serialization (`@JsonInclude(Include.NON_NULL)`). _(Source: `docs/api-standards.md`)_
- **Error response format:** RFC 7807 Problem Details wrapped inside the standard response envelope with `success = false`, `error` object (`type`, `title`, `status`, `detail`, `instance`, and optional `validationErrors`), and `metadata` (`processedBy`, `traceId`) with `Content-Type: application/problem+json`. _(Source: `docs/api-standards.md`)_
- **Idempotency-key convention:** Not applicable for this feature (see per-endpoint decisions below).
- **Auth header convention:** `Authorization: Bearer <JWT>` — RS256 signed, required on every endpoint.
- **Date/time formatting convention:** ISO 8601 with UTC offset — `YYYY-MM-DDTHH:mm:ssZ`.

## 3. Endpoints

### POST /api/v1/admin/workshops/media/upload

**Description:** Uploads a workshop cover image or speaker avatar to MinIO object storage (`unihub-media`). Returns the generated public URL and storage key. This endpoint is stateless with respect to the database; it does not persist workshop records or modify database rows.  
**Required Role / Auth:** `ORGANIZER` — JWT with `role = ORGANIZER`.  
**Idempotency Requirement:** Not Required — each upload generates a new unique file key.

#### Request

**Headers:**

| Header          | Required | Description           |
| :-------------- | :------- | :-------------------- |
| `Authorization` | Yes      | `Bearer <JWT>`        |
| `Content-Type`  | Yes      | `multipart/form-data` |

**Form Data / Multipart Parts:**

| Parameter | Type          | Required | Description                                                                                                                                                               |
| :-------- | :------------ | :------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `file`    | MultipartFile | Yes      | Binary image file. Max 5MB (5,242,880 bytes). Supported MIME: `image/jpeg`, `image/jpg`, `image/png`, `image/webp`. Allowed extensions: `.jpg`, `.jpeg`, `.png`, `.webp`. |
| `type`    | String (enum) | Yes      | Media asset type: `COVER_IMAGE` or `SPEAKER_AVATAR`.                                                                                                                      |

**Validation Rules:**

- `file` must be present, non-empty, and under 5,242,880 bytes.
- `file` Content-Type and magic bytes must correspond to JPEG/JPG, PNG, or WebP.
- `type` must be exactly `COVER_IMAGE` or `SPEAKER_AVATAR`.

#### Response

**Success — 201 Created:**

```json
{
  "success": true,
  "message": "Media uploaded successfully.",
  "data": {
    "url": "http://localhost:9000/unihub-media/workshops/covers/550e8400-e29b-41d4-a716-446655440000.jpg",
    "fileKey": "workshops/covers/550e8400-e29b-41d4-a716-446655440000.jpg",
    "type": "COVER_IMAGE",
    "size": 1048576,
    "contentType": "image/jpeg"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Error Responses:**

| Status                      | Error Type (`error.type`)       | Title                 | Condition                                                             |
| :-------------------------- | :------------------------------ | :-------------------- | :-------------------------------------------------------------------- |
| `400 Bad Request`           | `urn:unihub:error:bad-request`  | Bad Request           | Missing `file` or `type` form field, or invalid `type` value.         |
| `401 Unauthorized`          | `urn:unihub:error:unauthorized` | Unauthorized          | Missing, expired, invalid-signature, or Redis-blacklisted Bearer JWT. |
| `403 Forbidden`             | `urn:unihub:error:forbidden`    | Forbidden             | JWT `role` is not `ORGANIZER`.                                        |
| `422 Unprocessable Content` | `urn:unihub:error:validation`   | Unprocessable Content | File is empty, exceeds 5MB limit, or unsupported file format / MIME.  |
| `500 Internal Server Error` | `urn:unihub:error:internal`     | Internal Server Error | Storage provider error during MinIO upload.                           |

**Error Response Example (422 Unprocessable Content):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:validation",
    "title": "Unprocessable Content",
    "status": 422,
    "detail": "File size exceeds the 5MB maximum limit.",
    "instance": "/api/v1/admin/workshops/media/upload",
    "validationErrors": ["file: File size exceeds the 5MB maximum limit."]
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

#### Side Effects

Stores an object in the `unihub-media` MinIO bucket. No database record is created or modified.

#### Spec Traceability

- **Satisfies:** FR-01, FR-02, FR-31, FR-32, FR-33, FR-34, FR-35, BR-15, BR-16
- **Scenarios:** 13, 14, 20, 21

### POST /api/v1/admin/workshops/drafts

**Description:** Unified Save as Draft — creates a new `DRAFT` workshop when no `id` is supplied, or updates an existing `DRAFT` workshop when `id` is present.  
**Required Role / Auth:** `ORGANIZER` — JWT with `role = ORGANIZER` and `org` claim present.  
**Idempotency Requirement:** Not Required — this endpoint carries no financial consequence. A repeated call with identical inputs for a create will produce a second workshop record (not idempotent by design, consistent with REST `POST` semantics). Clients should use the returned `id` to subsequent updates, which are disambiguated by `version`.

#### Request

**Headers:**

| Header          | Required | Description        |
| :-------------- | :------- | :----------------- |
| `Authorization` | Yes      | `Bearer <JWT>`     |
| `Content-Type`  | Yes      | `application/json` |

**Path Parameters:** None.

**Query Parameters:** None.

**Request Body:**

```json
{
  "id": "uuid | null — omit or null for create; provide for update",
  "version": "integer | required when id is present; omit on create",
  "title": "string — required",
  "description": "string | null — optional",
  "coverImageUrl": "string | null — optional, valid URL",
  "location": "string | null — optional",
  "timezone": "string | null — optional, IANA timezone identifier; defaults to Asia/Ho_Chi_Minh",
  "startsAt": "string | null — ISO 8601 datetime; optional unless speakers provided",
  "endsAt": "string | null — ISO 8601 datetime; optional unless speakers provided",
  "capacity": "integer | null — optional; must be ≥ 1 if provided",
  "price": "number | null — optional; defaults to 0 if omitted",
  "currency": "string | null — optional, 3-letter ISO 4217 code; defaults to USD if omitted",
  "speakers": [
    {
      "name": "string — required",
      "email": "string — required, valid email format",
      "bio": "string | null — optional",
      "avatarUrl": "string | null — optional, valid HTTP/HTTPS URL",
      "linkedinUrl": "string | null — optional, valid HTTP/HTTPS URL",
      "sortOrder": "integer — optional, defaults to 0"
    }
  ]
}
```

**Validation Rules:**

- `id` — Optional UUID. When present: must reference an existing workshop in `DRAFT` status belonging to the caller's organization. When absent (or `null`): triggers a create operation.
- `version` — Required integer when `id` is present; must match the current stored `version` of the workshop. Omitted on create (the system initializes `version = 0`).
- `title` — Required; must not be blank or whitespace-only; maximum 255 characters.
- `description` — Optional; maximum 5000 characters. Blank string normalized to null.
- `coverImageUrl` — Optional; if provided, must be a syntactically valid HTTP or HTTPS URL; maximum 2048 characters. Blank string normalized to null.
- `location` — Optional; maximum 255 characters (matching database column `VARCHAR(255)`). Blank string normalized to null.
- `timezone` — Optional; if provided, must be a valid IANA timezone identifier (e.g. `"Asia/Ho_Chi_Minh"`, `"UTC"`); maximum 64 characters; defaults to `"Asia/Ho_Chi_Minh"` if omitted.
- `startsAt` — Optional ISO 8601 datetime; required when `speakers` array is non-empty; must be a future or current instant (no past enforcement at draft stage).
- `endsAt` — Optional ISO 8601 datetime; required when `speakers` array is non-empty; must be strictly after `startsAt` when both are present.
- `capacity` — Optional positive integer in range `[1, 1000000]`.
- `price` — Optional non-negative decimal (≥ 0.00, maximum 99,999,999.99, at most 2 decimal places); defaults to 0 if omitted.
- `currency` — Optional valid 3-letter ISO 4217 currency code (case-insensitive, normalized to uppercase); defaults to `USD` if omitted.
- `speakers` — Optional array; may be empty `[]` or omitted entirely (treated as "no change" only on update if omitted; "clear all" if `[]` is supplied explicitly on update — see Cross-Endpoint Consistency Notes). Each entry:
  - `name` — Required; must not be blank; maximum 200 characters.
  - `email` — Required; must be a syntactically valid email address; trimmed and lowercased before storage.
  - `bio` — Optional; maximum 2 000 characters. Blank string normalized to null.
  - `avatarUrl` — Optional; if provided, must be a syntactically valid HTTP or HTTPS URL; maximum 2048 characters. Blank string normalized to null.
  - `linkedinUrl` — Optional; if provided, must be a syntactically valid HTTP or HTTPS URL; maximum 2048 characters. Blank string normalized to null.
  - `sortOrder` — Optional non-negative integer; defaults to 0 if omitted.
- Intra-request: all speaker `email` values (after normalization) must be unique across the array.

> **Speaker update semantics on update:** When `speakers` is **present** (including `[]`) in an update request, the stored speaker list is fully replaced. When `speakers` is **omitted entirely** from an update request, existing speakers are left unchanged.

#### Response

**Success — 201 Created (new draft):**

```json
{
  "success": true,
  "message": "Workshop draft created successfully.",
  "data": {
    "id": "uuid",
    "organizationId": "uuid",
    "title": "string",
    "slug": "string",
    "description": "string | null",
    "coverImageUrl": "string | null",
    "location": "string | null",
    "timezone": "string",
    "startsAt": "ISO 8601 datetime | null",
    "endsAt": "ISO 8601 datetime | null",
    "capacity": "integer | null",
    "price": "number",
    "currency": "string",
    "status": "DRAFT",
    "version": 0,
    "createdAt": "ISO 8601 datetime",
    "updatedAt": "ISO 8601 datetime",
    "speakers": [
      {
        "id": "uuid",
        "name": "string",
        "email": "string",
        "bio": "string | null",
        "avatarUrl": "string | null",
        "linkedinUrl": "string | null",
        "sortOrder": 0
      }
    ]
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Success — 200 OK (existing draft updated):**

Same shape as `201` above; `version` reflects the incremented value after the update; `slug` reflects the regenerated value if `title` changed.

**Error Responses:**

| Status                      | Error Type (`error.type`)       | Title                 | Condition                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| :-------------------------- | :------------------------------ | :-------------------- | :-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `400 Bad Request`           | `urn:unihub:error:bad-request`  | Bad Request           | Malformed JSON body or missing `Content-Type: application/json` header.                                                                                                                                                                                                                                                                                                                                                                             |
| `401 Unauthorized`          | `urn:unihub:error:unauthorized` | Unauthorized          | Missing, expired, invalid-signature, or Redis-blacklisted Bearer JWT.                                                                                                                                                                                                                                                                                                                                                                               |
| `403 Forbidden`             | `urn:unihub:error:forbidden`    | Forbidden             | JWT `role` is not `ORGANIZER`, `org` claim missing, or `id` refers to a workshop belonging to a different organization.                                                                                                                                                                                                                                                                                                                             |
| `404 Not Found`             | `urn:unihub:error:not-found`    | Not Found             | `id` is supplied but no workshop with that identifier exists.                                                                                                                                                                                                                                                                                                                                                                                       |
| `409 Conflict`              | `urn:unihub:error:conflict`     | Conflict              | `id` is supplied and `version` does not match the stored version (optimistic locking), or `id` refers to a workshop whose status is not `DRAFT` (e.g., `PUBLISHED`, `COMPLETED`, `CANCELED`).                                                                                                                                                                                                                                                       |
| `422 Unprocessable Content` | `urn:unihub:error:validation`   | Unprocessable Content | One or more validation rules failed: `title` missing/blank; `version` missing when `id` is present; `capacity < 1`; `endsAt ≤ startsAt`; speakers present but `startsAt`/`endsAt` absent; duplicate speaker emails; invalid speaker email format; speaker `name` blank; speaker schedule conflict; invalid timezone; invalid URL for `coverImageUrl` or `avatarUrl`. The `validationErrors` list identifies each specific failing field and reason. |
| `500 Internal Server Error` | `urn:unihub:error:internal`     | Internal Server Error | Slug uniqueness retry exhausted or unexpected database error.                                                                                                                                                                                                                                                                                                                                                                                       |

**Error Response Example (422 Unprocessable Content):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:validation",
    "title": "Unprocessable Content",
    "status": 422,
    "detail": "Validation failed for 'saveDraftRequest'. 1 error(s) found.",
    "instance": "/api/v1/admin/workshops/drafts",
    "validationErrors": ["title: must not be blank"]
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Error Response Example (409 Conflict — Stale Version):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:conflict",
    "title": "Conflict",
    "status": 409,
    "detail": "Workshop version conflict: expected version 2 but found version 3.",
    "instance": "/api/v1/admin/workshops/drafts"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

#### Side Effects

On create: writes to `workshops` and optionally `workshop_speakers`. No domain events are published. No Elasticsearch index is updated.
On update: updates `workshops` and replaces `workshop_speakers`. If `coverImageUrl` was replaced or removed, the obsolete cover image object in MinIO is deleted. If `speakers` was replaced and previous speaker `avatarUrl`s are no longer present, the obsolete avatar objects in MinIO are deleted.

#### Spec Traceability

- **Satisfies:** FR-01, FR-02, FR-03, FR-04, FR-05, FR-06, FR-07, FR-08, FR-09, FR-10, FR-11, FR-12, FR-13, FR-14, FR-15, FR-16, FR-17, FR-18, FR-19, FR-20, FR-21, FR-22, FR-23, FR-24, FR-25, FR-26, FR-27, FR-28, FR-29, FR-30, FR-36, FR-37, FR-47, BR-16
- **Scenarios:** 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 17, 20, 21

### GET /api/v1/admin/workshops/{id}

**Description:** Retrieve a workshop's full details (including speakers), accessible by the owning ORGANIZER for any workshop status, for viewing or hydrating the edit form.  
**Required Role / Auth:** `ORGANIZER` — JWT with `role = ORGANIZER` and `org` claim matching the workshop's `organization_id`.  
**Idempotency Requirement:** Not Required — read-only operation; naturally safe to repeat.

#### Request

**Headers:**

| Header          | Required | Description    |
| :-------------- | :------- | :------------- |
| `Authorization` | Yes      | `Bearer <JWT>` |

**Path Parameters:**

| Parameter | Type | Description                        |
| :-------- | :--- | :--------------------------------- |
| `id`      | UUID | Unique identifier of the workshop. |

**Query Parameters:** None.

**Request Body:** None.

#### Response

**Success — 200 OK:**

```json
{
  "success": true,
  "message": "Workshop retrieved successfully.",
  "data": {
    "id": "uuid",
    "organizationId": "uuid",
    "title": "string",
    "slug": "string",
    "description": "string | null",
    "coverImageUrl": "string | null",
    "location": "string | null",
    "timezone": "string",
    "startsAt": "ISO 8601 datetime | null",
    "endsAt": "ISO 8601 datetime | null",
    "capacity": "integer | null",
    "price": "number",
    "currency": "string",
    "status": "DRAFT | PUBLISHED | COMPLETED | CANCELED",
    "version": "integer",
    "createdAt": "ISO 8601 datetime",
    "updatedAt": "ISO 8601 datetime",
    "speakers": [
      {
        "id": "11111111-2222-3333-4444-555555555555",
        "name": "Dr. Alan Turing",
        "email": "alan.turing@university.edu",
        "bio": "AI Researcher",
        "avatarUrl": "http://localhost:9000/unihub-media/speakers/avatars/660e8400-e29b-41d4-a716-446655440000.jpg",
        "linkedinUrl": "https://linkedin.com/in/alan-turing",
        "sortOrder": 0
      }
    ]
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Error Responses:**

| Status             | Error Type (`error.type`)       | Title        | Condition                                                                                                             |
| :----------------- | :------------------------------ | :----------- | :-------------------------------------------------------------------------------------------------------------------- |
| `401 Unauthorized` | `urn:unihub:error:unauthorized` | Unauthorized | Missing, expired, invalid-signature, or Redis-blacklisted Bearer JWT.                                                 |
| `403 Forbidden`    | `urn:unihub:error:forbidden`    | Forbidden    | JWT `role` is not `ORGANIZER`, or the workshop belongs to a different organization than the caller's JWT `org` claim. |
| `404 Not Found`    | `urn:unihub:error:not-found`    | Not Found    | No workshop exists with the given `id`.                                                                               |

**Error Response Example (404 Not Found):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:not-found",
    "title": "Not Found",
    "status": 404,
    "detail": "Workshop not found.",
    "instance": "/api/v1/admin/workshops/8e4b9b3b-9c1a-4a8d-9f6b-3c8b7f9f2c10"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

#### Side Effects

None. Read-only operation.

#### Spec Traceability

- **Satisfies:** FR-01, FR-02, FR-39, FR-40, FR-41, FR-42
- **Scenarios:** 15, 16, 20, 21

### DELETE /api/v1/admin/workshops/{id}

**Description:** Permanently delete a workshop that is in `DRAFT` status, cascading to all associated speaker records, and deleting all associated media assets from MinIO.  
**Required Role / Auth:** `ORGANIZER` — JWT with `role = ORGANIZER` and `org` claim matching the workshop's `organization_id`.  
**Idempotency Requirement:** Not Required — a second `DELETE` on the same already-deleted `id` returns `404`, which is an acceptable idempotent behavior for a hard delete (the caller's intent — resource is gone — is satisfied).

#### Request

**Headers:**

| Header          | Required | Description    |
| :-------------- | :------- | :------------- |
| `Authorization` | Yes      | `Bearer <JWT>` |

**Path Parameters:**

| Parameter | Type | Description                                  |
| :-------- | :--- | :------------------------------------------- |
| `id`      | UUID | Unique identifier of the workshop to delete. |

**Query Parameters:** None.

**Request Body:** None.

#### Response

**Success — 204 No Content:**

No response body.

**Error Responses:**

| Status             | Error Type (`error.type`)       | Title        | Condition                                                                                                             |
| :----------------- | :------------------------------ | :----------- | :-------------------------------------------------------------------------------------------------------------------- |
| `401 Unauthorized` | `urn:unihub:error:unauthorized` | Unauthorized | Missing, expired, invalid-signature, or Redis-blacklisted Bearer JWT.                                                 |
| `403 Forbidden`    | `urn:unihub:error:forbidden`    | Forbidden    | JWT `role` is not `ORGANIZER`, or the workshop belongs to a different organization than the caller's JWT `org` claim. |
| `404 Not Found`    | `urn:unihub:error:not-found`    | Not Found    | No workshop exists with the given `id`.                                                                               |
| `409 Conflict`     | `urn:unihub:error:conflict`     | Conflict     | The workshop exists but is not in `DRAFT` status (e.g., `PUBLISHED`, `COMPLETED`, `CANCELED`).                        |

**Error Response Example (409 Conflict — Not in DRAFT Status):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:conflict",
    "title": "Conflict",
    "status": 409,
    "detail": "Workshop cannot be deleted because it is not in DRAFT status.",
    "instance": "/api/v1/admin/workshops/8e4b9b3b-9c1a-4a8d-9f6b-3c8b7f9f2c10"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

#### Side Effects

On success: all `workshop_speakers` rows associated with the deleted workshop are removed via `ON DELETE CASCADE` at the database level. Associated `coverImageUrl` and all speaker `avatarUrl`s hosted in MinIO bucket `unihub-media` are deleted from MinIO storage. No domain events are published. No Elasticsearch documents are deleted (search indexing is managed in Feature 2.2).

#### Spec Traceability

- **Satisfies:** FR-01, FR-02, FR-43, FR-44, FR-45, FR-46, BR-09, BR-16
- **Scenarios:** 18, 19, 20, 21

### GET /api/v1/admin/workshops

**Description:** Retrieves a paginated list of workshops owned by the caller's organization (`organization_id == jwt.org`), with optional filters for status, price type (`FREE` vs `PAID`), keyword search `q` (across `title`, `slug`, `location`, and `description`), date range, and sorting (by `capacity`, `price`, `createdAt`, or `startsAt`). Ensures strict tenant isolation and read-after-write consistency by querying the PostgreSQL Primary DB (`@Transactional(readOnly = true)`).  
**Required Role / Auth:** `ORGANIZER` — JWT with `role = ORGANIZER` and non-null `org`.  
**Idempotency Requirement:** Not Required (safe, read-only GET operation).

#### Request

**Headers:**

| Header          | Required | Description        |
| :-------------- | :------- | :----------------- |
| `Authorization` | Yes      | `Bearer <JWT>`     |
| `Accept`        | No       | `application/json` |

**Query Parameters:**

| Parameter   | Type    | Required | Default          | Description                                                                                                                                                                                     |
| :---------- | :------ | :------- | :--------------- | :---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `status`    | String  | No       | _(all)_          | Workshop status filter: `DRAFT`, `PUBLISHED`, `COMPLETED`, `CANCELED`. If omitted, all statuses returned.                                                                                       |
| `priceType` | String  | No       | _(all)_          | Pricing filter: `FREE` (price = 0) or `PAID` (price > 0). If omitted, both free and paid workshops returned.                                                                                    |
| `q`         | String  | No       | —                | Case-insensitive partial matching across workshop `title`, `slug`, `location`, and `description` (max 100 characters).                                                                          |
| `from`      | String  | No       | —                | ISO 8601 UTC timestamp (`YYYY-MM-DDTHH:mm:ssZ`). Filters workshops where `starts_at >= from`.                                                                                                   |
| `to`        | String  | No       | —                | ISO 8601 UTC timestamp (`YYYY-MM-DDTHH:mm:ssZ`). Filters workshops where `starts_at <= to`.                                                                                                     |
| `sort`      | String  | No       | `createdAt,desc` | Sort field and direction. Supported: `capacity,asc`, `capacity,desc`, `price,asc`, `price,desc`, `createdAt,desc`, `createdAt,asc`, `startsAt,asc`, `startsAt,desc`.                            |
| `cursor`    | String  | No       | —                | Keyset pagination cursor (opaque Base64-encoded string representing the last item from the previous page). Bound to the filter set (`status`, `priceType`, `q`, `from`, `to`) that produced it. |
| `size`      | Integer | No       | `20`             | Number of workshops per page. Min: 1, Max: 100.                                                                                                                                                 |

#### Response

**Success — 200 OK:**

```json
{
  "success": true,
  "message": "Workshops retrieved successfully.",
  "data": {
    "items": [
      {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "slug": "introduction-to-ai-a1b2",
        "title": "Introduction to AI",
        "description": "Comprehensive introduction to artificial intelligence.",
        "coverImageUrl": "http://localhost:9000/unihub-media/workshops/covers/550e8400-e29b-41d4-a716-446655440000.jpg",
        "location": "Auditorium B",
        "timezone": "Asia/Ho_Chi_Minh",
        "startsAt": "2026-10-15T09:00:00Z",
        "endsAt": "2026-10-15T12:00:00Z",
        "capacity": 100,
        "price": 0.0,
        "currency": "USD",
        "status": "DRAFT",
        "version": 0,
        "speakerCount": 2,
        "createdAt": "2026-09-25T10:00:00Z",
        "updatedAt": "2026-09-25T10:30:00Z"
      }
    ],
    "cursor": {
      "nextCursor": "eyJjcmVhdGVkQXQiOiIyMDI2LTA5LTI1VDEwOjAwOjAwWiIsImlkIjoiNTUwZTg0MDAtZTI5Yi00MWQ0LWE3MTYtNDQ2NjU1NDQwMDAwIn0=",
      "hasMore": false
    }
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Error Responses:**

| Status                      | Error Type (`error.type`)       | Title                 | Condition                                                                                                                                                                                                                  |
| :-------------------------- | :------------------------------ | :-------------------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `400 Bad Request`           | `urn:unihub:error:bad-request`  | Bad Request           | Invalid `status` or `priceType` enum, unsupported `sort` parameter, invalid ISO 8601 date, invalid `size`, malformed `cursor`, or a `cursor` issued for a different filter set (`status`, `priceType`, `q`, `from`, `to`). |
| `401 Unauthorized`          | `urn:unihub:error:unauthorized` | Unauthorized          | Missing, expired, invalid-signature, or Redis-blacklisted Bearer JWT.                                                                                                                                                      |
| `403 Forbidden`             | `urn:unihub:error:forbidden`    | Forbidden             | Caller `role` is not `ORGANIZER`, or caller has no associated `org` claim.                                                                                                                                                 |
| `500 Internal Server Error` | `urn:unihub:error:internal`     | Internal Server Error | Unexpected database failure.                                                                                                                                                                                               |

#### Side Effects

None. This is a read-only query that hits the PostgreSQL Primary DB to ensure read-after-write consistency.

#### Spec Traceability

- **Satisfies:** FR-01, FR-02, FR-48, FR-49, FR-50, FR-51, FR-52, FR-53, FR-54, FR-55, FR-56, FR-57, BR-01, BR-17
- **Scenarios:** 20, 21, 22, 23, 24, 25, 26, 27, 28, 29

## 4. Rate Limiting Behavior

All five endpoints in this contract fall under the **Default Tier** of the Bucket4j + Redis Token Bucket enforced at the Core Backend Monolith filter layer (not at Nginx). _(Source: `docs/system-design.md` — Rate Limiting Tiers)_

| Endpoint                                    | Tier    |  Capacity  | Refill Rate  | Key                               |
| :------------------------------------------ | :------ | :--------: | :----------: | :-------------------------------- |
| `POST /api/v1/admin/workshops/media/upload` | Default | 100 tokens | 100 per 60 s | `jwt.sub` (authenticated user ID) |
| `POST /api/v1/admin/workshops/drafts`       | Default | 100 tokens | 100 per 60 s | `jwt.sub`                         |
| `GET /api/v1/admin/workshops/{id}`          | Default | 100 tokens | 100 per 60 s | `jwt.sub`                         |
| `DELETE /api/v1/admin/workshops/{id}`       | Default | 100 tokens | 100 per 60 s | `jwt.sub`                         |
| `GET /api/v1/admin/workshops`               | Default | 100 tokens | 100 per 60 s | `jwt.sub`                         |

The Default Tier is appropriate because:

- All callers are authenticated ORGANIZERs (low population, no anonymous spike risk).
- Save-as-Draft is an infrequent creative action; 100 requests per minute per user is a ceiling no legitimate use case will reach.
- No dedicated tier (Registration, Auth, Stream) applies to `/api/v1/admin/workshops/**` paths.

**When the bucket is exhausted**, the Core Backend returns:

| Status                  | Error Type (`error.type`)       | Title             | Condition                                         |
| :---------------------- | :------------------------------ | :---------------- | :------------------------------------------------ |
| `429 Too Many Requests` | `urn:unihub:error:rate-limited` | Too Many Requests | Token bucket for the caller's `jwt.sub` is empty. |

**Error Response Example (429 Too Many Requests):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:rate-limited",
    "title": "Too Many Requests",
    "status": 429,
    "detail": "Rate limit exceeded. Try again in 30 seconds.",
    "instance": "/api/v1/admin/workshops/drafts"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

Response headers included on `429`:

| Header                  | Description                                             |
| :---------------------- | :------------------------------------------------------ |
| `X-RateLimit-Limit`     | Maximum tokens in the bucket (100).                     |
| `X-RateLimit-Remaining` | Tokens remaining after this request (0 on a 429).       |
| `X-RateLimit-Reset`     | Unix epoch second at which the bucket will next refill. |

Clients SHOULD implement exponential backoff upon receiving `429`. The frontend shows a "Please wait" message and retries with backoff per the system's client-side contract.

## 5. Idempotency Behavior

No endpoint in this contract requires an idempotency key:

- **`POST /api/v1/admin/workshops/drafts` (create):** Standard `POST` semantics; repeated requests create additional drafts. Clients receive the `id` on the first call and should include it for subsequent saves. No financial consequence.
- **`POST /api/v1/admin/workshops/drafts` (update):** Protected by optimistic locking (`version`). A retried update with the same `version` will succeed only once; subsequent retries will receive `409 Conflict` (version mismatch), preventing accidental double-update.
- **`GET /api/v1/admin/workshops/{id}`:** Read-only; naturally idempotent.
- **`DELETE /api/v1/admin/workshops/{id}`:** Hard delete; second call returns `404`, which is an acceptable safe outcome.
- **`GET /api/v1/admin/workshops`:** Read-only; naturally idempotent.

## 6. Cross-Endpoint Consistency Notes

1. **Workshop resource shape:** The response body from `POST /api/v1/admin/workshops/drafts` (both 201 and 200) and `GET /api/v1/admin/workshops/{id}` use the **same field names, types, and nesting structure** for the workshop object and the embedded `speakers` array. This ensures clients can use a single deserialization model for any workshop response.
2. **`urn:unihub:error:conflict` error type** is reused for both `POST /api/v1/admin/workshops/drafts` (update on non-DRAFT or version mismatch) and `DELETE /api/v1/admin/workshops/{id}` (delete on non-DRAFT), keeping the error taxonomy consistent per `docs/api-standards.md`.
3. **`urn:unihub:error:not-found` error type** is shared across all three endpoints that accept a workshop `{id}`, ensuring clients can write one `404` handler.
4. **Null vs omitted semantics for `speakers` on update:** To avoid ambiguity between "caller forgot to include speakers" and "caller intends to clear all speakers," the contract treats `speakers` field **presence** as the discriminator:
   - Field **present** (even as `[]`) → full replace of the speaker list.
   - Field **absent** → existing speakers unchanged.
     This behavior is explicitly documented in the Request Body section for `POST /api/v1/admin/workshops/drafts` and must be documented in the client SDK / frontend integration guide.
