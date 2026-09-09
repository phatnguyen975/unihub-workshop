# API Contract: Real-Time Event Streaming Hub

> **Status:** APPROVED  
> **Spec:** `specs/real-time-event-streaming-hub/SPEC.md`  
> **Last revised:** 2026-09-09

## 1. Overview

This contract defines the network interface for the **Real-Time Event Streaming Hub** (`shared/realtime` module in the Core Backend Monolith). It specifies the long-lived HTTP streaming endpoint (`GET /api/v1/stream/events`), the Server-Sent Events (SSE) wire protocol, client handshake authentication, keep-alive heartbeat framing, RFC 7807 error responses, and the complete schemas for all multiplexed real-time events in the Unified Event Catalog.

## 2. Global Standards Applied

- **URI Structure / Versioning:** `GET /api/v1/stream/events` conformant with REST API standards (`docs/api-standards.md`).
- **Streaming Protocol:** Server-Sent Events (W3C Recommendation / RFC 8895) over HTTP/1.1 or HTTP/2.
- **Success Stream MIME Type:** `text/event-stream; charset=UTF-8`.
- **Response Headers:**
  - `Cache-Control: no-cache, no-transform`
  - `Connection: keep-alive`
  - `X-Accel-Buffering: no` (disables reverse proxy buffering in Nginx)
- **Error Response Format:** RFC 7807 Problem Details wrapped in the standard JSON envelope (`{"success": false, "error": {...}, "metadata": {...}}`) with Content-Type `application/problem+json`, returned synchronously on handshake rejection before stream establishment.
- **Rate Limiting:** Dedicated `streamTier` (`capacity: 10`, `refill-tokens: 10`, `refill-duration: 60s`) mapped to `/api/v1/stream/**` via Bucket4j distributed token bucket. Protects backend from socket exhaustion while isolating browsing quota.
- **Authentication:** Standard HTTP Bearer header (`Authorization: Bearer <jwt>`) using RS256 JWT tokens. Passing tokens via query parameters is strictly forbidden to prevent token leakage in web server access logs.
- **Client Implementation Recommendation:** Web clients must use `@microsoft/fetch-event-source` to enable custom `Authorization` headers. Mobile React Native clients use `react-native-sse`.
- **Date/Time Formatting:** ISO 8601 UTC string format (e.g. `2026-09-09T05:00:00Z`).
- **Identifier Conventions:** UUID v4 strings for `id`, `eventId`, `userId`, `workshopId`, and `registrationId`.

## 3. Endpoints

### `GET /api/v1/stream/events`

**Description:** Establishes a long-lived, multiplexed Server-Sent Events connection delivering platform events to the authenticated client in real time.  
**Required Role / Auth:** `AUTHENTICATED` (`STUDENT`, `ORGANIZER`, `STAFF`). Requires an active account (`is_active = true`) and a valid, non-blacklisted RS256 JWT access token.  
**Idempotency Requirement:** Not Required — Read-only persistent HTTP streaming connection.

#### Request

**Headers:**

| Header          | Required | Type     | Description                                                                   |
| :-------------- | :------: | :------- | :---------------------------------------------------------------------------- |
| `Authorization` | **Yes**  | `String` | Bearer token format: `Bearer <jwt_access_token>`.                             |
| `Accept`        |    No    | `String` | Expected MIME type: `text/event-stream`.                                      |
| `Last-Event-ID` |    No    | `String` | UUID of the last event received prior to reconnection (optional resume hint). |

**Path Parameters:** None.

**Query Parameters:** None.

**Request Body:** None.

#### Response

**Success — `200 OK` (Stream Established):**

**Headers:**

```http
HTTP/1.1 200 OK
Content-Type: text/event-stream; charset=UTF-8
Cache-Control: no-cache, no-transform
Connection: keep-alive
X-Accel-Buffering: no
```

**Stream Wire Format:**

Every transmission over the socket conforms to standard SSE syntax, separated by double newlines (`\n\n`):

##### 1. Heartbeat Comment (every 15 seconds)

```text
: ping

```

_(Begins with a colon `:`; treated as an ignored comment by client parsers to maintain TCP keep-alive)_

##### 2. Event Envelope Framing

```text
id: <uuid>
event: <event_name>
data: {"eventId":"<uuid>","eventType":"<event_name>","timestamp":"<iso8601_utc>","payload":{<event_specific_fields>}}

```

#### Unified Event Catalog Schemas

The following 7 event types are multiplexed over the single connection:

##### 1. `notification.new`

- **Module Owner:** `notifications`
- **Trigger:** An in-app notification is persisted for the recipient.
- **Wire Example:**

```text
id: 550e8400-e29b-41d4-a716-446655440001
event: notification.new
data: {"eventId":"550e8400-e29b-41d4-a716-446655440001","eventType":"notification.new","timestamp":"2026-09-09T05:15:00Z","payload":{"id":"7f2c1b4d-9a8e-4a6c-b3d1-e5c7a9f8b012","title":"Workshop Venue Changed","type":"WORKSHOP_RESCHEDULED","linkUrl":"/workshops/9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d","createdAt":"2026-09-09T05:15:00Z"}}

```

##### 2. `notification.read`

- **Module Owner:** `notifications`
- **Trigger:** Single or all notifications marked as read via PATCH API (multi-device badge sync).
- **Wire Example:**

```text
id: 550e8400-e29b-41d4-a716-446655440002
event: notification.read
data: {"eventId":"550e8400-e29b-41d4-a716-446655440002","eventType":"notification.read","timestamp":"2026-09-09T05:15:30Z","payload":{"id":"7f2c1b4d-9a8e-4a6c-b3d1-e5c7a9f8b012","all":false,"unreadCount":2,"readAt":"2026-09-09T05:15:30Z"}}

```

##### 3. `ai.summary.completed`

- **Module Owner:** `workshops`
- **Trigger:** Background worker successfully completes brochure summarization.
- **Wire Example:**

```text
id: 550e8400-e29b-41d4-a716-446655440003
event: ai.summary.completed
data: {"eventId":"550e8400-e29b-41d4-a716-446655440003","eventType":"ai.summary.completed","timestamp":"2026-09-09T05:16:00Z","payload":{"jobId":"1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d","workshopId":"9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d","resultText":"This interactive workshop covers advanced Cloud-Native architecture..."}}

```

##### 4. `ai.summary.failed`

- **Module Owner:** `workshops`
- **Trigger:** Background worker brochure summarization fails after max retries.
- **Wire Example:**

```text
id: 550e8400-e29b-41d4-a716-446655440004
event: ai.summary.failed
data: {"eventId":"550e8400-e29b-41d4-a716-446655440004","eventType":"ai.summary.failed","timestamp":"2026-09-09T05:16:15Z","payload":{"jobId":"1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d","workshopId":"9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d","errorMessage":"PDF document contains encrypted text layer and cannot be extracted."}}

```

##### 5. `attendance.updated`

- **Module Owner:** `attendances`
- **Trigger:** Staff check-in sync batch completes (pushed to organizer dashboard and assigned staff).
- **Wire Example:**

```text
id: 550e8400-e29b-41d4-a716-446655440005
event: attendance.updated
data: {"eventId":"550e8400-e29b-41d4-a716-446655440005","eventType":"attendance.updated","timestamp":"2026-09-09T05:17:00Z","payload":{"workshopId":"9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d","checkedInCount":85,"capacity":100,"lastSyncedAt":"2026-09-09T05:17:00Z"}}

```

##### 6. `registration.updated`

- **Module Owner:** `registrations`
- **Trigger:** A workshop registration seat is confirmed or canceled (pushed to organizer dashboard).
- **Wire Example:**

```text
id: 550e8400-e29b-41d4-a716-446655440006
event: registration.updated
data: {"eventId":"550e8400-e29b-41d4-a716-446655440006","eventType":"registration.updated","timestamp":"2026-09-09T05:17:30Z","payload":{"workshopId":"9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d","confirmedCount":95,"pendingCount":5,"capacity":100,"availableSeats":0,"lastUpdatedAt":"2026-09-09T05:17:30Z"}}

```

##### 7. `payment.confirmed`

- **Module Owner:** `payments`
- **Trigger:** Stripe payment webhook confirms student registration fee, issuing QR code ticket.
- **Wire Example:**

```text
id: 550e8400-e29b-41d4-a716-446655440007
event: payment.confirmed
data: {"eventId":"550e8400-e29b-41d4-a716-446655440007","eventType":"payment.confirmed","timestamp":"2026-09-09T05:18:00Z","payload":{"registrationId":"3c4d5e6f-7a8b-9c0d-1e2f-3a4b5c6d7e8f","workshopId":"9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d","qrPayload":"unihub:reg:3c4d5e6f-7a8b-9c0d-1e2f-3a4b5c6d7e8f:sig:abc123xyz","qrIssuedAt":"2026-09-09T05:18:00Z"}}

```

#### Error Responses

When a handshake fails, the server rejects the request immediately before establishing the stream, returning a standard RFC 7807 problem detail response:

| HTTP Status               | Error Type Identifier                  | Reason / Condition                                                            |
| :------------------------ | :------------------------------------- | :---------------------------------------------------------------------------- |
| `401 Unauthorized`        | `urn:unihub:error:unauthorized`        | Missing, malformed, or signature-invalid JWT in `Authorization` header.       |
| `401 Unauthorized`        | `urn:unihub:error:token-revoked`       | JWT `jti` is present in Redis blacklist (user logged out).                    |
| `403 Forbidden`           | `urn:unihub:error:account-inactive`    | Authenticated user account has `is_active = false` (banned or unverified).    |
| `429 Too Many Requests`   | `urn:unihub:error:rate-limited`        | Client exceeded distributed token bucket rate limit on handshake endpoint.    |
| `503 Service Unavailable` | `urn:unihub:error:service-unavailable` | Redis blacklist is unreachable during handshake (ADR-010 fail-closed policy). |

##### Example: 401 Unauthorized Problem Detail Response

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:unauthorized",
    "title": "Unauthorized",
    "status": 401,
    "detail": "Full authentication is required to establish real-time event stream.",
    "instance": "/api/v1/stream/events"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

##### Example: 429 Too Many Requests Problem Detail Response

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:rate-limited",
    "title": "Too Many Requests",
    "status": 429,
    "detail": "Rate limit exceeded. Try again in 10 seconds.",
    "instance": "/api/v1/stream/events"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

#### Side Effects

1. **In-Memory Registration:** The backend registers a new `SseEmitter` instance in `SseEmitterRegistry` under the client's `userId`.
2. **Metrics Emission:** Increments active connection gauge `realtime.sse.active_connections`.
3. **Automatic Cleanup on Socket Termination:** Triggers deregistration and decrements gauge upon client abort, network drop, or timeout.

#### Spec Traceability

- **Functional Requirements:** FR-01, FR-02, FR-03, FR-04, FR-05, FR-06, FR-07, FR-08, FR-09, FR-10, FR-15, FR-16.
- **Acceptance Criteria:** Satisfies Scenarios 1, 2, 3, 4, 5, 8 in `SPEC.md`.
