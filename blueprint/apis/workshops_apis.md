# UniHub Workshop - Workshops API Contracts

## 1. Conventions

- **Base path:** `/api/v1/workshops`
- All responses use the standard envelope:

```json
{
  "success": true,
  "message": "...",
  "data": {},
  "metadata": {
    "statusCode": 200,
    "processedBy": "unihub-app-1",
    "timestamp": "2026-05-11T10:00:00Z"
  },
  "error": null
}
```

- Error payloads use RFC 7807 Problem Details inside `error`:

```json
{
  "success": false,
  "message": "Validation failed",
  "data": null,
  "metadata": {
    "statusCode": 400,
    "processedBy": "unihub-app-1",
    "timestamp": "2026-05-11T10:00:00Z"
  },
  "error": {
    "type": "https://api.unihub.local/problems/validation",
    "title": "Bad Request",
    "detail": "One or more fields are invalid.",
    "instance": "/api/v1/workshops",
    "validationErrors": [
      "title: must not be empty",
      "capacityTotal: must be >= 1"
    ]
  }
}
```

- **Pagination format:** Any list response MUST place array items in `data.items` and include `data.pagination` with `page`, `size`, `totalElements`, `totalPages`, `hasNext`, `hasPrevious`.
- **Read-after-write strategy (Master/Replica):**
  - Read (GET) requests are served from the read replica.
  - Write requests (POST, PUT, PATCH, DELETE) are executed on the Master.
  - Endpoints that mutate state MUST return the fully updated entity as read from the Master in the `data` field in the same response to avoid read-after-write inconsistencies.
- **Headers:** `Authorization: Bearer <token>` for protected endpoints.
- All JSON keys are **camelCase**.

> [!NOTE]
>
> - All write endpoints MUST return the Master-updated entity in `data` to avoid read-after-write inconsistencies for clients that immediately act on results.
> - Error responses MUST use RFC 7807 inside the `error` field as documented above.

## 2. Public Discovery APIs (Guest/Student)

### GET /api/v1/workshops

**Security:** Public

**Query Parameters:**

| Field       | Type             | Required | Description                                      |
| :---------- | :--------------- | :------- | :----------------------------------------------- |
| q           | string           | No       | Full-text search against title/description.      |
| organizerId | string           | No       | Filter workshops by organizing ID.               |
| page        | int              | No       | Zero-based page index. Default `0`.              |
| size        | int              | No       | Page size. Default `20`.                         |
| startDate   | string (ISO8601) | No       | Filter workshops starting on or after this date. |
| endDate     | string (ISO8601) | No       | Filter workshops ending on or before this date.  |
| minCapacity | int              | No       | Minimum `capacityTotal` filter.                  |
| sort        | string           | No       | e.g., `startTime,asc`.                           |

**Constraints / Business Rules:**

- Uses cache-aside for discovery responses; cache may be eventually consistent.
- Only `PUBLISHED` workshops are returned to public discovery.

**Success Response (200):**

```json
{
  "success": true,
  "message": "Workshops list retrieved",
  "data": {
    "items": [
      {
        "id": "workshop-123",
        "version": 1,
        "title": "Intro to Node",
        "description": "Tutorial about Node",
        "startTime": "2026-06-01T09:00:00Z",
        "endTime": "2026-06-01T12:00:00Z",
        "capacityTotal": 30,
        "capacityRemaining": 12,
        "status": "PUBLISHED",
        "location": "Main Hall",
        "venue": "Main Campus",
        "room": "Room 101",
        "roomLayoutKey": "CLASSROOM",
        "priceAmount": 0.0,
        "currency": "USD"
      }
    ],
    "pagination": {
      "page": 0,
      "size": 20,
      "totalElements": 1,
      "totalPages": 1,
      "hasNext": false,
      "hasPrevious": false
    }
  },
  "metadata": {
    "statusCode": 200,
    "processedBy": "unihub-app-1",
    "timestamp": "2026-05-11T10:00:00Z"
  },
  "error": null
}
```

**Common Errors:**

- 400 Bad Request (invalid query/filter).
- 429 Too Many Requests (rate limit).

### GET /api/v1/workshops/{id}

**Security:** Public

**Path Parameters:**

- `id` (string) - Workshop identifier.

**Constraints / Business Rules:**

- This endpoint is strictly public for Guests/Students. It MUST NOT return internal management details such as `assignedStaff`, and only returns workshops where status is `PUBLISHED`.
- Read served from replica; consumers should expect eventual consistency relative to latest writes.
- The `brochureUrl` returned (if present) is read from the `file_url` column on the `brochures` table and points to the S3/MinIO object URL.

**Success Response (200):**

```json
{
  "success": true,
  "message": "Workshop details retrieved",
  "data": {
    "id": "workshop-123",
    "version": 1,
    "title": "Intro to Node",
    "description": "Tutorial about Node",
    "startTime": "2026-06-01T09:00:00Z",
    "endTime": "2026-06-01T12:00:00Z",
    "capacityTotal": 30,
    "capacityRemaining": 12,
    "status": "PUBLISHED",
    "location": "Main Hall",
    "venue": "Main Campus",
    "room": "Room 101",
    "roomLayoutKey": "CLASSROOM",
    "priceAmount": 0.0,
    "currency": "USD",
    "speakers": [
      {
        "id": "sp-1",
        "name": "Alice Presenter",
        "bio": "Speaker bio",
        "sortOrder": 1
      }
    ],
    "brochureUrl": "https://assets.unihub.local/brochures/workshop-123.pdf"
  },
  "metadata": {
    "statusCode": 200,
    "processedBy": "unihub-app-1",
    "timestamp": "2026-05-11T10:00:00Z"
  },
  "error": null
}
```

**Common Errors:**

- 400 Bad Request (invalid id format).
- 401 Unauthorized (requesting unpublished workshop without ownership).
- 404 Not Found (workshop not found).

## 3. Core Lifecycle APIs (Organizer)

### GET /api/v1/workshops/{id}/management

**Security:** Bearer Token (Organizer)

**Path Parameters:**

- `id` (string) - Workshop identifier.

**Constraints / Business Rules:**

- Validates that the authenticated organizer owns the workshop (`organizer_id` matches token issuer/subject).
- Allows retrieving workshops in ANY status, including `DRAFT` and `CANCELED`.
- Read served from replica; consumers should expect eventual consistency relative to latest writes.

**Success Response (200):**

```json
{
  "success": true,
  "message": "Workshop management details retrieved",
  "data": {
    "id": "workshop-123",
    "version": 1,
    "title": "Intro to Node",
    "description": "Tutorial about Node",
    "startTime": "2026-06-01T09:00:00Z",
    "endTime": "2026-06-01T12:00:00Z",
    "capacityTotal": 30,
    "capacityRemaining": 12,
    "status": "DRAFT",
    "location": "Main Hall",
    "venue": "Main Campus",
    "room": "Room 101",
    "roomLayoutKey": "CLASSROOM",
    "priceAmount": 0.0,
    "currency": "USD",
    "speakers": [
      {
        "id": "sp-1",
        "name": "Alice Presenter",
        "bio": "Speaker bio",
        "sortOrder": 1
      }
    ],
    "assignedStaff": [
      {
        "id": "staff-uuid",
        "fullName": "Jane Staff",
        "email": "jane@unihub.edu"
      }
    ],
    "brochureUrl": "https://assets.unihub.local/brochures/workshop-123.pdf"
  },
  "metadata": {
    "statusCode": 200,
    "processedBy": "unihub-app-1",
    "timestamp": "2026-05-11T10:00:00Z"
  },
  "error": null
}
```

**Common Errors:**

- 401 Unauthorized (missing/invalid token).
- 403 Forbidden (caller does not own the workshop).
- 404 Not Found (workshop not found).

### POST /api/v1/workshops

**Security:** Bearer Token (Organizer)

**Request Body:**

```json
{
  "title": "Intro to Node",
  "description": "Tutorial about Node",
  "startTime": "2026-06-01T09:00:00Z",
  "endTime": "2026-06-01T12:00:00Z",
  "capacityTotal": 30,
  "location": "Main Hall",
  "priceAmount": 0.0,
  "currency": "USD",
  "venue": "Main Campus",
  "room": "Room 101",
  "roomLayoutKey": "CLASSROOM"
}
```

| Field         | Type             | Required | Constraints / Business Rules                                           |
| :------------ | :--------------- | :------- | :--------------------------------------------------------------------- |
| title         | string           | Yes      | Non-empty.                                                             |
| description   | string           | No       |                                                                        |
| startTime     | string (ISO8601) | Yes      | Must be before `endTime`.                                              |
| endTime       | string (ISO8601) | Yes      | Must be after `startTime`.                                             |
| capacityTotal | int              | Yes      | Must be >= 1.                                                          |
| location      | string           | No       |                                                                        |
| priceAmount   | decimal          | No       | Monetary amount in smallest unit or decimal as per client conventions. |
| currency      | string           | No       | ISO 4217 currency code.                                                |
| venue         | string           | No       | Venue name or identifier.                                              |
| room          | string           | No       | Room identifier within venue.                                          |
| roomLayoutKey | string           | No       | Room layout identifier, e.g. `CLASSROOM`, `THEATER`.                   |

**Constraints / Business Rules:**

- Creates a workshop in `DRAFT` status by default.
- Write executed on Master; response MUST include the created entity as returned by the Master transaction.

**Success Response (201):**

```json
{
  "success": true,
  "message": "Workshop created",
  "data": {
    "id": "workshop-123",
    "version": 1,
    "title": "Intro to Node",
    "description": "Tutorial about Node",
    "startTime": "2026-06-01T09:00:00Z",
    "endTime": "2026-06-01T12:00:00Z",
    "capacityTotal": 30,
    "capacityRemaining": 30,
    "status": "DRAFT",
    "location": "Main Hall",
    "venue": "Main Campus",
    "room": "Room 101",
    "roomLayoutKey": "CLASSROOM",
    "priceAmount": 0.0,
    "currency": "USD"
  },
  "metadata": {
    "statusCode": 201,
    "processedBy": "unihub-app-1",
    "timestamp": "2026-05-11T10:00:00Z"
  },
  "error": null
}
```

**Common Errors:**

- 400 Bad Request (validation).
- 401 Unauthorized (missing/invalid token).
- 403 Forbidden (caller not an organizer).
- 409 Conflict (duplicate workshop slug/title where uniqueness enforced).

### PUT /api/v1/workshops/{id}

**Security:** Bearer Token (Organizer)

**Path Parameters:**

- `id` (string) - Workshop identifier.

**Request Body:**

```json
{
  "title": "Intro to Node Updated",
  "description": "Tutorial about Node",
  "version": 1,
  "startTime": "2026-06-01T10:00:00Z",
  "endTime": "2026-06-01T13:00:00Z",
  "capacityTotal": 25,
  "location": "Main Hall",
  "priceAmount": 0.0,
  "currency": "USD",
  "venue": "Main Campus",
  "room": "Room 101",
  "roomLayoutKey": "THEATER"
}
```

| Field         | Type             | Required | Constraints / Business Rules                                                                                                    |
| :------------ | :--------------- | :------- | :------------------------------------------------------------------------------------------------------------------------------ |
| title         | string           | No       | If provided, non-empty.                                                                                                         |
| description   | string           | No       |                                                                                                                                 |
| startTime     | string (ISO8601) | No       | If provided, must be before `endTime`.                                                                                          |
| endTime       | string (ISO8601) | No       | If provided, must be after `startTime`.                                                                                         |
| capacityTotal | int              | No       | If provided, MUST be >= currently allocated seats. Attempting to reduce `capacityTotal` below allocated seats returns HTTP 400. |
| location      | string           | No       |                                                                                                                                 |
| priceAmount   | decimal          | No       | Monetary amount.                                                                                                                |
| currency      | string           | No       | ISO 4217 currency code.                                                                                                         |
| venue         | string           | No       | Venue name or identifier.                                                                                                       |
| room          | string           | No       | Room identifier within venue.                                                                                                   |
| roomLayoutKey | string           | No       | Room layout identifier, e.g. `CLASSROOM`, `THEATER`.                                                                            |
| version       | int              | Yes      | Optimistic lock version. Client MUST provide the current version to update.                                                     |

**Constraints / Business Rules:**

- The system MUST validate the optimistic lock `version`. If it does not match the database, return HTTP 409 Conflict.
- When updating `capacityTotal` for a `PUBLISHED` workshop, the system MUST validate new `capacityTotal` >= currently allocated seats. If violated, return HTTP 400 with a problem detail.
- If the workshop `status` is `PUBLISHED`, logistical fields (`startTime`, `endTime`, `location`, `venue`, `room`, `roomLayoutKey`) MUST NOT be modified via this endpoint. Client must use the Reschedule endpoint for logistical changes.
- If the workshop `status` is `PUBLISHED`, financial fields (`priceAmount`, `currency`) are STRICTLY IMMUTABLE and cannot be changed via any endpoint to prevent billing disputes with existing registrants.
- If `priceAmount` is greater than 0, `currency` MUST be provided.
- Writes executed on Master; response MUST include the updated entity as read from the Master.

**Success Response (200):**

```json
{
  "success": true,
  "message": "Workshop updated",
  "data": {
    "id": "workshop-123",
    "version": 2,
    "title": "Intro to Node Updated",
    "description": "Tutorial about Node",
    "startTime": "2026-06-01T10:00:00Z",
    "endTime": "2026-06-01T13:00:00Z",
    "capacityTotal": 25,
    "capacityRemaining": 7,
    "status": "PUBLISHED",
    "location": "Main Hall",
    "venue": "Main Campus",
    "room": "Room 101",
    "roomLayoutKey": "CLASSROOM",
    "priceAmount": 0.0,
    "currency": "USD",
    "speakers": [
      {
        "id": "sp-1",
        "name": "Alice Presenter",
        "bio": "Speaker bio",
        "sortOrder": 1
      }
    ],
    "assignedStaff": [
      {
        "id": "staff-uuid",
        "fullName": "Jane Staff",
        "email": "jane@unihub.edu"
      }
    ],
    "brochureUrl": "https://assets.unihub.local/brochures/workshop-123.pdf"
  },
  "metadata": {
    "statusCode": 200,
    "processedBy": "unihub-app-1",
    "timestamp": "2026-05-11T10:00:00Z"
  },
  "error": null
}
```

**Common Errors:**

- 400 Bad Request (validation or capacity shrink rule).
- 401 Unauthorized.
- 403 Forbidden (not organizer/owner).
- 404 Not Found (workshop not found).

### PATCH /api/v1/workshops/{id}/status

**Security:** Bearer Token (Organizer)

**Request Body:**

```json
{
  "status": "PUBLISHED",
  "version": 1
}
```

| Field   | Type   | Required | Constraints / Business Rules                                                       |
| :------ | :----- | :------- | :--------------------------------------------------------------------------------- |
| status  | string | Yes      | One of `PUBLISHED`, `CANCELED`.                                                    |
| version | int    | Yes      | Optimistic lock version. Client MUST provide the current version to change status. |

**Constraints / Business Rules:**

- Changing to `PUBLISHED` will validate capacity and required fields; if invalid return HTTP 400.
- Changing to `CANCELED` is terminal; system MUST free allocated seats and publish downstream events for cancellations.
- The system MUST validate the optimistic lock `version` before changing status. If mismatched, return HTTP 409 Conflict.
- Validates transition logic: `DRAFT` -> `PUBLISHED` or `PUBLISHED` -> `CANCELED`.
- Writes executed on Master; response MUST include the updated full workshop entity as returned by Master.

**Success Response (200):**

```json
{
  "success": true,
  "message": "Workshop status updated",
  "data": {
    "id": "workshop-123",
    "version": 2,
    "title": "Intro to Node",
    "description": "Tutorial about Node",
    "startTime": "2026-06-01T09:00:00Z",
    "endTime": "2026-06-01T12:00:00Z",
    "capacityTotal": 30,
    "capacityRemaining": 12,
    "status": "PUBLISHED",
    "location": "Main Hall",
    "venue": "Main Campus",
    "room": "Room 101",
    "roomLayoutKey": "CLASSROOM",
    "priceAmount": 0.0,
    "currency": "USD",
    "speakers": [
      {
        "id": "sp-1",
        "name": "Alice Presenter",
        "bio": "Speaker bio",
        "sortOrder": 1
      }
    ],
    "assignedStaff": [
      {
        "id": "staff-uuid",
        "fullName": "Jane Staff",
        "email": "jane@unihub.edu"
      }
    ],
    "brochureUrl": "https://assets.unihub.local/brochures/workshop-123.pdf"
  },
  "metadata": {
    "statusCode": 200,
    "processedBy": "unihub-app-1",
    "timestamp": "2026-05-11T10:00:00Z"
  },
  "error": null
}
```

**Common Errors:**

- 400 Bad Request (invalid transition).
- 401 Unauthorized.
- 403 Forbidden.
- 409 Conflict (concurrent state change).

### PATCH /api/v1/workshops/{id}/reschedule

**Security:** Bearer Token (Organizer)

**Request Body:**

```json
{
  "startTime": "2026-06-08T09:00:00Z",
  "endTime": "2026-06-08T12:00:00Z",
  "version": 1,
  "location": "Main Hall",
  "venue": "Main Campus",
  "room": "Room 101",
  "roomLayoutKey": "CLASSROOM"
}
```

| Field         | Type             | Required | Constraints / Business Rules                                                    |
| :------------ | :--------------- | :------- | :------------------------------------------------------------------------------ |
| startTime     | string (ISO8601) | Yes      | Must be before `endTime`.                                                       |
| endTime       | string (ISO8601) | Yes      | Must be after `startTime`.                                                      |
| location      | string           | No       | Optional new location name.                                                     |
| venue         | string           | No       | Optional new venue identifier.                                                  |
| room          | string           | No       | Optional new room identifier.                                                   |
| roomLayoutKey | string           | No       | Optional room layout identifier, e.g. `CLASSROOM`, `THEATER`.                   |
| version       | int              | Yes      | Optimistic lock version. Client MUST provide the current version to reschedule. |

**Constraints / Business Rules:**

- Rescheduling is allowed only for `PUBLISHED` workshops.
- The system MUST validate the optimistic lock `version` before applying the reschedule. If it does not match the database, return HTTP 409 Conflict.
- Rescheduling is allowed only for `PUBLISHED` workshops.
- Rescheduling MUST delete all `workshop_assignments` for that workshop. For every removed assignment the system MUST publish `StaffUnassignedFromWorkshopEvent(workshopId, staffId, removedAt)` and notify the organizer. The system MUST prompt the organizer to reassign staff to the rescheduled session.
- Writes executed on Master; response MUST include the updated full workshop entity as read from Master.

**Success Response (200):**

```json
{
  "success": true,
  "message": "Workshop rescheduled",
  "data": {
    "id": "workshop-123",
    "version": 2,
    "title": "Intro to Node",
    "description": "Tutorial about Node",
    "startTime": "2026-06-08T09:00:00Z",
    "endTime": "2026-06-08T12:00:00Z",
    "capacityTotal": 30,
    "capacityRemaining": 12,
    "status": "PUBLISHED",
    "location": "Main Hall",
    "venue": "Main Campus",
    "room": "Room 101",
    "roomLayoutKey": "CLASSROOM",
    "priceAmount": 0.0,
    "currency": "USD",
    "speakers": [
      {
        "id": "sp-1",
        "name": "Alice Presenter",
        "bio": "Speaker bio",
        "sortOrder": 1
      }
    ],
    "assignedStaff": [
      {
        "id": "staff-uuid",
        "fullName": "Jane Staff",
        "email": "jane@unihub.edu"
      }
    ],
    "brochureUrl": "https://assets.unihub.local/brochures/workshop-123.pdf"
  },
  "metadata": {
    "statusCode": 200,
    "processedBy": "unihub-app-1",
    "timestamp": "2026-05-11T10:00:00Z"
  },
  "error": null
}
```

**Common Errors:**

- 400 Bad Request (invalid dates).
- 401 Unauthorized.
- 403 Forbidden.
- 409 Conflict (assignment removal race).

### DELETE /api/v1/workshops/{id}

**Security:** Bearer Token (Organizer)

**Query Parameters:**

| Field   | Type | Required | Description                                                                          |
| :------ | :--- | :------- | :----------------------------------------------------------------------------------- |
| version | int  | Yes      | Optimistic lock version. Client MUST provide the current workshop version to delete. |

**Constraints / Business Rules:**

- Only `DRAFT` workshops can be deleted. Attempting to delete `PUBLISHED` or `CANCELED` workshops returns HTTP 400.
- The system MUST validate the optimistic lock `version` before deleting to prevent stale deletes. If mismatched, return HTTP 409 Conflict.
- Writes executed on Master; response returns `204 No Content` with the standard envelope set to `data: null`.

**Success Response (204):**

```json
{
  "success": true,
  "message": "Workshop deleted",
  "data": null,
  "metadata": {
    "statusCode": 204,
    "processedBy": "unihub-app-1",
    "timestamp": "2026-05-11T10:00:00Z"
  },
  "error": null
}
```

**Common Errors:**

- 400 Bad Request (invalid state for deletion).
- 401 Unauthorized.
- 403 Forbidden.
- 404 Not Found.

## 4. Sub-resources APIs (Organizer)

### POST /api/v1/workshops/{id}/speakers

**Security:** Bearer Token (Organizer)

**Request Body:**

```json
{
  "name": "Alice Presenter",
  "bio": "Speaker bio",
  "sortOrder": 1,
  "version": 1
}
```

| Field     | Type   | Required | Constraints / Business Rules                                                                |
| :-------- | :----- | :------- | :------------------------------------------------------------------------------------------ |
| name      | string | Yes      | Non-empty.                                                                                  |
| bio       | string | No       |                                                                                             |
| sortOrder | int    | No       | Optional ordering integer.                                                                  |
| version   | int    | Yes      | Optimistic lock version. Client MUST provide the current workshop version to add a speaker. |

**Constraints / Business Rules:**

- System MUST validate the optimistic lock `version`. If it does not match the database, return HTTP 409 Conflict.
- Adding a speaker mutates the workshop aggregate and MUST increment its version.
- Writes executed on Master; response MUST include the updated full workshop entity as read from Master.

**Success Response (201):**

```json
{
  "success": true,
  "message": "Speaker added",
  "data": {
    "id": "workshop-123",
    "version": 2,
    "title": "Intro to Node",
    "description": "Tutorial about Node",
    "startTime": "2026-06-01T09:00:00Z",
    "endTime": "2026-06-01T12:00:00Z",
    "capacityTotal": 30,
    "capacityRemaining": 12,
    "status": "DRAFT",
    "location": "Main Hall",
    "venue": "Main Campus",
    "room": "Room 101",
    "roomLayoutKey": "CLASSROOM",
    "priceAmount": 0.0,
    "currency": "USD",
    "speakers": [
      {
        "id": "sp-1",
        "name": "Alice Presenter",
        "bio": "Speaker bio",
        "sortOrder": 1
      }
    ],
    "assignedStaff": [],
    "brochureUrl": null
  },
  "metadata": {
    "statusCode": 201,
    "processedBy": "unihub-app-1",
    "timestamp": "2026-05-11T10:00:00Z"
  },
  "error": null
}
```

**Common Errors:**

- 400 Bad Request (validation).
- 401 Unauthorized.
- 403 Forbidden (not owner).
- 404 Not Found (workshop not found).
- 409 Conflict (optimistic lock mismatch).

### DELETE /api/v1/workshops/{id}/speakers/{speakerId}

**Security:** Bearer Token (Organizer)

**Path Parameters:**

- `id` (string) - Workshop identifier.
- `speakerId` (string) - Speaker identifier to remove.

**Query Parameters:**

| Field   | Type | Required | Description                                                                                    |
| :------ | :--- | :------- | :--------------------------------------------------------------------------------------------- |
| version | int  | Yes      | Optimistic lock version. Client MUST provide the current workshop version to remove a speaker. |

**Constraints / Business Rules:**

- System MUST validate the optimistic lock `version`. If it does not match the database, return HTTP 409 Conflict.
- Removing a speaker does not delete the speaker profile globally; it only removes the workshop association.
- Removal mutates the workshop aggregate and MUST increment its version.
- Writes executed on Master; response MUST include the updated full workshop entity as read from Master.

**Success Response (200):**

```json
{
  "success": true,
  "message": "Speaker removed",
  "data": {
    "id": "workshop-123",
    "version": 2,
    "title": "Intro to Node",
    "description": "Tutorial about Node",
    "startTime": "2026-06-01T09:00:00Z",
    "endTime": "2026-06-01T12:00:00Z",
    "capacityTotal": 30,
    "capacityRemaining": 12,
    "status": "DRAFT",
    "location": "Main Hall",
    "venue": "Main Campus",
    "room": "Room 101",
    "roomLayoutKey": "CLASSROOM",
    "priceAmount": 0.0,
    "currency": "USD",
    "speakers": [],
    "assignedStaff": [],
    "brochureUrl": null
  },
  "metadata": {
    "statusCode": 200,
    "processedBy": "unihub-app-1",
    "timestamp": "2026-05-11T10:00:00Z"
  },
  "error": null
}
```

**Common Errors:**

- 400 Bad Request (invalid version format).
- 401 Unauthorized.
- 403 Forbidden (not owner).
- 404 Not Found (workshop or speaker not found).
- 409 Conflict (optimistic lock mismatch).

### POST /api/v1/workshops/{id}/brochure

**Security:** Bearer Token (Organizer)

**Request:** `multipart/form-data`

| Field | Type         | Required | Description                     |
| :---- | :----------- | :------- | :------------------------------ |
| file  | binary (PDF) | Yes      | The brochure PDF file (max 5MB) |

**Constraints / Business Rules:**

- Accepts a PDF file, stores the object in S3/MinIO, and persists brochure metadata in the `brochures` table. Returns `202 Accepted` immediately after the file is stored and metadata is persisted; async processing (virus scan, PDF optimization, AI summarization) continues in the background.
- The `aiSummaryStatus` in the response indicates the async summarization job has been queued.
- Writes executed on Master; the upload is acknowledged immediately and processing happens asynchronously.

**Success Response (202):**

```json
{
  "success": true,
  "message": "Brochure upload accepted",
  "data": {
    "brochureId": "uuid",
    "fileUrl": "https://assets.unihub.local/brochures/workshop-123.pdf",
    "brochureStatus": "UPLOADED",
    "aiSummaryStatus": "PROCESSING"
  },
  "metadata": {
    "statusCode": 202,
    "processedBy": "unihub-app-1",
    "timestamp": "2026-05-11T10:00:00Z"
  },
  "error": null
}
```

**Common Errors:**

- 400 Bad Request (invalid file type/size).
- 401 Unauthorized.
- 403 Forbidden.
- 413 Payload Too Large.

## 5. Staff Management APIs (Organizer)

### GET /api/v1/workshops/{id}/staff/available

**Security:** Bearer Token (Organizer)

**Query Parameters:**

| Field | Type | Required | Description                         |
| :---- | :--- | :------- | :---------------------------------- |
| page  | int  | No       | Zero-based page index. Default `0`. |
| size  | int  | No       | Page size. Default `20`.            |

**Constraints / Business Rules:**

- Returns staff members who are available for the workshop timeframe and have no schedule overlaps.
- Read from replica; accept eventual consistency.

**Success Response (200):**

```json
{
  "success": true,
  "message": "Available staff retrieved",
  "data": {
    "items": [
      {
        "id": "staff-1",
        "fullName": "Jordan Lee",
        "email": "jordan@unihub.edu"
      }
    ],
    "pagination": {
      "page": 0,
      "size": 20,
      "totalElements": 1,
      "totalPages": 1,
      "hasNext": false,
      "hasPrevious": false
    }
  },
  "metadata": {
    "statusCode": 200,
    "processedBy": "unihub-app-1",
    "timestamp": "2026-05-11T10:00:00Z"
  },
  "error": null
}
```

**Common Errors:**

- 400 Bad Request.
- 401 Unauthorized.
- 403 Forbidden.

### POST /api/v1/workshops/{id}/staff

**Security:** Bearer Token (Organizer)

**Request Body:**

```json
{
  "staffId": "staff-1",
  "version": 1
}
```

| Field   | Type   | Required | Constraints / Business Rules                                                               |
| :------ | :----- | :------- | :----------------------------------------------------------------------------------------- |
| staffId | string | Yes      | Must reference an existing staff member in the organizer tenant.                           |
| version | int    | Yes      | Optimistic lock version. Client MUST provide the current workshop version to assign staff. |

**Constraints / Business Rules:**

- The system MUST validate the optimistic lock `version`. If it does not match the database, return HTTP 409 Conflict.
- Assigning staff mutates the workshop aggregate root and MUST increment the workshop's version.
- Assignments are created on the Master and the response MUST include the updated full `workshop` entity as read from Master.
- If the staff has overlapping assignments, return HTTP 409.

**Success Response (201):**

```json
{
  "success": true,
  "message": "Staff assigned to workshop",
  "data": {
    "id": "workshop-123",
    "version": 2,
    "title": "Intro to Node",
    "description": "Tutorial about Node",
    "startTime": "2026-06-01T09:00:00Z",
    "endTime": "2026-06-01T12:00:00Z",
    "capacityTotal": 30,
    "capacityRemaining": 12,
    "status": "PUBLISHED",
    "location": "Main Hall",
    "venue": "Main Campus",
    "room": "Room 101",
    "priceAmount": 0.0,
    "currency": "USD",
    "speakers": [
      {
        "id": "sp-1",
        "name": "Alice Presenter",
        "bio": "Speaker bio",
        "sortOrder": 1
      }
    ],
    "assignedStaff": [
      {
        "id": "staff-uuid",
        "fullName": "Jane Staff",
        "email": "jane@unihub.edu"
      }
    ],
    "brochureUrl": "https://assets.unihub.local/brochures/workshop-123.pdf"
  },
  "metadata": {
    "statusCode": 201,
    "processedBy": "unihub-app-1",
    "timestamp": "2026-05-11T10:00:00Z"
  },
  "error": null
}
```

**Common Errors:**

- 400 Bad Request.
- 401 Unauthorized.
- 403 Forbidden.
- 404 Not Found (staff/workshop not found).
- 409 Conflict (schedule overlap or assignment already exists).

### DELETE /api/v1/workshops/{id}/staff/{staffId}

**Security:** Bearer Token (Organizer)

**Path Parameters:**

- `id` (string) - Workshop identifier.
- `staffId` (string) - Staff identifier to remove.

**Query Parameters:**

| Field   | Type | Required | Description                                                                                |
| :------ | :--- | :------- | :----------------------------------------------------------------------------------------- |
| version | int  | Yes      | Optimistic lock version. Client MUST provide the current workshop version to remove staff. |

**Constraints / Business Rules:**

- The system MUST validate the optimistic lock `version`. If it does not match the database, return HTTP 409 Conflict.
- Removing staff mutates the workshop aggregate root and MUST increment the workshop's version.
- Removes the assignment on the Master and publishes `StaffUnassignedFromWorkshopEvent(workshopId, staffId, removedAt)`.
- Response MUST include updated full workshop entity from Master.

**Success Response (200):**

```json
{
  "success": true,
  "message": "Staff unassigned",
  "data": {
    "id": "workshop-123",
    "version": 2,
    "title": "Intro to Node",
    "description": "Tutorial about Node",
    "startTime": "2026-06-01T09:00:00Z",
    "endTime": "2026-06-01T12:00:00Z",
    "capacityTotal": 30,
    "capacityRemaining": 12,
    "status": "PUBLISHED",
    "location": "Main Hall",
    "venue": "Main Campus",
    "room": "Room 101",
    "priceAmount": 0.0,
    "currency": "USD",
    "speakers": [
      {
        "id": "sp-1",
        "name": "Alice Presenter",
        "bio": "Speaker bio",
        "sortOrder": 1
      }
    ],
    "assignedStaff": [],
    "brochureUrl": "https://assets.unihub.local/brochures/workshop-123.pdf"
  },
  "metadata": {
    "statusCode": 200,
    "processedBy": "unihub-app-1",
    "timestamp": "2026-05-11T10:00:00Z"
  },
  "error": null
}
```

**Common Errors:**

- 400 Bad Request.
- 401 Unauthorized.
- 403 Forbidden.
- 404 Not Found.

## 6. Analytics & Reporting APIs (Organizer)

### GET /api/v1/workshops/management

**Security:** Bearer Token (Organizer)

**Query Parameters:**

| Field     | Type             | Required | Description                                                                       |
| :-------- | :--------------- | :------- | :-------------------------------------------------------------------------------- |
| page      | int              | No       | Zero-based page index. Default `0`.                                               |
| size      | int              | No       | Page size. Default `20`.                                                          |
| status    | string           | No       | Filter workshops by status (e.g., `DRAFT`, `PUBLISHED`, `CANCELED`, `COMPLETED`). |
| startDate | string (ISO8601) | No       | Filter workshops with startTime >= this date/time.                                |
| endDate   | string (ISO8601) | No       | Filter workshops with startTime <= this date/time.                                |
| sort      | string           | No       | Sort order, e.g. `startTime,asc` or `createdAt,desc`.                             |

**Constraints / Business Rules:**

- Returns workshops owned by the authenticated organizer. Read from replica.

**Success Response (200):**

```json
{
  "success": true,
  "message": "Managed workshops retrieved",
  "data": {
    "items": [
      {
        "id": "workshop-123",
        "title": "Intro to Node",
        "status": "PUBLISHED",
        "startTime": "2026-06-01T09:00:00Z",
        "endTime": "2026-06-01T12:00:00Z",
        "capacityTotal": 30,
        "capacityRemaining": 12
      }
    ],
    "pagination": {
      "page": 0,
      "size": 20,
      "totalElements": 1,
      "totalPages": 1,
      "hasNext": false,
      "hasPrevious": false
    }
  },
  "metadata": {
    "statusCode": 200,
    "processedBy": "unihub-app-1",
    "timestamp": "2026-05-11T10:00:00Z"
  },
  "error": null
}
```

**Common Errors:**

- 401 Unauthorized.
- 403 Forbidden.

### GET /api/v1/workshops/{id}/analytics

**Security:** Bearer Token (Organizer)

**Constraints / Business Rules:**

- Returns aggregated stats (registrations, confirmed, capacity utilization). Read from replica.

**Success Response (200):**

```json
{
  "success": true,
  "message": "Workshop analytics retrieved",
  "data": {
    "id": "workshop-123",
    "registrations": 28,
    "confirmed": 24,
    "checkedInCount": 20,
    "canceledCount": 4,
    "noShowCount": 4,
    "capacityTotal": 30,
    "capacityRemaining": 6,
    "utilizationPercent": 80
  },
  "metadata": {
    "statusCode": 200,
    "processedBy": "unihub-app-1",
    "timestamp": "2026-05-11T10:00:00Z"
  },
  "error": null
}
```

**Common Errors:**

- 401 Unauthorized.
- 403 Forbidden.
- 404 Not Found.

### GET /api/v1/workshops/{id}/attendees

**Security:** Bearer Token (Organizer)

**Query Parameters:**

| Field  | Type   | Required | Description                                                                         |
| :----- | :----- | :------- | :---------------------------------------------------------------------------------- |
| page   | int    | No       | Zero-based page index. Default `0`.                                                 |
| size   | int    | No       | Page size. Default `20`.                                                            |
| status | string | No       | Filter attendees by registration status (e.g., `PENDING`, `CONFIRMED`, `CANCELED`). |

**Constraints / Business Rules:**

- Returns registrations for the workshop with filtering for `PENDING`/`CONFIRMED`/`CANCELED` if requested.
- Read from replica.

**Success Response (200):**

```json
{
  "success": true,
  "message": "Attendee list retrieved",
  "data": {
    "items": [
      {
        "id": "reg-1",
        "studentId": "STU-10992",
        "fullName": "Ava Student",
        "email": "student@unihub.edu",
        "status": "CONFIRMED"
      }
    ],
    "pagination": {
      "page": 0,
      "size": 20,
      "totalElements": 1,
      "totalPages": 1,
      "hasNext": false,
      "hasPrevious": false
    }
  },
  "metadata": {
    "statusCode": 200,
    "processedBy": "unihub-app-1",
    "timestamp": "2026-05-11T10:00:00Z"
  },
  "error": null
}
```

**Common Errors:**

- 401 Unauthorized.
- 403 Forbidden.
- 404 Not Found.
