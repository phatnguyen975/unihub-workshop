# Spec: Workshop Creation and Draft Management

> **Status:** APPROVED  
> **Domain:** 2 — Workshop Lifecycle Management  
> **Owner/area:** `workshops` module — backend (`apps/backend/`)  
> **Last revised:** 2026-09-25 — first version

## 1. Overview

This feature allows an authenticated **ORGANIZER** to compose a workshop incrementally and save it as a `DRAFT` before it is ready to publish. Drafts are private to the owning organization: they are not visible to students, not indexed for search, and not eligible for registration. A single "Save as Draft" action covers both first-time creation and subsequent updates to the same draft, disambiguated by whether the caller supplies an existing workshop identifier. An ORGANIZER can also preview their draft, and permanently delete it if it is no longer needed. This feature replaces the previous manual process and is a prerequisite for Feature 2.2 (workshop publishing and lifecycle management).

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

### 3.3 Required Fields

- **FR-11:** The system SHALL reject a Save as Draft request with `422 Unprocessable Entity` when `title` is absent or blank.

### 3.4 Slug Management

- **FR-12:** The system SHALL automatically generate a URL-safe, unique `slug` from the `title` upon the first Save as Draft (create), supporting both Vietnamese and English characters via transliteration to ASCII kebab-case.
- **FR-13:** The system SHALL automatically regenerate and update the `slug` whenever the `title` changes during a Save as Draft update, while the workshop remains in `DRAFT` status.
- **FR-14:** The system SHALL guarantee `slug` uniqueness across all workshops by appending a short random suffix to the generated slug when a collision is detected.

### 3.5 Speaker Management

- **FR-15:** The system SHALL accept `speakers` as an optional array in the Save as Draft request; omitting the field is valid and results in no speakers being stored.
- **FR-16:** The system SHALL replace the entire speaker list for the workshop on every Save as Draft update that includes a `speakers` field (full replace, not merge).
- **FR-17:** The system SHALL reject a Save as Draft request with `422 Unprocessable Entity` when the `speakers` array contains two or more entries with the same email address (intra-request duplicate).
- **FR-18:** The system SHALL reject a Save as Draft request with `422 Unprocessable Entity` when the `speakers` array is non-empty and `starts_at` or `ends_at` is absent (schedule is required to perform overlap detection).
- **FR-19:** The system SHALL reject a Save as Draft request with `422 Unprocessable Entity` when one or more speaker emails conflict with a confirmed booking at another workshop (status is `PUBLISHED` or `COMPLETED`) whose time interval overlaps the proposed `starts_at`–`ends_at` window, excluding the current workshop being updated.
- **FR-20:** The system SHALL normalize every speaker email to lowercase and trim surrounding whitespace before storing it and before performing overlap checks.
- **FR-21:** The system SHALL reject a Save as Draft request with `422 Unprocessable Entity` when any speaker entry is missing the required `name` field.
- **FR-22:** The system SHALL reject a Save as Draft request with `422 Unprocessable Entity` when any speaker entry contains an `email` that is not a syntactically valid email address.

### 3.6 Preview Draft

- **FR-23:** The system SHALL return the full workshop details (including the speaker list) for a given workshop identifier when a Preview Draft request is received and the requester is an ORGANIZER belonging to the same organization as the workshop.
- **FR-24:** The system SHALL reject a Preview Draft request with `403 Forbidden` when the workshop belongs to a different organization than the caller's JWT `org` claim.
- **FR-25:** The system SHALL reject a Preview Draft request with `404 Not Found` when the workshop identifier does not exist.
- **FR-26:** The system SHALL allow Preview Draft for a workshop in any status (DRAFT, PUBLISHED, COMPLETED, CANCELED), not only DRAFT.

### 3.7 Delete Draft

- **FR-27:** The system SHALL permanently delete a workshop and all associated speakers when a Delete Draft request is received and the workshop is in `DRAFT` status and belongs to the caller's organization.
- **FR-28:** The system SHALL reject a Delete Draft request with `403 Forbidden` when the workshop belongs to a different organization than the caller's JWT `org` claim.
- **FR-29:** The system SHALL reject a Delete Draft request with `404 Not Found` when the workshop identifier does not exist.
- **FR-30:** The system SHALL reject a Delete Draft request with `409 Conflict` when the workshop is not in `DRAFT` status (deletion of published, completed, or canceled workshops is not permitted by this feature).

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
- **BR-14:** `capacity`, when supplied, must be a positive integer (≥ 1). _(Source: `docs/database-schema.md` — CHECK constraint)_

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

_Satisfies: FR-04, FR-11, FR-12, FR-03_

### Scenario 2: ORGANIZER creates a draft with a Vietnamese title

```gherkin
Given an authenticated ORGANIZER with a valid JWT
When they send a Save as Draft request with title = "Hội thảo Trí tuệ Nhân tạo"
Then the system creates a new workshop and the slug is ASCII kebab-case (e.g., "hoi-thao-tri-tue-nhan-tao-<suffix>")
And the slug contains no diacritical marks or non-ASCII characters
```

_Satisfies: FR-12, FR-14_

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

_Satisfies: FR-05, FR-13, FR-14_

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

_Satisfies: FR-15, FR-19, FR-20_

### Scenario 10: Speaker schedule conflict is rejected

```gherkin
Given speaker "bob@example.com" is booked at workshop W2 (status = PUBLISHED, starts_at = "2026-10-01T09:00:00Z", ends_at = "2026-10-01T11:00:00Z")
And an authenticated ORGANIZER from any organization
When they send a Save as Draft request with starts_at = "2026-10-01T10:00:00Z", ends_at = "2026-10-01T12:00:00Z" and speakers = [{name: "Bob", email: "bob@example.com"}]
Then the system returns HTTP 422 Unprocessable Entity
And no workshop record is created
And the response identifies which speaker email caused the conflict
```

_Satisfies: FR-19_

### Scenario 11: Speakers provided but schedule missing

```gherkin
Given an authenticated ORGANIZER with a valid JWT
When they send a Save as Draft request with speakers = [{name: "Carol", email: "carol@example.com"}] and no starts_at or ends_at
Then the system returns HTTP 422 Unprocessable Entity with a message indicating schedule is required when speakers are provided
```

_Satisfies: FR-18_

### Scenario 12: Intra-request duplicate speaker emails are rejected

```gherkin
Given an authenticated ORGANIZER with a valid JWT
When they send a Save as Draft request with speakers containing two entries both with email = "dave@example.com"
Then the system returns HTTP 422 Unprocessable Entity
And no workshop record is created
```

_Satisfies: FR-17_

### Scenario 13: Preview Draft returns full workshop details

```gherkin
Given a workshop with id = W1, status = DRAFT, organization_id = org-A, with two speakers
And an authenticated ORGANIZER from org-A
When they send a Preview Draft request for id = W1
Then the system returns HTTP 200 with the full workshop object, including the speakers array
```

_Satisfies: FR-23_

### Scenario 14: Preview Draft denied for another organization's workshop

```gherkin
Given a workshop with id = W1, status = DRAFT, organization_id = org-B
And an authenticated ORGANIZER from org-A
When they send a Preview Draft request for id = W1
Then the system returns HTTP 403 Forbidden
```

_Satisfies: FR-24_

### Scenario 15: ORGANIZER deletes a DRAFT workshop

```gherkin
Given a workshop with id = W1, status = DRAFT, organization_id = org-A, with three speakers
And an authenticated ORGANIZER from org-A
When they send a Delete Draft request for id = W1
Then the system returns HTTP 204 No Content
And the workshop record is permanently removed from the database
And all associated speaker records are also removed
```

_Satisfies: FR-27, BR-09_

### Scenario 16: Delete Draft denied for published workshop

```gherkin
Given a workshop with id = W1, status = PUBLISHED, organization_id = org-A
And an authenticated ORGANIZER from org-A
When they send a Delete Draft request for id = W1
Then the system returns HTTP 409 Conflict
And the workshop record is unchanged
```

_Satisfies: FR-30_

### Scenario 17: Unauthenticated request is rejected

```gherkin
Given no Authorization header
When any request in this feature is sent
Then the system returns HTTP 401 Unauthorized
```

_Satisfies: FR-01_

### Scenario 18: Non-ORGANIZER role is rejected

```gherkin
Given a valid JWT with role = STUDENT
When any mutating request in this feature is sent
Then the system returns HTTP 403 Forbidden
```

_Satisfies: FR-02_

## 8. Out of Scope

- **Publishing a workshop** (transitioning from `DRAFT` to `PUBLISHED`) — this is Feature 2.2.
- **Updating a workshop that is already PUBLISHED, COMPLETED, or CANCELED** — this is Feature 2.2.
- **Canceling a workshop** — this is Feature 2.5.
- **Student-facing workshop discovery or registration** — these are Features 3.x.
- **Search indexing (Elasticsearch)** — Elasticsearch CQRS write-side updates are triggered on publish (Feature 2.2), not on draft save.
- **Event publishing (RabbitMQ outbox)** — no domain events are published for draft operations.
- **File upload for cover image** — `cover_image_url` is a string URL; the upload/storage workflow (MinIO) is a separate concern outside this feature.
- **Capacity enforcement or overbooking prevention** — relevant only when registration is open (Feature 3.x).
- **Payment setup or pricing validation** — `price` and `currency` are stored as-is in draft; Stripe integration is out of scope for this feature.
- **QR code generation** — part of Feature 4.x.
- **AI summarization** — Feature 5.x.
- **STAFF assignment** — Feature 2.4.

## 9. Linked References

| Document                    | Section Consulted                                                                                                                                    |
| :-------------------------- | :--------------------------------------------------------------------------------------------------------------------------------------------------- |
| `docs/feature-catalog.md`   | Feature 2.1 — Workshop Creation and Draft Management; Appendix A — Feature Dependency Map                                                            |
| `docs/system-design.md`     | Workshop Lifecycle Flow; SAVE DRAFT OPERATION; PREVIEW DRAFT OPERATION; DELETE DRAFT OPERATION; RBAC sequence diagrams; System Protection Mechanisms |
| `docs/database-schema.md`   | `workshops` table (all columns, constraints, indexes); `workshop_speakers` table (columns, FK, `ON DELETE CASCADE`, `idx_workshop_speakers_email`)   |
| `docs/adrs.md`              | ADR-006 (RBAC), ADR-008 (Flyway migrations), ADR-013 (optimistic locking via `@Version`)                                                             |
| `docs/api-standards.md`     | HTTP status code table; RFC 7807 error format; JSON envelope format; Auth header conventions                                                         |
| `docs/project-structure.md` | `workshops` module Clean Architecture layout; inter-module communication rules                                                                       |
| `AGENTS.md`                 | Feature status lifecycle; SDD + TDD methodology; RBAC enforcement                                                                                    |
