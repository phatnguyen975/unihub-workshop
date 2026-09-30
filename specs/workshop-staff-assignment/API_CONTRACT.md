# API Contract: Workshop Staff Assignment

> **Status:** APPROVED  
> **Spec:** `specs/workshop-staff-assignment/SPEC.md`  
> **Last revised:** 2026-09-29

## 1. Overview

This contract defines the four REST endpoints exposed by the `workshops` module for Feature 1.6 — Workshop Staff Assignment:

1. `GET /api/v1/admin/workshops/{id}/staff` — Retrieving a paginated list of staff members assigned to a workshop
2. `GET /api/v1/admin/workshops/{id}/staff/available` — Retrieving a paginated list of available staff members eligible for assignment
3. `POST /api/v1/admin/workshops/{id}/staff` — Atomically assigning a batch of staff members to a workshop
4. `DELETE /api/v1/admin/workshops/{id}/staff/{userId}` — Revoking a staff member's assignment from a workshop

All endpoints are strictly ORGANIZER-only, scoped to the caller's organization (`organization_id`). Staff availability queries, batch assignments, and revocations strictly require the workshop to be in `PUBLISHED` status and prior to its scheduled start time (`now() < starts_at`). Querying the assigned staff roster is available across all workshop lifecycle states for auditability. This contract traces directly to `SPEC.md` for this feature and conforms in full to `docs/api-standards.md`.

## 2. Global Standards Applied

- **URI structure / versioning:** All endpoints are prefixed with `/api/v1/admin/workshops/{id}/staff`. Resources follow plural noun RESTful conventions nested under the admin workshop aggregate. _(Source: `docs/api-standards.md`)_
- **Pagination convention:** Collection endpoints (`GET .../staff` and `GET .../staff/available`) use universal keyset cursor pagination (`cursor`, `size` with default 20, max 100) returning `{ "items": [...], "cursor": { "nextCursor": "...", "hasMore": boolean } }`. Single-mutation and deletion endpoints do not paginate. _(Source: `docs/api-standards.md`)_
- **Success response shape:** Unified `ApiResponse<T>` envelope — `{ "success": true, "message": "...", "data": { ... }, "metadata": { "processedBy": "...", "traceId": "..." } }`. `204 No Content` responses carry no body. Null fields dropped via non-null serialization (`@JsonInclude(Include.NON_NULL)`). _(Source: `docs/api-standards.md`)_
- **Error response format:** RFC 7807 Problem Details wrapped inside the standard response envelope with `success = false`, `error` object (`type`, `title`, `status`, `detail`, `instance`, and optional `validationErrors`), and `metadata` (`processedBy`, `traceId`) with `Content-Type: application/problem+json`. _(Source: `docs/api-standards.md`)_
- **Idempotency-key convention:** Not required for endpoints in this contract. Mutation safety is guaranteed by database unique constraints and state pre-checks. _(Source: Section 5 of this document)_
- **Auth header convention:** `Authorization: Bearer <JWT>` — RS256 signed, required on every endpoint. Role claim must be `ORGANIZER` and `organization_id` must match the target workshop's organization.
- **Date/time formatting convention:** ISO 8601 with UTC offset — `YYYY-MM-DDTHH:mm:ssZ`.

## 3. Endpoints

### GET /api/v1/admin/workshops/{id}/staff

**Description:** Returns a paginated list of all staff members currently assigned to the specified workshop. Allowed across all workshop lifecycle states (`DRAFT`, `PUBLISHED`, `COMPLETED`, `CANCELED`) and all times for auditability.  
**Required Role / Auth:** `ORGANIZER` — JWT with `role = ORGANIZER` and `org` claim matching workshop's `organization_id`.  
**Idempotency Requirement:** Not Required — read-only operation.

#### Request

**Headers:**

| Header          | Required | Description                            |
| :-------------- | :------- | :------------------------------------- |
| `Authorization` | Yes      | `Bearer <JWT>` (RS256, ORGANIZER role) |
| `Accept`        | No       | `application/json`                     |

**Path Parameters:**

| Parameter | Type | Description                           |
| :-------- | :--- | :------------------------------------ |
| `id`      | UUID | The unique identifier of the workshop |

**Query Parameters:**

| Parameter | Type    | Required | Default | Description                                                                                                |
| :-------- | :------ | :------- | :------ | :--------------------------------------------------------------------------------------------------------- |
| `cursor`  | String  | No       | —       | Keyset pagination cursor (opaque Base64-encoded string representing the last item from the previous page). |
| `size`    | Integer | No       | `20`    | Number of staff members per page. Min: 1, Max: 100.                                                        |

> **Sorting:** Always sorted by `assignedAt DESC, id DESC` (most recently assigned first). No client `sort` parameter is required.

**Request Body:** None.

#### Response

**Success — 200 OK:**

```json
{
  "success": true,
  "message": "Assigned staff retrieved successfully.",
  "data": {
    "items": [
      {
        "userId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
        "fullName": "Alice Nguyen",
        "email": "alice@example.com",
        "avatarUrl": "https://minio.unihub.io/unihub-media/users/avatars/alice.jpg",
        "phoneNumber": "+84901234567",
        "assignedAt": "2026-09-28T08:30:00Z"
      }
    ],
    "cursor": {
      "nextCursor": "eyJhc3NpZ25lZEF0IjoiMjAyNi0wOS0yOFQwODozMDowMFoiLCJpZCI6IjNmYTg1ZjY0LTU3MTctNDU2Mi1iM2ZjLTJjOTYzZjY2YWZhNiJ9",
      "hasMore": false
    }
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

> If the workshop is in `DRAFT` status and has no staff assigned, `items` returns an empty array `[]` with `cursor.hasMore = false`.

**Item Field Definitions:**

| Field         | Type            | Description                                                                     |
| :------------ | :-------------- | :------------------------------------------------------------------------------ |
| `userId`      | UUID string     | Unique identifier of the staff member (from `users.id`)                         |
| `fullName`    | String          | Full name of the staff member                                                   |
| `email`       | String          | Work email address of the staff member                                          |
| `avatarUrl`   | String \| null  | URL to the staff member's avatar image (null if no custom avatar uploaded)      |
| `phoneNumber` | String \| null  | Contact phone number of the staff member (null if not provided in user profile) |
| `assignedAt`  | ISO 8601 string | UTC timestamp when this staff member was assigned to workshop                   |

**Error Responses:**

| Status                      | Error Type (`error.type`)       | Title                 | Condition                                                                                   |
| :-------------------------- | :------------------------------ | :-------------------- | :------------------------------------------------------------------------------------------ |
| `400 Bad Request`           | `urn:unihub:error:bad-request`  | Bad Request           | Invalid UUID syntax for `{id}` or invalid `size` parameter (`size < 1` or `size > 100`).    |
| `401 Unauthorized`          | `urn:unihub:error:unauthorized` | Unauthorized          | Missing, expired, invalid-signature, or Redis-blacklisted Bearer JWT.                       |
| `403 Forbidden`             | `urn:unihub:error:forbidden`    | Forbidden             | Caller `role` is not `ORGANIZER`, or workshop does not belong to the caller's organization. |
| `404 Not Found`             | `urn:unihub:error:not-found`    | Not Found             | No workshop exists with the specified `id`.                                                 |
| `500 Internal Server Error` | `urn:unihub:error:internal`     | Internal Server Error | Unexpected database query failure.                                                          |

**Error Response Example (404 Not Found):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:not-found",
    "title": "Not Found",
    "status": 404,
    "detail": "Workshop not found with id: a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "instance": "/api/v1/admin/workshops/a1b2c3d4-e5f6-7890-abcd-ef1234567890/staff"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

#### Side Effects

None. This read-only query routes to PostgreSQL Read Replica via `@Transactional(readOnly = true)` to offload query load from the Primary DB.

#### Spec Traceability

- **Satisfies:** FR-01, FR-02, FR-03, FR-04, FR-05, FR-06, FR-07, FR-08, BR-01, BR-08, BR-09
- **Scenarios:** 1, 2, 3, 4, 5

### GET /api/v1/admin/workshops/{id}/staff/available

**Description:** Returns a paginated list of staff members who are eligible to be assigned to the specified workshop (same organization, `STAFF` role, active status, not currently assigned, and no schedule conflict with other non-`CANCELED` workshops). Strictly requires workshop status `PUBLISHED` and execution prior to workshop start (`now() < starts_at`).  
**Required Role / Auth:** `ORGANIZER` — JWT with `role = ORGANIZER` and `org` claim matching workshop's `organization_id`.  
**Idempotency Requirement:** Not Required — read-only operation.

#### Request

**Headers:**

| Header          | Required | Description                            |
| :-------------- | :------- | :------------------------------------- |
| `Authorization` | Yes      | `Bearer <JWT>` (RS256, ORGANIZER role) |
| `Accept`        | No       | `application/json`                     |

**Path Parameters:**

| Parameter | Type | Description                           |
| :-------- | :--- | :------------------------------------ |
| `id`      | UUID | The unique identifier of the workshop |

**Query Parameters:**

| Parameter | Type    | Required | Default | Description                                                                                                |
| :-------- | :------ | :------- | :------ | :--------------------------------------------------------------------------------------------------------- |
| `q`       | String  | No       | —       | Free-text keyword search matched case-insensitively against `fullName` and `email` (max 100 characters).   |
| `cursor`  | String  | No       | —       | Keyset pagination cursor (opaque Base64-encoded string representing the last item from the previous page). |
| `size`    | Integer | No       | `20`    | Number of staff members per page. Min: 1, Max: 100.                                                        |

> **Sorting:** Always sorted by `fullName ASC, id ASC` (alphabetical order with user ID tiebreaker).

**Request Body:** None.

#### Response

**Success — 200 OK:**

```json
{
  "success": true,
  "message": "Available staff retrieved successfully.",
  "data": {
    "items": [
      {
        "userId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
        "fullName": "Bob Tran",
        "email": "bob@example.com",
        "avatarUrl": null,
        "phoneNumber": "+84909876543",
        "activeAssignmentsCount": 2
      }
    ],
    "cursor": {
      "nextCursor": null,
      "hasMore": false
    }
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Item Field Definitions:**

| Field                    | Type          | Description                                                                                                   |
| :----------------------- | :------------ | :------------------------------------------------------------------------------------------------------------ |
| `userId`                 | UUID string   | Unique identifier of the staff member                                                                         |
| `fullName`               | String        | Full name of the staff member                                                                                 |
| `email`                  | String        | Work email address of the staff member                                                                        |
| `avatarUrl`              | String / null | URL to the staff member's avatar image (null if no custom avatar uploaded)                                    |
| `phoneNumber`            | String / null | Contact phone number of the staff member (null if not provided in user profile)                               |
| `activeAssignmentsCount` | Integer       | Total number of non-CANCELED workshops across the organization currently assigned to this staff member (>= 0) |

**Error Responses:**

| Status                      | Error Type (`error.type`)       | Title                 | Condition                                                                                      |
| :-------------------------- | :------------------------------ | :-------------------- | :--------------------------------------------------------------------------------------------- |
| `400 Bad Request`           | `urn:unihub:error:bad-request`  | Bad Request           | Invalid UUID syntax for `{id}`, invalid `size`, or query parameter `q` exceeds 100 characters. |
| `401 Unauthorized`          | `urn:unihub:error:unauthorized` | Unauthorized          | Missing, expired, invalid-signature, or Redis-blacklisted Bearer JWT.                          |
| `403 Forbidden`             | `urn:unihub:error:forbidden`    | Forbidden             | Caller `role` is not `ORGANIZER`, or workshop does not belong to the caller's organization.    |
| `404 Not Found`             | `urn:unihub:error:not-found`    | Not Found             | No workshop exists with the specified `id`.                                                    |
| `409 Conflict`              | `urn:unihub:error:conflict`     | Conflict              | Workshop status is not `PUBLISHED` (e.g. `DRAFT`, `COMPLETED`, or `CANCELED`).                 |
| `422 Unprocessable Content` | `urn:unihub:error:validation`   | Unprocessable Content | Workshop has already started (`now() >= starts_at`), where staff discovery is disabled.        |
| `500 Internal Server Error` | `urn:unihub:error:internal`     | Internal Server Error | Unexpected database query failure.                                                             |

**Error Response Example (409 Conflict — Workshop in DRAFT):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:conflict",
    "title": "Conflict",
    "status": 409,
    "detail": "Workshop must be in PUBLISHED status before querying available staff.",
    "instance": "/api/v1/admin/workshops/a1b2c3d4-e5f6-7890-abcd-ef1234567890/staff/available"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Error Response Example (422 Unprocessable Content — Workshop Already Started):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:validation",
    "title": "Unprocessable Content",
    "status": 422,
    "detail": "Cannot query available staff after the workshop has started.",
    "instance": "/api/v1/admin/workshops/a1b2c3d4-e5f6-7890-abcd-ef1234567890/staff/available",
    "validationErrors": ["startsAt: Workshop has already started"]
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

#### Side Effects

None. This read-only query routes to PostgreSQL Read Replica via `@Transactional(readOnly = true)`. Check-then-act conflict validation is executed on the Primary DB during the subsequent POST batch assignment transaction, ensuring that eventual consistency on the replica does not compromise assignment integrity.

#### Spec Traceability

- **Satisfies:** FR-01, FR-02, FR-03, FR-09, FR-10, FR-11, FR-12, FR-13, FR-14, FR-15, BR-01, BR-03, BR-04, BR-05, BR-09, BR-10
- **Scenarios:** 1, 2, 3, 6, 7, 8, 9, 10, 11, 12

### POST /api/v1/admin/workshops/{id}/staff

**Description:** Atomically assigns one or more staff members to the specified workshop in a single batch. Rejects the entire batch if any member fails validation or has a conflict (all-or-nothing). Strictly requires workshop status `PUBLISHED` and execution prior to workshop start (`now() < starts_at`).  
**Required Role / Auth:** `ORGANIZER` — JWT with `role = ORGANIZER` and `org` claim matching workshop's `organization_id`.  
**Idempotency Requirement:** Not Required — duplicate assignment attempts are rejected deterministically with `409 Conflict`. Database unique constraint `UNIQUE (workshop_id, staff_user_id)` prevents concurrent double-assignment.

#### Request

**Headers:**

| Header          | Required | Description                            |
| :-------------- | :------- | :------------------------------------- |
| `Authorization` | Yes      | `Bearer <JWT>` (RS256, ORGANIZER role) |
| `Content-Type`  | Yes      | `application/json`                     |

**Path Parameters:**

| Parameter | Type | Description                           |
| :-------- | :--- | :------------------------------------ |
| `id`      | UUID | The unique identifier of the workshop |

**Query Parameters:** None.

**Request Body:**

```json
{
  "staffUserIds": [
    "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "7c9e6679-7425-40de-944b-e07fc1f90ae7"
  ]
}
```

**Validation Rules:**

- `staffUserIds` — Required. Non-empty array of valid UUID strings. Minimum 1 item, maximum 50 items per batch request.
- Intra-request de-duplication: Duplicate UUIDs within the same request array are de-duplicated server-side before execution.

#### Response

**Success — 201 Created:**

```json
{
  "success": true,
  "message": "Staff members assigned successfully.",
  "data": {
    "workshopId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "assignedStaffs": [
      {
        "userId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
        "fullName": "Alice Nguyen",
        "email": "alice@example.com",
        "avatarUrl": "https://minio.unihub.io/unihub-media/users/avatars/alice.jpg",
        "phoneNumber": "+84901234567",
        "assignedAt": "2026-09-29T09:15:00Z"
      },
      {
        "userId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
        "fullName": "Bob Tran",
        "email": "bob@example.com",
        "avatarUrl": null,
        "phoneNumber": "+84909876543",
        "assignedAt": "2026-09-29T09:15:00Z"
      }
    ]
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Response Field Definitions:**

| Field                          | Type            | Description                                                                     |
| :----------------------------- | :-------------- | :------------------------------------------------------------------------------ |
| `workshopId`                   | UUID string     | Target workshop ID                                                              |
| `assignedStaffs`               | Array of Object | List of staff assignment records created                                        |
| `assignedStaffs[].userId`      | UUID string     | Staff member user ID                                                            |
| `assignedStaffs[].fullName`    | String          | Staff member's full name                                                        |
| `assignedStaffs[].email`       | String          | Staff member's email                                                            |
| `assignedStaffs[].avatarUrl`   | String / null   | URL to the staff member's avatar image (null if no custom avatar uploaded)      |
| `assignedStaffs[].phoneNumber` | String / null   | Contact phone number of the staff member (null if not provided in user profile) |
| `assignedStaffs[].assignedAt`  | ISO 8601 string | UTC timestamp when assignment was created                                       |

**Error Responses:**

| Status                      | Error Type (`error.type`)       | Title                 | Condition                                                                                                                                                                 |
| :-------------------------- | :------------------------------ | :-------------------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `400 Bad Request`           | `urn:unihub:error:bad-request`  | Bad Request           | Malformed JSON syntax, unparseable UUIDs, empty `staffUserIds` array, or batch size exceeding 50 items.                                                                   |
| `401 Unauthorized`          | `urn:unihub:error:unauthorized` | Unauthorized          | Missing, expired, invalid-signature, or Redis-blacklisted Bearer JWT.                                                                                                     |
| `403 Forbidden`             | `urn:unihub:error:forbidden`    | Forbidden             | Caller `role` is not `ORGANIZER`, or workshop does not belong to the caller's organization.                                                                               |
| `404 Not Found`             | `urn:unihub:error:not-found`    | Not Found             | No workshop exists with the specified `id`.                                                                                                                               |
| `409 Conflict`              | `urn:unihub:error:conflict`     | Conflict              | Workshop status is not `PUBLISHED` (`DRAFT`, `COMPLETED`, `CANCELED`); OR one or more staff are already assigned; OR staff schedule overlap with another active workshop. |
| `422 Unprocessable Content` | `urn:unihub:error:validation`   | Unprocessable Content | Workshop has already started (`now() >= starts_at`); OR one or more staff IDs do not exist, are not `STAFF`, belong to another org, or are inactive (`validationErrors`). |
| `500 Internal Server Error` | `urn:unihub:error:internal`     | Internal Server Error | Unexpected database or Transactional Outbox persistence failure.                                                                                                          |

> **Validation Evaluation Order:** Checks are evaluated in strict priority:
>
> 1. Workshop exists (404)
> 2. Organization tenant ownership match (403)
> 3. Workshop status is `PUBLISHED` (409)
> 4. Workshop has not started: `now() < starts_at` (422)
> 5. Staff members valid: exist, role `STAFF`, same org, account active (422, includes `validationErrors`)
> 6. Not already assigned to this workshop (409)
> 7. No schedule overlap with active workshops (409)

**Error Response Example (409 Conflict — Workshop Not PUBLISHED):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:conflict",
    "title": "Conflict",
    "status": 409,
    "detail": "Workshop must be in PUBLISHED status before assigning staff.",
    "instance": "/api/v1/admin/workshops/a1b2c3d4-e5f6-7890-abcd-ef1234567890/staff"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Error Response Example (422 Unprocessable Content — Workshop Already Started):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:validation",
    "title": "Unprocessable Content",
    "status": 422,
    "detail": "Cannot assign staff after the workshop has started.",
    "instance": "/api/v1/admin/workshops/a1b2c3d4-e5f6-7890-abcd-ef1234567890/staff",
    "validationErrors": ["startsAt: Workshop has already started"]
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Error Response Example (409 Conflict — Already Assigned):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:conflict",
    "title": "Conflict",
    "status": 409,
    "detail": "One or more staff members are already assigned to this workshop.",
    "instance": "/api/v1/admin/workshops/a1b2c3d4-e5f6-7890-abcd-ef1234567890/staff"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Error Response Example (422 Unprocessable Content — Invalid Staff Member):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:validation",
    "title": "Unprocessable Content",
    "status": 422,
    "detail": "Validation failed for one or more staff members in the batch.",
    "instance": "/api/v1/admin/workshops/a1b2c3d4-e5f6-7890-abcd-ef1234567890/staff",
    "validationErrors": [
      "staffUserIds: Staff user e3f2a1b0-9876-4321-fedc-ba9876543210 does not exist, is inactive, does not hold the STAFF role, or belongs to another organization"
    ]
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

#### Side Effects

- Atomically inserts rows into `workshop_assignments` table in PostgreSQL Primary DB.
- Publishes one separate `StaffAssignedToWorkshopEvent` integration event per assigned staff member via Spring Modulith Transactional Outbox to RabbitMQ exchange `unihub.events` with routing key `notifications`.

#### Spec Traceability

- **Satisfies:** FR-01, FR-02, FR-03, FR-16, FR-17, FR-18, FR-19, FR-20, FR-21, FR-22, FR-23, BR-01, BR-02, BR-03, BR-04, BR-05, BR-06, BR-07, BR-09, BR-11
- **Scenarios:** 1, 2, 3, 13, 14, 15, 16, 17, 18, 23

### DELETE /api/v1/admin/workshops/{id}/staff/{userId}

**Description:** Revokes the assignment of a single staff member from the specified workshop. Strictly requires workshop status `PUBLISHED` and execution prior to workshop start (`now() < starts_at`).  
**Required Role / Auth:** `ORGANIZER` — JWT with `role = ORGANIZER` and `org` claim matching workshop's `organization_id`.  
**Idempotency Requirement:** Not Required — repeated DELETE on an already revoked assignment returns `404 Not Found`.

#### Request

**Headers:**

| Header          | Required | Description                            |
| :-------------- | :------- | :------------------------------------- |
| `Authorization` | Yes      | `Bearer <JWT>` (RS256, ORGANIZER role) |

**Path Parameters:**

| Parameter | Type | Description                                                           |
| :-------- | :--- | :-------------------------------------------------------------------- |
| `id`      | UUID | The unique identifier of the workshop                                 |
| `userId`  | UUID | The unique identifier of the staff member whose assignment is revoked |

**Query Parameters:** None.

**Request Body:** None.

#### Response

**Success — 204 No Content:**

_Empty response body (no payload)._

**Error Responses:**

| Status                      | Error Type (`error.type`)       | Title                 | Condition                                                                                       |
| :-------------------------- | :------------------------------ | :-------------------- | :---------------------------------------------------------------------------------------------- |
| `400 Bad Request`           | `urn:unihub:error:bad-request`  | Bad Request           | Invalid UUID syntax for `{id}` or `{userId}`.                                                   |
| `401 Unauthorized`          | `urn:unihub:error:unauthorized` | Unauthorized          | Missing, expired, invalid-signature, or Redis-blacklisted Bearer JWT.                           |
| `403 Forbidden`             | `urn:unihub:error:forbidden`    | Forbidden             | Caller `role` is not `ORGANIZER`, or workshop does not belong to the caller's organization.     |
| `404 Not Found`             | `urn:unihub:error:not-found`    | Not Found             | Workshop does not exist, or staff member `{userId}` is not currently assigned to this workshop. |
| `409 Conflict`              | `urn:unihub:error:conflict`     | Conflict              | Workshop status is not `PUBLISHED` (e.g. `DRAFT`, `COMPLETED`, or `CANCELED`).                  |
| `422 Unprocessable Content` | `urn:unihub:error:validation`   | Unprocessable Content | Workshop has already started (`now() >= starts_at`), where revocation is forbidden.             |
| `500 Internal Server Error` | `urn:unihub:error:internal`     | Internal Server Error | Unexpected database or Transactional Outbox persistence failure.                                |

**Error Response Example (404 Not Found — Assignment Not Found):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:not-found",
    "title": "Not Found",
    "status": 404,
    "detail": "Staff assignment not found for workshop id a1b2c3d4-e5f6-7890-abcd-ef1234567890 and user id 3fa85f64-5717-4562-b3fc-2c963f66afa6.",
    "instance": "/api/v1/admin/workshops/a1b2c3d4-e5f6-7890-abcd-ef1234567890/staff/3fa85f64-5717-4562-b3fc-2c963f66afa6"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Error Response Example (422 Unprocessable Content — Workshop Already Started):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:validation",
    "title": "Unprocessable Content",
    "status": 422,
    "detail": "Cannot revoke staff after the workshop has started.",
    "instance": "/api/v1/admin/workshops/a1b2c3d4-e5f6-7890-abcd-ef1234567890/staff/3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "validationErrors": ["startsAt: Workshop has already started"]
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

#### Side Effects

- Deletes the corresponding row from `workshop_assignments` in PostgreSQL Primary DB.
- Publishes one `StaffRevokedFromWorkshopEvent` integration event via Spring Modulith Transactional Outbox to RabbitMQ exchange `unihub.events` with routing key `notifications`.

#### Spec Traceability

- **Satisfies:** FR-01, FR-02, FR-03, FR-24, FR-25, FR-26, FR-27, FR-28, BR-01, BR-03, BR-04, BR-09, BR-11
- **Scenarios:** 1, 2, 3, 19, 20, 21, 22

## 4. Rate Limiting Behavior

All four endpoints in this contract fall strictly under the **Default Tier** of the Bucket4j + Redis Token Bucket rate limiter enforced at the Core Backend Monolith application filter layer (not at Nginx). _(Source: `docs/system-design.md` — §6 System Protection Mechanisms / Rate Limiting Tiers)_

| Endpoint                                             | Tier        |  Capacity  | Refill Rate  | Key                               |
| :--------------------------------------------------- | :---------- | :--------: | :----------: | :-------------------------------- |
| `GET /api/v1/admin/workshops/{id}/staff`             | **Default** | 100 tokens | 100 per 60 s | `jwt.sub` (authenticated user ID) |
| `GET /api/v1/admin/workshops/{id}/staff/available`   | **Default** | 100 tokens | 100 per 60 s | `jwt.sub`                         |
| `POST /api/v1/admin/workshops/{id}/staff`            | **Default** | 100 tokens | 100 per 60 s | `jwt.sub`                         |
| `DELETE /api/v1/admin/workshops/{id}/staff/{userId}` | **Default** | 100 tokens | 100 per 60 s | `jwt.sub`                         |

### Rationale: Why the Default Tier is Optimal (No Custom Tier Required)

1. **Low Caller Volume & Authenticated Scope:** All requests to these endpoints are authenticated `ORGANIZER` actors managing their own organization's workshops. Unlike public student registration routes that face burst traffic of 12,000 concurrent users within minutes, Organizer actions have a very small concurrency footprint.
2. **Infrequent Administrative Actions:** Assigning or revoking check-in staff is an administrative setup task performed during workshop planning, not a high-frequency polling endpoint. A bucket of 100 requests per 60 seconds per Organizer (`jwt.sub`) provides abundant headroom for normal web admin UI interactions while completely preventing script abuse or accidental client loops.
3. **No Dedicated Tier Conflicts:** The other defined tiers in `docs/system-design.md` are:
   - **Registration Tier** (`POST /api/v1/registrations/**`): 5 tokens / 10s (protects student rush & seat inventory).
   - **Auth Tier** (`/api/v1/auth/**`): 20 tokens / 60s (protects CPU-intensive Argon2id hashing & OTP).
   - **Stream Tier** (`GET /api/v1/stream/**`): 10 tokens / 60s (protects SSE connection sockets).
     None of these apply to `/api/v1/admin/workshops/**`. Introducing a custom tier for staff assignments would add unnecessary operational overhead and fragment rate-limiting configuration without architectural benefit.

### Bucket Exhaustion Behavior

When a caller exhausts their token bucket, the Core Backend returns HTTP `429 Too Many Requests` in the standard response envelope:

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:rate-limited",
    "title": "Too Many Requests",
    "status": 429,
    "detail": "Rate limit exceeded. Try again in 30 seconds.",
    "instance": "/api/v1/admin/workshops/a1b2c3d4-e5f6-7890-abcd-ef1234567890/staff"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Rate Limit Response Headers included on 429:**

| Header                  | Description                                             |
| :---------------------- | :------------------------------------------------------ |
| `X-RateLimit-Limit`     | Maximum tokens in the bucket (`100`).                   |
| `X-RateLimit-Remaining` | Tokens remaining after this request (`0` on a 429).     |
| `X-RateLimit-Reset`     | Unix epoch second at which the bucket will next refill. |

## 5. Idempotency Behavior

No endpoint in this contract requires a client-generated `Idempotency-Key` header:

- **`GET /api/v1/admin/workshops/{id}/staff`:** Read-only query; naturally idempotent.
- **`GET /api/v1/admin/workshops/{id}/staff/available`:** Read-only query; naturally idempotent.
- **`POST /api/v1/admin/workshops/{id}/staff` (batch assign):**
  - Duplicate submissions for staff members already assigned return `409 Conflict` (`STAFF_ALREADY_ASSIGNED`), preventing duplicate rows.
  - Concurrent race conditions between two parallel requests assigning the same user are caught by the PostgreSQL database constraint `UNIQUE (workshop_id, staff_user_id)`. The first transaction commits; the second triggers a `DataIntegrityViolationException` which the backend translates to a clean `409 Conflict` response.
  - This design avoids the overhead of Redis L1/PostgreSQL L2 idempotency tracking because staff assignment carries zero financial risk and has a natural unique constraint.
- **`DELETE /api/v1/admin/workshops/{id}/staff/{userId}` (revoke):**
  - Standard REST `DELETE` semantics. If the assignment row exists, it is deleted and returns `204 No Content`.
  - A subsequent repeated call finds no assignment and returns `404 Not Found`, which is a safe, deterministic outcome.

## 6. Cross-Endpoint Consistency Notes

1. **Standard `ApiResponse<T>` Envelope:** Every endpoint in this contract (except 204 No Content) wraps domain data in the uniform envelope `{ "success": boolean, "message": string, "data": ..., "metadata": { "processedBy": string, "traceId": string } }` matching `docs/api-standards.md` and Feature 2.1 (`workshop-creation-and-draft-management`).
2. **Unified RFC 7807 Error URIs:** All error responses adhere to standard `urn:unihub:error:*` naming (`urn:unihub:error:bad-request`, `urn:unihub:error:unauthorized`, `urn:unihub:error:forbidden`, `urn:unihub:error:not-found`, `urn:unihub:error:conflict`, `urn:unihub:error:validation`, `urn:unihub:error:rate-limited`, `urn:unihub:error:internal`).
3. **Standard RFC 7807 Error Semantics:** Error responses follow standard RFC 7807 Problem Details wrapped in `ApiResponse`. Client input and payload validation failures return `422 Unprocessable Content` with field-level issues aggregated in `validationErrors: [...]`. Business state and schedule conflict failures return clean `409 Conflict` responses without exposing non-standard custom attributes.
4. **Keyset Cursor Pagination Contract:** Keyset cursors returned in `data.cursor.nextCursor` serialize to opaque Base64-encoded JSON strings consistent with `shared/pagination/PageResponse.java` and `WorkshopRepositoryAdapter.java`.
5. **Database Routing & Consistency Strategy:** Per `docs/database-schema.md` (§4 Replication Lag & Consistency) and `TransactionAwareRoutingDataSource`:
   - Read collection endpoints (`GET .../staff` and `GET .../staff/available`) use `@Transactional(readOnly = true)` and route to **Read Replicas**, offloading expensive conflict evaluation and roster browsing from the primary database.
   - Mutation endpoints (`POST` batch assign and `DELETE` revoke) use write transactions on the **Primary DB**. All validation pre-checks (schedule conflict, duplicate assignment, status) execute against the Primary DB inside the write transaction (check-then-act), ensuring that eventual consistency on the replica cannot cause over-assignment or double-booking.
6. **Transactional Outbox Event Emission:** Both mutating operations publish fine-grained integration events (`StaffAssignedToWorkshopEvent` per assigned staff member, `StaffRevokedFromWorkshopEvent`) into the same local database transaction, externalized to RabbitMQ exchange `unihub.events` with routing key `notifications`.
7. **Consistent Staff Representation & UI Placeholder Handling:** All endpoints returning staff information (`GET .../staff`, `GET .../staff/available`, and `POST .../staff`) provide a uniform set of core profile attributes: `userId`, `fullName`, `email`, `avatarUrl`, and `phoneNumber`. Nullable fields (`avatarUrl`, `phoneNumber`) return `null` if unpopulated on the user's account, allowing client UI applications to predictably render avatar initials or fallback placeholders without separate profile lookups.
