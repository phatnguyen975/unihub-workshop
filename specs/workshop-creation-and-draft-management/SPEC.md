# Spec: Workshop Creation and Draft Management

> **Status:** APPROVED  
> **Domain:** 2 — Workshop Lifecycle Management  
> **Owner/area:** `workshops` module — backend (`apps/backend/`)  
> **Last revised:** 2026-09-25 — first version

## 1. Overview

This feature allows an authenticated **ORGANIZER** to compose a workshop incrementally and save it as a `DRAFT` before it is ready to publish. Drafts are private to the owning organization: they are not visible to students, not indexed for search, and not eligible for registration. A single "Save as Draft" action covers both first-time creation and subsequent updates to the same draft, disambiguated by whether the caller supplies an existing workshop identifier. An ORGANIZER can also upload media assets (workshop cover images and speaker portraits) via a dedicated upload endpoint, retrieve full workshop details to inspect or hydrate the edit form, and permanently delete a draft if it is no longer needed. This feature replaces the previous manual process and is a prerequisite for Feature 2.2 (workshop publishing and lifecycle management).

## 2. Actors & Preconditions

| Actor              | Precondition                                                                                                                                                  |
| :----------------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **ORGANIZER**      | Authenticated with a valid, non-expired JWT whose `role` claim equals `ORGANIZER` and whose `org` claim identifies the organization this workshop belongs to. |
| _(No other actor)_ | Students, STAFF, and unauthenticated callers may not trigger any operation in this feature.                                                                   |

## 3. Functional Requirements

### 3.1 Authorization & Identity

- **FR-01:** The system SHALL reject any request in this feature that does not carry a valid JWT with `401 Unauthorized`.
- **FR-02:** The system SHALL reject any request in this feature that carries a valid JWT whose `role` claim is not `ORGANIZER` with `403 Forbidden`.
- **FR-03:** The system SHALL scope every workshop created by this feature to the `organization_id` extracted from the caller's JWT; the caller may not supply or override the `organization_id`.

### 3.2 Save as Draft (Unified Upsert)

- **FR-04:** The system SHALL create a new workshop record in `DRAFT` status when a Save as Draft request is received and no workshop identifier is supplied in the request body.
- **FR-05:** The system SHALL update an existing `DRAFT` workshop when a Save as Draft request is received and a workshop identifier is supplied in the request body.
- **FR-06:** The system SHALL reject a Save as Draft update (FR-05) with `403 Forbidden` when the supplied workshop identifier refers to a workshop that belongs to a different organization than the caller's JWT `org` claim.
- **FR-07:** The system SHALL reject a Save as Draft update (FR-05) with `404 Not Found` when the supplied workshop identifier does not exist in the system.
- **FR-08:** The system SHALL reject a Save as Draft update (FR-05) with `409 Conflict` when the supplied `version` does not match the stored version of the workshop (optimistic locking violation).
- **FR-09:** The system SHALL reject a Save as Draft update (FR-05) with `422 Unprocessable Entity` when no `version` is supplied in the request body for an update operation (i.e., workshop identifier is present but `version` is absent).
- **FR-10:** The system SHALL reject a Save as Draft operation (both create and update) with `409 Conflict` when the status of the targeted workshop is not `DRAFT` (i.e., the workshop has already been published, completed, or canceled).

### 3.3 Required Fields and Validation Rules

- **FR-11:** The system SHALL reject a Save as Draft request with `422 Unprocessable Entity` when `title` is absent, blank, or exceeds 255 characters.
- **FR-12:** The system SHALL reject a Save as Draft request with `422 Unprocessable Entity` when `description` exceeds 5000 characters. Blank string is normalized to null.
- **FR-13:** The system SHALL reject a Save as Draft request with `422 Unprocessable Entity` when `cover_image_url`, if provided, is not a valid HTTP or HTTPS URL or exceeds 2048 characters. Blank string is normalized to null.
- **FR-14:** The system SHALL reject a Save as Draft request with `422 Unprocessable Entity` when `location`, if provided, exceeds 255 characters. Blank string is normalized to null.
- **FR-15:** The system SHALL reject a Save as Draft request with `422 Unprocessable Entity` when `timezone`, if provided, is not a valid IANA timezone identifier or exceeds 64 characters. Defaults to `Asia/Ho_Chi_Minh` if omitted.
- **FR-16:** The system SHALL reject a Save as Draft request with `422 Unprocessable Entity` when `capacity`, if provided, is not in the range [1, 1000000].
- **FR-17:** The system SHALL reject a Save as Draft request with `422 Unprocessable Entity` when `price`, if provided, is negative, exceeds 99,999,999.99, or has more than 2 decimal places. Defaults to 0 if omitted.
- **FR-18:** The system SHALL reject a Save as Draft request with `422 Unprocessable Entity` when `currency`, if provided, is not a valid 3-letter ISO 4217 currency code. Defaults to `USD` if omitted. Case-insensitive and normalized to uppercase.
- **FR-19:** The system SHALL reject a Save as Draft request with `422 Unprocessable Entity` when `starts_at` and `ends_at` are both present and `ends_at` is not strictly after `starts_at`.

### 3.4 Slug Management

- **FR-20:** The system SHALL automatically generate a URL-safe, unique `slug` from the `title` upon the first Save as Draft (create), supporting both Vietnamese and English characters via transliteration to ASCII kebab-case.
- **FR-21:** The system SHALL automatically regenerate and update the `slug` whenever the `title` changes during a Save as Draft update, while the workshop remains in `DRAFT` status.
- **FR-22:** The system SHALL guarantee `slug` uniqueness across all workshops by appending a short random suffix to the generated slug when a collision is detected.

### 3.5 Speaker Management

- **FR-23:** The system SHALL accept `speakers` as an optional array in the Save as Draft request (with required fields `name`, `email` and optional metadata specified in FR-47); omitting the field is valid and results in no speakers being stored.
- **FR-24:** The system SHALL replace the entire speaker list for the workshop on every Save as Draft update that includes a `speakers` field (full replace, not merge).
- **FR-25:** The system SHALL reject a Save as Draft request with `422 Unprocessable Entity` when the `speakers` array contains two or more entries with the same email address (intra-request duplicate).
- **FR-26:** The system SHALL reject a Save as Draft request with `422 Unprocessable Entity` when the `speakers` array is non-empty and `starts_at` or `ends_at` is absent (schedule is required to perform overlap detection).
- **FR-27:** The system SHALL reject a Save as Draft request with `422 Unprocessable Entity` when one or more speaker emails conflict with a confirmed booking at another workshop (status is `PUBLISHED` or `COMPLETED`) whose time interval overlaps the proposed `starts_at`–`ends_at` window, excluding the current workshop being updated.
- **FR-28:** The system SHALL normalize every speaker email to lowercase and trim surrounding whitespace before storing it and before performing overlap checks.
- **FR-29:** The system SHALL reject a Save as Draft request with `422 Unprocessable Entity` when any speaker entry is missing the required `name` field.
- **FR-30:** The system SHALL reject a Save as Draft request with `422 Unprocessable Entity` when any speaker entry contains an `email` that is not a syntactically valid email address.

### 3.6 Media Upload & Storage

- **FR-31:** The system SHALL accept a multipart/form-data request at `POST /api/v1/admin/workshops/media/upload` containing a binary `file` and a `type` parameter with allowed values `COVER_IMAGE` or `SPEAKER_AVATAR`.
- **FR-32:** The system SHALL validate that `file` is non-empty, does not exceed 5MB (5,242,880 bytes), and has a MIME type of `image/jpeg`, `image/jpg`, `image/png`, or `image/webp` (supported file extensions: `.jpg`, `.jpeg`, `.png`, `.webp`).
- **FR-33:** The system SHALL stream the validated file to MinIO bucket `unihub-media` under `workshops/covers/{uuid}.{ext}` for `COVER_IMAGE` or `speakers/avatars/{uuid}.{ext}` for `SPEAKER_AVATAR`.
- **FR-34:** The system SHALL return HTTP `201 Created` with the public media URL, storage key, and media type upon successful upload, without creating or modifying any database record.
- **FR-35:** The system SHALL reject media upload requests with `422 Unprocessable Entity` if the file is empty, exceeds 5MB, or is not an allowed image format.

### 3.7 Media Lifecycle & Cleanup

- **FR-36:** When an existing draft workshop is updated via Save as Draft and the stored `cover_image_url` is replaced or removed, the system SHALL delete the previous cover image object from MinIO if it was stored in the system's MinIO storage.
- **FR-37:** When an existing draft workshop is updated via Save as Draft and the speaker list is replaced, the system SHALL delete previous speaker avatar objects from MinIO for any speakers whose `avatar_url` is no longer present in the updated list.
- **FR-38:** When a draft workshop is deleted via Delete Draft, the system SHALL delete its associated cover image and all attached speaker avatars from MinIO.

### 3.8 Workshop Details for Organizer

- **FR-39:** The system SHALL return full workshop details (including the speaker list) for a given workshop identifier when a workshop detail request (`GET /api/v1/admin/workshops/{id}`) is received from an ORGANIZER belonging to the same organization as the workshop.
- **FR-40:** The system SHALL reject a workshop detail request with `403 Forbidden` when the workshop belongs to a different organization than the caller's JWT `org` claim.
- **FR-41:** The system SHALL reject a workshop detail request with `404 Not Found` when the workshop identifier does not exist.
- **FR-42:** The system SHALL allow retrieving workshop details for a workshop in any status (`DRAFT`, `PUBLISHED`, `COMPLETED`, `CANCELED`).

### 3.9 Delete Draft

- **FR-43:** The system SHALL permanently delete a workshop and all associated speakers from the database, and purge associated media from MinIO, when a Delete Draft request is received and the workshop is in `DRAFT` status and belongs to the caller's organization.
- **FR-44:** The system SHALL reject a Delete Draft request with `403 Forbidden` when the workshop belongs to a different organization than the caller's JWT `org` claim.
- **FR-45:** The system SHALL reject a Delete Draft request with `404 Not Found` when the workshop identifier does not exist.
- **FR-46:** The system SHALL reject a Delete Draft request with `409 Conflict` when the workshop is not in `DRAFT` status (deletion of published, completed, or canceled workshops is not permitted by this feature).

### 3.10 Speaker Optional Metadata

- **FR-47:** The system SHALL accept optional speaker metadata `bio` (max 2000 chars), `avatar_url` (valid HTTP/HTTPS URL, max 2048 chars), `linkedin_url` (valid HTTP/HTTPS URL, max 2048 chars), and `sort_order` (non-negative integer, default 0) for each speaker entry in the Save as Draft request, normalizing blank string values to `null`.

### 3.11 List Workshops for Organizer

- **FR-48:** The system SHALL provide a paginated list of workshops owned by the caller's organization when `GET /api/v1/admin/workshops` is called by an authenticated user with `role = ORGANIZER`.
- **FR-49:** The system SHALL filter the returned workshops strictly by `organization_id == jwt.org`, ensuring zero cross-tenant visibility (IDOR-proof).
- **FR-50:** The system SHALL support an optional query parameter `status` (`DRAFT`, `PUBLISHED`, `COMPLETED`, `CANCELED`). When omitted, workshops across all statuses within the caller's organization SHALL be returned.
- **FR-51:** The system SHALL support an optional query parameter `q` (case-insensitive substring match against `title`, `slug`, `location`, and `description`).
- **FR-52:** The system SHALL support optional query parameters `from` and `to` (ISO 8601 timestamps) to filter workshops whose `starts_at` falls within the specified range.
- **FR-53:** The system SHALL support keyset cursor pagination with `cursor` and `size` (default: 20, max: 100), returning an envelope with `items` and cursor container `cursor: { nextCursor, hasMore }` conforming to `docs/api-standards.md`. The cursor SHALL be bound to the filter set (`status`, `priceType`, `q`, `from`, `to`) that produced it; replaying it with a different filter set SHALL be rejected with `400 Bad Request`.
- **FR-54:** The system SHALL reject requests to `GET /api/v1/admin/workshops` with `403 Forbidden` if the caller does not have `role = ORGANIZER`.
- **FR-55:** The system SHALL execute workshop listing queries against the PostgreSQL Read Replica (via `@Transactional(readOnly = true)`) to reduce primary DB load. The listing is a standalone HTTP request with no shared transaction boundary from any preceding write; per `docs/database-schema.md`, eventual consistency is acceptable for browsing queries and replica reads.
- **FR-56:** The system SHALL support an optional query parameter `priceType` (`FREE`, `PAID`). When `priceType = FREE`, the system SHALL return only workshops with `price = 0` (or `price IS NULL`). When `priceType = PAID`, the system SHALL return only workshops with `price > 0`. When omitted, workshops of both free and paid pricing SHALL be returned.
- **FR-57:** The system SHALL support an optional query parameter `sort` with allowed values `price,asc` (price ascending), `price,desc` (price descending), `capacity,asc` (capacity ascending), `capacity,desc` (capacity descending), `createdAt,desc` (newest first, default), `createdAt,asc`, `startsAt,asc`, and `startsAt,desc`. When omitted, the listing SHALL default to `createdAt,desc`.

## 4. Business Rules

- **BR-01:** An ORGANIZER is only permitted to manage workshops that belong to their own organization (`organization_id`). Cross-organization access is never permitted, even for read-only operations. _(Source: `docs/system-design.md` — RBAC rules; `docs/adrs.md` — ADR-006)_
- **BR-02:** A workshop in `DRAFT` status is never visible to students, never searchable, and is never eligible for registration or payment. _(Source: `docs/feature-catalog.md` — Feature 2.1; `docs/system-design.md`)_
- **BR-03:** The `slug` is derived deterministically from `title` and must always remain in sync with `title` while the workshop is in `DRAFT` status. _(Source: user decision, confirmed 2026-09-25)_
- **BR-04:** The `slug` must be unique across all workshops in the system (enforced by a UNIQUE constraint on the `slug` column). _(Source: `docs/database-schema.md`)_
- **BR-05:** Speaker schedule-conflict detection is global — it applies across all organizations and is not scoped to the caller's organization. _(Source: user decision, confirmed 2026-09-25)_
- **BR-06:** Speaker schedule-conflict detection excludes workshops in `CANCELED` or `DRAFT` status. Only workshops in `PUBLISHED` or `COMPLETED` status are considered when checking for overlap. _(Source: user decision, confirmed 2026-09-25)_
- **BR-07:** When `starts_at` and `ends_at` are both present, `ends_at` MUST be strictly after `starts_at`. _(Source: `docs/database-schema.md` — CHECK constraint)_
- **BR-08:** Optimistic locking is enforced via the `version` field on every update. A stale `version` must be rejected to prevent lost updates under concurrent edits. _(Source: `docs/adrs.md` — ADR-013)_
- **BR-09:** Deleting a `DRAFT` workshop permanently removes it along with all its associated speaker rows (cascaded at the database level). No soft-delete or recovery mechanism exists for this operation. _(Source: user decision, confirmed 2026-09-25; `docs/database-schema.md` — `ON DELETE CASCADE`)_
- **BR-10:** No outbox event or RabbitMQ message is published for any draft operation (save, preview, delete). Event publishing is deferred to Feature 2.2 (publishing). _(Source: `docs/feature-catalog.md` — Feature 2.1 Out of Scope)_
- **BR-11:** No Elasticsearch index update occurs for any draft operation. Search indexing is deferred to Feature 2.2. _(Source: `docs/feature-catalog.md` — Feature 2.1 Out of Scope)_
- **BR-12:** The `timezone` field defaults to `Asia/Ho_Chi_Minh` when not supplied. _(Source: `docs/database-schema.md` — column default)_
- **BR-13:** The `price` defaults to `0` and `currency` defaults to `USD` when not supplied. _(Source: `docs/database-schema.md`)_
- **BR-14:** `capacity`, when supplied, must be a positive integer in range `[1, 1000000]`. _(Source: `docs/database-schema.md` — CHECK constraint)_
- **BR-15:** Media upload is stateless with respect to the database; it does not persist workshop records or modify database entities. URLs returned from media upload must be included in the Save Draft payload to be persisted. _(Source: ADR-032)_
- **BR-16:** Media files uploaded to MinIO bucket `unihub-media` must follow strict key partitioning (`workshops/covers/{uuid}.{ext}` for cover images and `speakers/avatars/{uuid}.{ext}` for speaker portraits). Stale or deleted assets must be cleaned up from MinIO. _(Source: ADR-032)_
- **BR-17:** Organizer workshop listing (`GET /api/v1/admin/workshops`) routes to the PostgreSQL Read Replica via `@Transactional(readOnly = true)`. This is a browsing query (not a check-then-act or idempotency-sensitive operation) so eventual consistency is acceptable per `docs/database-schema.md`. Tenant isolation is still strictly enforced — all results are scoped to the caller's organization (`jwt.org`).

## 5. State Transitions

| From State            | Event / Trigger                              | To State    |      Valid?       | Rejection Behavior (if invalid)                  |
| :-------------------- | :------------------------------------------- | :---------- | :---------------: | :----------------------------------------------- |
| _(none)_              | Save as Draft (no ID in body)                | `DRAFT`     |        ✅         | —                                                |
| `DRAFT`               | Save as Draft (ID in body, version matches)  | `DRAFT`     |        ✅         | —                                                |
| `DRAFT`               | Save as Draft (ID in body, version mismatch) | —           |        ❌         | `409 Conflict` — optimistic locking violation    |
| `DRAFT`               | Delete Draft                                 | _(deleted)_ |        ✅         | —                                                |
| `PUBLISHED`           | Save as Draft                                | —           |        ❌         | `409 Conflict` — workshop is not in DRAFT status |
| `PUBLISHED`           | Delete Draft                                 | —           |        ❌         | `409 Conflict` — workshop is not in DRAFT status |
| `COMPLETED`           | Save as Draft                                | —           |        ❌         | `409 Conflict` — workshop is not in DRAFT status |
| `COMPLETED`           | Delete Draft                                 | —           |        ❌         | `409 Conflict` — workshop is not in DRAFT status |
| `CANCELED`            | Save as Draft                                | —           |        ❌         | `409 Conflict` — workshop is not in DRAFT status |
| `CANCELED`            | Delete Draft                                 | —           |        ❌         | `409 Conflict` — workshop is not in DRAFT status |
| `DRAFT` → `PUBLISHED` | _(publish)_                                  | `PUBLISHED` | N/A (Feature 2.2) | —                                                |

> **Note:** The `DRAFT → PUBLISHED` transition is owned by Feature 2.2 and is listed here only to establish that this feature does **not** produce that transition.

## 6. Non-Functional Requirements

- **Performance:** The speaker overlap check must use the indexed `workshop_speakers.email` column and must not execute a per-speaker query in a loop (N+1 is prohibited); the lookup must be a single batched query.
- **Concurrency:** The `version` field provides optimistic locking for concurrent saves to the same draft. No pessimistic locking (e.g., `SELECT FOR UPDATE`) is required for this feature.
- **Idempotency:** Save as Draft is not strictly idempotent (each invocation may update timestamps or replace speakers). No idempotency key is required because the operation carries no financial consequence and the client receives an immediate response to retry safely.
- **Rate Limiting:** Applied by Redis/Bucket4j at the gateway layer; no per-endpoint rate limiting logic is owned by this feature itself. _(Source: `docs/system-design.md` — system protection mechanisms)_
- **Security:** The `organization_id` is always sourced from the JWT claim; the caller must never be able to set it directly. All inputs from the request body are validated before any persistence operation.
- **Reliability / Failure Modes:** A slug collision (detected at DB insert time) must be retried with a new random suffix internally, transparent to the caller, up to a reasonable retry count before returning `500`.
- **Observability:** Standard structured logging per `docs/api-standards.md`. No additional custom metrics are required for this feature beyond the baseline request/response logging.

## 7. Acceptance Criteria

### Scenario 1: ORGANIZER creates a new draft with title only

```gherkin
Given an authenticated ORGANIZER with a valid JWT (role = ORGANIZER, org = org-A)
When they send a Save as Draft request with title = "Introduction to AI" and no workshop ID
Then the system creates a new workshop with status = DRAFT and organization_id = org-A
And returns HTTP 201 with the new workshop's id, slug, version = 0, and status = "DRAFT"
And the slug is a URL-safe kebab-case string derived from "Introduction to AI"
```

_Satisfies: FR-04, FR-11, FR-20, FR-03_

### Scenario 2: ORGANIZER creates a draft with a Vietnamese title

```gherkin
Given an authenticated ORGANIZER with a valid JWT
When they send a Save as Draft request with title = "Hội thảo Trí tuệ Nhân tạo"
Then the system creates a new workshop and the slug is ASCII kebab-case (e.g., "hoi-thao-tri-tue-nhan-tao-<suffix>")
And the slug contains no diacritical marks or non-ASCII characters
```

_Satisfies: FR-20, FR-22_

### Scenario 3: ORGANIZER updates a draft and title changes → slug is regenerated

```gherkin
Given a workshop with id = W1, status = DRAFT, title = "Old Title", slug = "old-title-xyz", version = 0
And the workshop belongs to org-A
And an authenticated ORGANIZER from org-A
When they send a Save as Draft request with id = W1, title = "New Title", version = 0
Then the system updates the workshop
And the slug is regenerated from "New Title" (e.g., "new-title-<suffix>")
And version is incremented to 1
And returns HTTP 200
```

_Satisfies: FR-05, FR-21, FR-22_

### Scenario 4: Optimistic locking rejects stale update

```gherkin
Given a workshop with id = W1, status = DRAFT, version = 2
And an authenticated ORGANIZER from the same organization
When they send a Save as Draft request with id = W1 and version = 1
Then the system returns HTTP 409 Conflict
And the workshop record is unchanged
```

_Satisfies: FR-08_

### Scenario 5: Update without version field is rejected

```gherkin
Given a workshop with id = W1, status = DRAFT
And an authenticated ORGANIZER from the same organization
When they send a Save as Draft request with id = W1 and no version field
Then the system returns HTTP 422 Unprocessable Entity
```

_Satisfies: FR-09_

### Scenario 6: Save as Draft on a non-DRAFT workshop is rejected

```gherkin
Given a workshop with id = W1, status = PUBLISHED
And an authenticated ORGANIZER from the same organization
When they send a Save as Draft request with id = W1, version = 1
Then the system returns HTTP 409 Conflict
And the workshop record is unchanged
```

_Satisfies: FR-10_

### Scenario 7: ORGANIZER cannot access another organization's workshop

```gherkin
Given a workshop with id = W1, status = DRAFT, organization_id = org-B
And an authenticated ORGANIZER from org-A (a different organization)
When they send a Save as Draft request with id = W1
Then the system returns HTTP 403 Forbidden
```

_Satisfies: FR-06_

### Scenario 8: Title is required

```gherkin
Given an authenticated ORGANIZER with a valid JWT
When they send a Save as Draft request with no title field
Then the system returns HTTP 422 Unprocessable Entity
And no workshop record is created
```

_Satisfies: FR-11_

### Scenario 9: Speaker added without conflict

```gherkin
Given an authenticated ORGANIZER with a valid JWT
And speaker "alice@example.com" is not booked at any PUBLISHED or COMPLETED workshop overlapping 10:00–12:00 on 2026-10-01
When they send a Save as Draft request with title, starts_at = "2026-10-01T10:00:00Z", ends_at = "2026-10-01T12:00:00Z", and speakers = [{name: "Alice", email: "Alice@Example.com"}]
Then the system creates the draft with one speaker, email stored as "alice@example.com"
And returns HTTP 201
```

_Satisfies: FR-23, FR-27, FR-28_

### Scenario 10: Speaker schedule conflict is rejected

```gherkin
Given speaker "bob@example.com" is booked at workshop W2 (status = PUBLISHED, starts_at = "2026-10-01T09:00:00Z", ends_at = "2026-10-01T11:00:00Z")
And an authenticated ORGANIZER from any organization
When they send a Save as Draft request with starts_at = "2026-10-01T10:00:00Z", ends_at = "2026-10-01T12:00:00Z" and speakers = [{name: "Bob", email: "bob@example.com"}]
Then the system returns HTTP 422 Unprocessable Entity
And no workshop record is created
And the response identifies which speaker email caused the conflict
```

_Satisfies: FR-27_

### Scenario 11: Speakers provided but schedule missing

```gherkin
Given an authenticated ORGANIZER with a valid JWT
When they send a Save as Draft request with speakers = [{name: "Carol", email: "carol@example.com"}] and no starts_at or ends_at
Then the system returns HTTP 422 Unprocessable Entity with a message indicating schedule is required when speakers are provided
```

_Satisfies: FR-26_

### Scenario 12: Intra-request duplicate speaker emails are rejected

```gherkin
Given an authenticated ORGANIZER with a valid JWT
When they send a Save as Draft request with speakers containing two entries both with email = "dave@example.com"
Then the system returns HTTP 422 Unprocessable Entity
And no workshop record is created
```

_Satisfies: FR-25_

### Scenario 13: ORGANIZER uploads a valid workshop cover image

```gherkin
Given an authenticated ORGANIZER with a valid JWT
When they upload a 2MB JPEG/JPG image file to POST /api/v1/admin/workshops/media/upload with type = "COVER_IMAGE"
Then the system returns HTTP 201 Created
And the response includes the public URL matching MinIO storage path (e.g., ".../workshops/covers/<uuid>.jpg")
And no database record is created or modified
```

_Satisfies: FR-31, FR-32, FR-33, FR-34, BR-15, BR-16_

### Scenario 14: Media upload rejected for invalid file size or type

```gherkin
Given an authenticated ORGANIZER with a valid JWT
When they upload a 6MB file or an unsupported format (e.g. text/plain) to POST /api/v1/admin/workshops/media/upload
Then the system returns HTTP 422 Unprocessable Entity
And no object is stored in MinIO
```

_Satisfies: FR-32, FR-35_

### Scenario 15: Workshop details returned for organizer

```gherkin
Given a workshop with id = W1, status = DRAFT, organization_id = org-A, with two speakers
And an authenticated ORGANIZER from org-A
When they send a GET /api/v1/admin/workshops/W1 request
Then the system returns HTTP 200 with the full workshop object, including the speakers array
```

_Satisfies: FR-39, FR-42_

### Scenario 16: Workshop details denied for another organization's workshop

```gherkin
Given a workshop with id = W1, status = DRAFT, organization_id = org-B
And an authenticated ORGANIZER from org-A
When they send a GET /api/v1/admin/workshops/W1 request
Then the system returns HTTP 403 Forbidden
```

_Satisfies: FR-40_

### Scenario 17: Save as Draft update cleans up replaced cover image and removed speaker avatars

```gherkin
Given a DRAFT workshop with coverImageUrl = ".../workshops/covers/old.jpg" and speaker avatarUrl = ".../speakers/avatars/speaker-old.jpg"
And an authenticated ORGANIZER from the owning organization
When they send a Save as Draft update replacing coverImageUrl with ".../workshops/covers/new.jpg" and removing the speaker
Then the system updates the draft in the database
And the old cover image and removed speaker avatar objects are deleted from MinIO
```

_Satisfies: FR-36, FR-37, BR-16_

### Scenario 18: ORGANIZER deletes a DRAFT workshop and associated media is cleaned up

```gherkin
Given a workshop with id = W1, status = DRAFT, organization_id = org-A, with cover image and three speakers
And an authenticated ORGANIZER from org-A
When they send a Delete Draft request for id = W1
Then the system returns HTTP 204 No Content
And the workshop record and associated speaker records are permanently deleted from the database
And the cover image and all speaker avatar files are deleted from MinIO
```

_Satisfies: FR-43, BR-09, BR-16_

### Scenario 19: Delete Draft denied for published workshop

```gherkin
Given a workshop with id = W1, status = PUBLISHED, organization_id = org-A
And an authenticated ORGANIZER from org-A
When they send a Delete Draft request for id = W1
Then the system returns HTTP 409 Conflict
And the workshop record is unchanged
```

_Satisfies: FR-46_

### Scenario 20: Unauthenticated request is rejected

```gherkin
Given no Authorization header
When any request in this feature is sent
Then the system returns HTTP 401 Unauthorized
```

_Satisfies: FR-01_

### Scenario 21: Non-ORGANIZER role is rejected

```gherkin
Given a valid JWT with role = STUDENT
When any mutating request in this feature is sent
Then the system returns HTTP 403 Forbidden
```

_Satisfies: FR-02_

### Scenario 22: ORGANIZER retrieves paginated list of workshops for their organization

```gherkin
Given an authenticated ORGANIZER with a valid JWT (role = ORGANIZER, org = org-A)
And org-A owns 3 workshops in statuses DRAFT, PUBLISHED, and COMPLETED
And org-B owns 2 workshops
When they send a request to GET /api/v1/admin/workshops
Then the system returns HTTP 200 OK
And the response envelope contains only the 3 workshops belonging to org-A
And no workshops belonging to org-B are returned
And "data.cursor.hasMore" is false and "data.cursor.nextCursor" is null
```

_Satisfies: FR-48, FR-49, FR-53, FR-55, BR-01, BR-17_

### Scenario 23: ORGANIZER filters workshops by status

```gherkin
Given an authenticated ORGANIZER with a valid JWT (role = ORGANIZER, org = org-A)
And org-A owns 2 workshops with status = DRAFT and 1 workshop with status = PUBLISHED
When they send a request to GET /api/v1/admin/workshops?status=DRAFT
Then the system returns HTTP 200 OK
And the response contains exactly the 2 DRAFT workshops
And no workshops with status != DRAFT are included
```

_Satisfies: FR-50_

### Scenario 24: ORGANIZER searches workshops using query parameter q (matching title, slug, location, description)

```gherkin
Given an authenticated ORGANIZER with a valid JWT (role = ORGANIZER, org = org-A)
And org-A owns workshops:
  | title                   | slug                   | location    | description             |
  | Advanced Spring Boot    | advanced-spring-boot   | Hall A      | Java backend framework  |
  | React Native Deep Dive  | react-native-deep-dive | Lab 3       | Mobile app dev          |
  | Cloud Native Kubernetes | cloud-native-k8s       | Auditorium  | Container orchestration |
When they send a request to GET /api/v1/admin/workshops?q=spring
Then the system returns HTTP 200 OK
And the response contains only "Advanced Spring Boot"
When they send a request to GET /api/v1/admin/workshops?q=auditorium
Then the system returns HTTP 200 OK
And the response contains only "Cloud Native Kubernetes"
When they send a request to GET /api/v1/admin/workshops?q=backend
Then the system returns HTTP 200 OK
And the response contains only "Advanced Spring Boot"
```

_Satisfies: FR-51_

### Scenario 25: ORGANIZER filters workshops by start date range

```gherkin
Given an authenticated ORGANIZER with a valid JWT (role = ORGANIZER, org = org-A)
And org-A owns a workshop starting on "2026-10-15T09:00:00Z" and another starting on "2026-11-20T09:00:00Z"
When they send a request to GET /api/v1/admin/workshops?from=2026-10-01T00:00:00Z&to=2026-10-31T23:59:59Z
Then the system returns HTTP 200 OK
And the response contains only the workshop starting on "2026-10-15T09:00:00Z"
```

_Satisfies: FR-52_

### Scenario 26: Listing workshops is rejected for non-ORGANIZER roles

```gherkin
Given an authenticated user with a valid JWT with role = STUDENT or role = STAFF
When they send a request to GET /api/v1/admin/workshops
Then the system returns HTTP 403 Forbidden
```

_Satisfies: FR-54_

### Scenario 27: ORGANIZER filters workshops by priceType (FREE vs PAID)

```gherkin
Given an authenticated ORGANIZER with a valid JWT (role = ORGANIZER, org = org-A)
And org-A owns a free workshop (price = 0) and a paid workshop (price = 50.00)
When they send a request to GET /api/v1/admin/workshops?priceType=FREE
Then the system returns HTTP 200 OK
And the response contains only the free workshop
When they send a request to GET /api/v1/admin/workshops?priceType=PAID
Then the system returns HTTP 200 OK
And the response contains only the paid workshop
```

_Satisfies: FR-56_

### Scenario 28: ORGANIZER sorts workshops by capacity ascending / descending

```gherkin
Given an authenticated ORGANIZER with a valid JWT (role = ORGANIZER, org = org-A)
And org-A owns Workshop A with capacity = 50 and Workshop B with capacity = 200
When they send a request to GET /api/v1/admin/workshops?sort=capacity,asc
Then the system returns HTTP 200 OK
And Workshop A appears before Workshop B in the items list
When they send a request to GET /api/v1/admin/workshops?sort=capacity,desc
Then the system returns HTTP 200 OK
And Workshop B appears before Workshop A in the items list
```

_Satisfies: FR-57_

### Scenario 29: ORGANIZER sorts workshops by price ascending / descending

```gherkin
Given an authenticated ORGANIZER with a valid JWT (role = ORGANIZER, org = org-A)
And org-A owns Workshop A with price = 10.00 and Workshop B with price = 50.00
When they send a request to GET /api/v1/admin/workshops?sort=price,asc
Then the system returns HTTP 200 OK
And Workshop A appears before Workshop B in the items list
When they send a request to GET /api/v1/admin/workshops?sort=price,desc
Then the system returns HTTP 200 OK
And Workshop B appears before Workshop A in the items list
```

_Satisfies: FR-57_

## 8. Out of Scope

- **Publishing a workshop** (transitioning from `DRAFT` to `PUBLISHED`) — this is Feature 2.2.
- **Updating a workshop that is already PUBLISHED, COMPLETED, or CANCELED** — this is Feature 2.2.
- **Canceling a workshop** — this is Feature 2.5.
- **Student-facing workshop discovery or registration** — these are Features 3.x.
- **Public workshop catalog browsing and student detail view** — public browsing of published workshops is Feature 3.1 (`workshop-catalog-browsing`).
- **In-progress live preview rendering** — rendering changes before saving is handled client-side in the web frontend using local form state.
- **Search indexing (Elasticsearch)** — Elasticsearch CQRS write-side updates are triggered on publish (Feature 2.2), not on draft save.
- **Event publishing (RabbitMQ outbox)** — no domain events are published for draft operations.
- **Capacity enforcement or overbooking prevention** — relevant only when registration is open (Feature 3.x).
- **Payment setup or pricing validation** — `price` and `currency` are stored as-is in draft; Stripe integration is out of scope for this feature.
- **QR code generation** — part of Feature 4.x.
- **AI summarization** — Feature 5.x.
- **STAFF assignment** — Feature 2.4.

## 9. Linked References

| Document                    | Section Consulted                                                                                                                                                   |
| :-------------------------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `docs/feature-catalog.md`   | Feature 2.1 — Workshop Creation and Draft Management; Appendix A — Feature Dependency Map                                                                           |
| `docs/system-design.md`     | Workshop Lifecycle Flow; MEDIA UPLOAD OPERATION; SAVE DRAFT OPERATION; GET WORKSHOP DETAILS FOR ORGANIZER OPERATION; DELETE DRAFT OPERATION; RBAC sequence diagrams |
| `docs/database-schema.md`   | `workshops` table (all columns, constraints, indexes); `workshop_speakers` table (columns, FK, `ON DELETE CASCADE`, `idx_workshop_speakers_email`)                  |
| `docs/adrs.md`              | ADR-006 (RBAC), ADR-008 (Flyway migrations), ADR-013 (optimistic locking via `@Version`), ADR-032 (Decoupled Media Upload via MinIO)                                |
| `docs/api-standards.md`     | HTTP status code table; RFC 7807 error format; JSON envelope format; Auth header conventions                                                                        |
| `docs/project-structure.md` | `workshops` module Clean Architecture layout; inter-module communication rules                                                                                      |
| `AGENTS.md`                 | Feature status lifecycle; SDD + TDD methodology; RBAC enforcement                                                                                                   |
