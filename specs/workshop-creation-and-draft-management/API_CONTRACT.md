# API Contract: Workshop Creation and Draft Management

> **Status:** APPROVED  
> **Spec:** `specs/workshop-creation-and-draft-management/SPEC.md`  
> **Last revised:** 2026-09-25

## 1. Overview

This contract defines the three endpoints exposed by the `workshops` module for Feature 2.1 — Workshop Creation and Draft Management. All endpoints are ORGANIZER-only and serve the use cases of saving a workshop as a draft (create or update), previewing a draft, and permanently deleting a draft. This contract traces directly to `SPEC.md` for this feature and conforms in full to `docs/api-standards.md`.

## 2. Global Standards Applied

- **URI structure / versioning:** All endpoints are prefixed with `/api/v1/`. Resources are named with plural nouns following RESTful conventions. _(Source: `docs/api-standards.md`)_
- **Pagination convention:** No collection endpoint exists in this feature; pagination is not applicable.
- **Success response shape:** `ApiResponse<T>` envelope — `{ "success": true, "message": "...", "data": { ... } }`. `204 No Content` responses carry no body.
- **Error response format:** RFC 7807 Problem Details — `{ "type": "...", "title": "...", "status": <int>, "detail": "...", "instance": "..." }` with `Content-Type: application/problem+json`. _(Source: `docs/api-standards.md`)_
- **Idempotency-key convention:** Not applicable for this feature (see per-endpoint decisions below).
- **Auth header convention:** `Authorization: Bearer <JWT>` — RS256 signed, required on every endpoint.
- **Date/time formatting convention:** ISO 8601 with UTC offset — `YYYY-MM-DDTHH:mm:ssZ`.

## 3. Endpoints

### POST /api/v1/workshops/drafts

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
      "avatarUrl": "string | null — optional"
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
  - `name` — Required; must not be blank; maximum 255 characters.
  - `email` — Required; must be a syntactically valid email address; trimmed and lowercased before storage.
  - `bio` — Optional; maximum 2 000 characters.
  - `avatarUrl` — Optional; if provided, must be a syntactically valid URL.
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
        "avatarUrl": "string | null"
      }
    ]
  }
}
```

**Success — 200 OK (existing draft updated):**

Same shape as `201` above; `version` reflects the incremented value after the update; `slug` reflects the regenerated value if `title` changed.

**Error Responses:**

| Status                      | Error Identifier          | Condition                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| :-------------------------- | :------------------------ | :-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `400 Bad Request`           | `INVALID_REQUEST`         | Malformed JSON body or missing `Content-Type: application/json` header.                                                                                                                                                                                                                                                                                                                                                                             |
| `401 Unauthorized`          | `UNAUTHORIZED`            | No or expired or invalid JWT.                                                                                                                                                                                                                                                                                                                                                                                                                       |
| `403 Forbidden`             | `FORBIDDEN`               | JWT `role` is not `ORGANIZER`, or `id` refers to a workshop belonging to a different organization.                                                                                                                                                                                                                                                                                                                                                  |
| `404 Not Found`             | `WORKSHOP_NOT_FOUND`      | `id` is supplied but no workshop with that identifier exists.                                                                                                                                                                                                                                                                                                                                                                                       |
| `409 Conflict`              | `VERSION_CONFLICT`        | `id` is supplied and `version` does not match the stored version (optimistic locking).                                                                                                                                                                                                                                                                                                                                                              |
| `409 Conflict`              | `INVALID_WORKSHOP_STATUS` | `id` refers to a workshop whose status is not `DRAFT` (e.g., `PUBLISHED`, `COMPLETED`, `CANCELED`).                                                                                                                                                                                                                                                                                                                                                 |
| `422 Unprocessable Entity`  | `VALIDATION_ERROR`        | One or more validation rules failed: `title` missing/blank; `version` missing when `id` is present; `capacity < 1`; `endsAt ≤ startsAt`; speakers present but `startsAt`/`endsAt` absent; duplicate speaker emails; invalid speaker email format; speaker `name` blank; speaker schedule conflict; invalid timezone; invalid URL for `coverImageUrl` or `avatarUrl`. The `detail` field MUST identify the specific failing field(s) and the reason. |
| `500 Internal Server Error` | `INTERNAL_ERROR`          | Slug uniqueness retry exhausted or unexpected database error.                                                                                                                                                                                                                                                                                                                                                                                       |

#### Side Effects

None. No domain events are published. No Elasticsearch index is updated. The operation is scoped entirely to the PostgreSQL `workshops` and `workshop_speakers` tables.

#### Spec Traceability

- **Satisfies:** FR-01, FR-02, FR-03, FR-04, FR-05, FR-06, FR-07, FR-08, FR-09, FR-10, FR-11, FR-12, FR-13, FR-14, FR-15, FR-16, FR-17, FR-18, FR-19, FR-20, FR-21, FR-22, FR-23, FR-24, FR-25, FR-26, FR-27, FR-28, FR-29, FR-30
- **Scenarios:** 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 17, 18

### GET /api/v1/workshops/{id}

**Description:** Preview a workshop's full details (including speakers), accessible by the owning ORGANIZER for any workshop status.  
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
        "id": "uuid",
        "name": "string",
        "email": "string",
        "bio": "string | null",
        "avatarUrl": "string | null"
      }
    ]
  }
}
```

**Error Responses:**

| Status             | Error Identifier     | Condition                                                                                                             |
| :----------------- | :------------------- | :-------------------------------------------------------------------------------------------------------------------- |
| `401 Unauthorized` | `UNAUTHORIZED`       | No or expired or invalid JWT.                                                                                         |
| `403 Forbidden`    | `FORBIDDEN`          | JWT `role` is not `ORGANIZER`, or the workshop belongs to a different organization than the caller's JWT `org` claim. |
| `404 Not Found`    | `WORKSHOP_NOT_FOUND` | No workshop exists with the given `id`.                                                                               |

#### Side Effects

None. Read-only operation.

#### Spec Traceability

- **Satisfies:** FR-01, FR-02, FR-31, FR-32, FR-33, FR-34
- **Scenarios:** 13, 14, 17, 18

### DELETE /api/v1/workshops/{id}

**Description:** Permanently delete a workshop that is in `DRAFT` status, including all associated speaker records.  
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

| Status             | Error Identifier          | Condition                                                                                                             |
| :----------------- | :------------------------ | :-------------------------------------------------------------------------------------------------------------------- |
| `401 Unauthorized` | `UNAUTHORIZED`            | No or expired or invalid JWT.                                                                                         |
| `403 Forbidden`    | `FORBIDDEN`               | JWT `role` is not `ORGANIZER`, or the workshop belongs to a different organization than the caller's JWT `org` claim. |
| `404 Not Found`    | `WORKSHOP_NOT_FOUND`      | No workshop exists with the given `id`.                                                                               |
| `409 Conflict`     | `INVALID_WORKSHOP_STATUS` | The workshop exists but is not in `DRAFT` status (e.g., PUBLISHED, COMPLETED, CANCELED).                              |

#### Side Effects

On success: all `workshop_speakers` rows associated with the deleted workshop are removed via `ON DELETE CASCADE` at the database level. No domain events are published. No Elasticsearch documents are deleted (search indexing is managed in Feature 2.2).

#### Spec Traceability

- **Satisfies:** FR-01, FR-02, FR-35, FR-36, FR-37, FR-38, BR-09
- **Scenarios:** 15, 16, 17, 18

## 4. Rate Limiting Behavior

All three endpoints in this contract fall under the **Default Tier** of the Bucket4j + Redis Token Bucket enforced at the Core Backend Monolith filter layer (not at Nginx). _(Source: `docs/system-design.md` — Rate Limiting Tiers)_

| Endpoint                        | Tier    |  Capacity  | Refill Rate  | Key                               |
| :------------------------------ | :------ | :--------: | :----------: | :-------------------------------- |
| `POST /api/v1/workshops/drafts` | Default | 100 tokens | 100 per 60 s | `jwt.sub` (authenticated user ID) |
| `GET /api/v1/workshops/{id}`    | Default | 100 tokens | 100 per 60 s | `jwt.sub`                         |
| `DELETE /api/v1/workshops/{id}` | Default | 100 tokens | 100 per 60 s | `jwt.sub`                         |

The Default Tier is appropriate because:

- All callers are authenticated ORGANIZERs (low population, no anonymous spike risk).
- Save-as-Draft is an infrequent creative action; 100 requests per minute per user is a ceiling no legitimate use case will reach.
- No dedicated tier (Registration, Auth, Stream) applies to `/api/v1/workshops/**` paths.

**When the bucket is exhausted**, the Core Backend returns:

| Status                  | Error Identifier | Condition                                         |
| :---------------------- | :--------------- | :------------------------------------------------ |
| `429 Too Many Requests` | `RATE_LIMITED`   | Token bucket for the caller's `jwt.sub` is empty. |

Response headers included on `429`:

| Header                  | Description                                             |
| :---------------------- | :------------------------------------------------------ |
| `X-RateLimit-Limit`     | Maximum tokens in the bucket (100).                     |
| `X-RateLimit-Remaining` | Tokens remaining after this request (0 on a 429).       |
| `X-RateLimit-Reset`     | Unix epoch second at which the bucket will next refill. |

Clients SHOULD implement exponential backoff upon receiving `429`. The frontend shows a "Please wait" message and retries with backoff per the system's client-side contract.

## 5. Idempotency Behavior

No endpoint in this contract requires an idempotency key:

- **`POST /api/v1/workshops/drafts` (create):** Standard `POST` semantics; repeated requests create additional drafts. Clients receive the `id` on the first call and should include it for subsequent saves. No financial consequence.
- **`POST /api/v1/workshops/drafts` (update):** Protected by optimistic locking (`version`). A retried update with the same `version` will succeed only once; subsequent retries will receive `409 Conflict` (version mismatch), preventing accidental double-update.
- **`GET /api/v1/workshops/{id}`:** Read-only; naturally idempotent.
- **`DELETE /api/v1/workshops/{id}`:** Hard delete; second call returns `404`, which is an acceptable safe outcome.

## 6. Cross-Endpoint Consistency Notes

1. **Workshop resource shape:** The response body from `POST /api/v1/workshops/drafts` (both 201 and 200) and `GET /api/v1/workshops/{id}` use the **same field names, types, and nesting structure** for the workshop object and the embedded `speakers` array. This ensures clients can use a single deserialization model for any workshop response.
2. **`INVALID_WORKSHOP_STATUS` error identifier** is reused for both `POST /api/v1/workshops/drafts` (update on non-DRAFT) and `DELETE /api/v1/workshops/{id}` (delete on non-DRAFT), keeping the error taxonomy consistent rather than introducing per-endpoint error names for the same underlying condition.
3. **`WORKSHOP_NOT_FOUND` error identifier** is shared across all three endpoints that accept a workshop `{id}`, ensuring clients can write one `404` handler.
4. **Null vs omitted semantics for `speakers` on update:** To avoid ambiguity between "caller forgot to include speakers" and "caller intends to clear all speakers," the contract treats `speakers` field **presence** as the discriminator:
   - Field **present** (even as `[]`) → full replace of the speaker list.
   - Field **absent** → existing speakers unchanged.
     This behavior is explicitly documented in the Request Body section for `POST /api/v1/workshops/drafts` and must be documented in the client SDK / frontend integration guide.
