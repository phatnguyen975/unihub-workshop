# UniHub Workshop — API Standards

## API Design & Versioning Standards

This section defines a predictable, robust contract between the clients and the backend to keep integrations stable as the system evolves.

### Versioning Strategy

We use URI path versioning (e.g., `/api/v1/...`) because it is explicit, cache-friendly, and easily routable at the API Gateway compared to header-based versioning.

> [!NOTE]
> This document defines global API patterns. Endpoint-level schemas and examples are the source of truth in the live OpenAPI/Swagger spec at `/swagger-ui.html`.

### Pagination & Filtering

All collection endpoints use cursor-based (keyset) pagination to guarantee stable `O(1)` performance at scale and prevent data drift under concurrent writes. Clients pass a `?cursor=` query parameter to fetch the next window and may pass `?size=` to control batch size (e.g., `?cursor=...&size=20`); filters remain optional query parameters. The backend must enforce a default page size (usually 20) and a strict hard-cap maximum (e.g., 100) to protect database performance from oversized requests.

### CORS Policy (Web Portal)

Wildcard origins (`*`) are strictly prohibited in production. The backend must explicitly whitelist allowed web origins (staging and production domains, or localhost for development). Allowed methods must explicitly include `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`. Custom headers such as `X-Correlation-ID`, `Idempotency-Key`, and `X-RateLimit-*` must be explicitly configured in both `Access-Control-Allow-Headers` and `Access-Control-Expose-Headers` so the React web client can send and read them.

### HTTP Status Codes

| Status Code | Reason Phrase         | Use Case in UniHub                                                                                                      |
| :---------- | :-------------------- | :---------------------------------------------------------------------------------------------------------------------- |
| 200         | OK                    | Standard successful `GET` or mutation response.                                                                         |
| 201         | Created               | Resource created (workshop, registration, payment intent).                                                              |
| 202         | Accepted              | Async request accepted (AI summarization, CSV ingestion) returning a Job ID.                                            |
| 204         | No Content            | Successful update or deletion with no response body to reduce bandwidth.                                                |
| 207         | Multi-Status          | Mobile offline sync bulk upsert with per-item outcomes.                                                                 |
| 400         | Bad Request           | Malformed JSON syntax, unparseable request body, corrupted headers, protocol-level errors.                              |
| 401         | Unauthorized          | Missing or invalid JWT.                                                                                                 |
| 403         | Forbidden             | RBAC or tenant boundary violation.                                                                                      |
| 404         | Not Found             | Resource ID does not exist or is not visible to the requester.                                                          |
| 409         | Conflict              | Optimistic locking failure or seat-capacity conflict.                                                                   |
| 422         | Unprocessable Entity  | Validation failure (`@Valid`, missing/invalid fields, constraint violations), state machine or business rule violation. |
| 429         | Too Many Requests     | Bucket4j rate limit exceeded.                                                                                           |
| 500         | Internal Server Error | Unhandled or unexpected backend failures.                                                                               |

### Mandatory Headers

- `Authorization: Bearer <token>`
- `X-Correlation-ID` (client-generated for cross-boundary tracing)
- `Accept-Language` (localized RFC 7807 error messages)
- `Idempotency-Key` (required on business-critical `POST`/`PUT`/`PATCH` endpoints such as payments, registrations, and offline-sync; UUIDv4 string, max 64 chars)

### Content Types

- Success responses must use `Content-Type: application/json`.
- Error responses must use `Content-Type: application/problem+json` (RFC 7807).
- Upload endpoints must use `Content-Type: multipart/form-data` (PDF brochures, CSV rosters).
- Real-time streaming endpoints must use `Content-Type: text/event-stream; charset=UTF-8` (Server-Sent Events).

### Date/Time & Time Zone

All timestamps must be ISO 8601 in UTC with a trailing `Z` (e.g., `2026-05-03T12:00:00Z`).

### Standard Response Headers

- `Strict-Transport-Security` for HTTPS enforcement.
- `X-Content-Type-Options: nosniff` to prevent MIME sniffing.
- `X-Frame-Options: DENY` to strictly prevent clickjacking.
- `Referrer-Policy: no-referrer` for privacy-safe defaults.
- `Content-Security-Policy: default-src 'none'; frame-ancestors 'none';` as a strict API baseline.
- `X-RateLimit-Limit`, `X-RateLimit-Remaining`, and `X-RateLimit-Retry-After` are returned by the server on Bucket4j-protected endpoints.

## Standard JSON Payloads

### Success Response Envelope (2xx)

The JSON envelope is used only for successful `2xx` responses. The backend must drop all `null` values using `@JsonInclude(JsonInclude.Include.NON_NULL)` to minimize payload size.

```typescript
{
  "success": boolean,
  "message": string,
  "data": object | object[],
  "metadata": {
    "processedBy": string,
    "traceId": string
  }
}
```

- `success`: Boolean flag indicating overall request success.
- `message`: Human-readable summary intended for logs and UI messages.
- `data`: Domain payload (single object, list, or pagination container).
- `metadata`: Transport/system metadata for observability and routing.
  - `metadata.processedBy`: Hostname or ID of the specific Spring Boot app server node (e.g., `unihub-app-1`) that handled the request behind the Nginx round-robin load balancer.
  - `metadata.traceId`: Correlation token for end-to-end tracing and client error reporting.

**Success Response (Single Resource)**

```json
{
  "success": true,
  "message": "Workshop retrieved",
  "data": {
    "id": "8e4b9b3b-9c1a-4a8d-9f6b-3c8b7f9f2c10",
    "title": "Career Sprint 101"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Success Response (List / Pagination)**

```json
{
  "success": true,
  "message": "Workshops page",
  "data": {
    "items": [
      {
        "id": "b2a3f0b8-22f9-4b6a-9a54-1a8d8d1f20f4",
        "title": "Design Systems"
      }
    ],
    "cursor": {
      "nextCursor": "eyJpZCI6ImIyYTNmMGI4LTIyZjktNGI2YS05YTU0LTFhOGQ4ZDFmMjBmNCJ9",
      "hasMore": false
    }
  },
  "metadata": {
    "processedBy": "unihub-app-2",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Success Response (207 Multi-Status)**

```json
{
  "success": true,
  "message": "Attendance sync processed",
  "data": [
    {
      "syncId": "7f8b0a5c-1aa0-44c6-9b6c-0a19b6c4b1f1",
      "status": "SUCCESS"
    },
    {
      "syncId": "3c3d7b2f-8d6d-4f52-bfd9-3f2a7b4b1e20",
      "status": "ALREADY_USED"
    }
  ],
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

### Error Response (4xx, 5xx)

Error responses are wrapped in the standard envelope. The `success` flag is set to `false`, the `data` field is absent, and the `error` field contains the RFC 7807 Problem Details. The `traceId` is present in `metadata`.

**Standard Error Types (`urn:unihub:error:*`):**

All RFC 7807 error responses use a unified URI identifier in the `error.type` field. Standard common error types are defined as follows:

| HTTP Status                 | Standard `type`                        | Default Title         | Description                                                                                                       | RFC 7807 Payload Details                        |
| :-------------------------- | :------------------------------------- | :-------------------- | :---------------------------------------------------------------------------------------------------------------- | :---------------------------------------------- |
| `400 Bad Request`           | `urn:unihub:error:bad-request`         | Bad Request           | Malformed JSON syntax, unparseable request body, or invalid raw query/path parameter formats.                     | Standard ProblemDetail                          |
| `401 Unauthorized`          | `urn:unihub:error:unauthorized`        | Unauthorized          | Missing Bearer token, invalid signature, expired token, revoked token (JTI blacklist), or invalid credentials.    | Standard ProblemDetail                          |
| `403 Forbidden`             | `urn:unihub:error:forbidden`           | Forbidden             | Insufficient permissions (RBAC) or organization boundary violation (`organization_id`).                           | Standard ProblemDetail                          |
| `404 Not Found`             | `urn:unihub:error:not-found`           | Not Found             | Target resource or endpoint does not exist.                                                                       | Standard ProblemDetail                          |
| `405 Method Not Allowed`    | `urn:unihub:error:method-not-allowed`  | Method Not Allowed    | HTTP method not supported for the requested resource.                                                             | Standard ProblemDetail                          |
| `409 Conflict`              | `urn:unihub:error:conflict`            | Conflict              | Resource conflict (unique constraint violation, already active account, assignment overlap, capacity exhaustion). | Standard ProblemDetail                          |
| `422 Unprocessable Content` | `urn:unihub:error:validation`          | Unprocessable Content | Business logic / input validation failure (`@Valid`, business rules).                                             | Includes `validationErrors: ["field: message"]` |
| `429 Too Many Requests`     | `urn:unihub:error:rate-limited`        | Too Many Requests     | Token bucket rate limit exceeded.                                                                                 | Includes `Retry-After` header                   |
| `500 Internal Server Error` | `urn:unihub:error:internal`            | Internal Server Error | Unexpected server error.                                                                                          | Technical stack trace suppressed                |
| `503 Service Unavailable`   | `urn:unihub:error:service-unavailable` | Service Unavailable   | Downstream or mandatory infrastructure (Redis, RabbitMQ, Gateway) unavailable, or Circuit Breaker is OPEN.        | Includes `Retry-After` header                   |

> [!NOTE]
> Domain-specific business exceptions (e.g. `urn:unihub:error:temporary-password`, `urn:unihub:error:account-disabled`) inherit this identical envelope structure and RFC 7807 schema, allowing client applications to branch UI logic predictably based on `error.type`.

**Error Response (422 Unprocessable Content)**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:validation",
    "title": "Unprocessable Content",
    "status": 422,
    "detail": "Workshop cannot be published from DRAFT when required fields are missing.",
    "instance": "/api/v1/workshops/8e4b9b3b-9c1a-4a8d-9f6b-3c8b7f9f2c10/publish",
    "validationErrors": [
      "coverImageUrl: is required",
      "startsAt: must be in the future"
    ]
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Error Response (404 Not Found)**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:not-found",
    "title": "Not Found",
    "status": 404,
    "detail": "Workshop not found.",
    "instance": "/api/v1/workshops/00000000-0000-0000-0000-000000000000"
  },
  "metadata": {
    "processedBy": "unihub-app-2",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

## Server-Sent Events (SSE) Streaming Standards

This section defines the real-time push contract between the backend and client applications (Web and Mobile) using Server-Sent Events.

### Stream Connection Endpoint

Authenticated clients establish a single, multiplexed connection to receive real-time updates:

- **Endpoint:** `GET /api/v1/stream/events`
- **Protocol:** HTTP/1.1 or HTTP/2 Streaming
- **Response Content-Type:** `text/event-stream; charset=UTF-8`
- **Response Headers:**
  - `Cache-Control: no-cache, no-transform`
  - `Connection: keep-alive`
  - `X-Accel-Buffering: no` (advisory to Nginx to prevent buffering)

### Handshake & Authentication

- **Primary Authentication:** The connection must be authenticated via the standard HTTP header `Authorization: Bearer <jwt>`.
- **Client Implementation:** Web clients use `@microsoft/fetch-event-source` rather than the browser's native `EventSource`, allowing standard header-based JWT authentication and fine-grained error lifecycle management. Mobile clients (React Native) use `react-native-sse` to support live attendance monitoring (F-6.3) when connectivity is active.
- **Handshake Status Codes:**
  - `200 OK`: Stream opened successfully.
  - `401 Unauthorized`: Missing, expired, or invalid JWT (standard RFC 7807 problem detail returned before stream begins).
  - `403 Forbidden`: User account banned or inactive.
  - `429 Too Many Requests`: Distributed rate limit exceeded on initial handshake request.

### Wire Envelope Format

Every event emitted over the stream conforms to standard SSE formatting:

```text
id: <uuid>
event: <event_name>
data: {"success": true, "data": { ... }, "timestamp": "2026-09-09T08:30:00Z"}
retry: 3000
<blank line>
```

- `id`: Unique event ID (UUIDv4) allowing client auto-reconnection via `Last-Event-ID`.
- `event`: Name of the domain event defining the schema of `data`.
- `data`: Serialized JSON payload containing the event data and timestamp.
- `retry`: Recommended reconnection interval in milliseconds (default: 3000ms).
- `<blank line>`: Mandatory terminating empty line (`\n\n`) per the W3C SSE specification. Client parsers only commit and dispatch the event buffer upon receiving this blank line.

### Heartbeat (Keep-Alive)

To prevent intermediary proxies (Nginx, cloud load balancers, corporate firewalls) from closing idle TCP sockets, the server emits a heartbeat comment every 15 seconds:

```text
: ping
<blank line>
```

- Lines beginning with a colon (`:`) are comments in the SSE specification and are ignored by client event parsers.
- The terminating `<blank line>` (`\n\n`) flushes the comment buffer across network buffers and intermediary proxies.

### Unified Event Catalog

All real-time events are multiplexed over this single user connection. The client dispatches handling based on the `event` field:

| Event Name             | Module Owner    | Trigger Condition                                                              | Payload `data` Contract                                                                                                                 |
| :--------------------- | :-------------- | :----------------------------------------------------------------------------- | :-------------------------------------------------------------------------------------------------------------------------------------- |
| `notification.new`     | `notifications` | A new in-app notification is persisted                                         | `{ id: string, title: string, type: string, linkUrl?: string, createdAt: string }`                                                      |
| `notification.read`    | `notifications` | Notification(s) marked as read via PATCH API (multi-device badge sync)         | `{ id?: string, all: boolean, unreadCount: number, readAt: string }`                                                                    |
| `ai.summary.completed` | `workshops`     | AI brochure summary background job completed                                   | `{ jobId: string, workshopId: string, resultText: string }`                                                                             |
| `ai.summary.failed`    | `workshops`     | AI brochure summary background job failed                                      | `{ jobId: string, workshopId: string, errorMessage: string }`                                                                           |
| `attendance.updated`   | `attendances`   | Staff completed check-in sync batch (streamed to Organizer and assigned Staff) | `{ workshopId: string, checkedInCount: number, capacity: number, lastSyncedAt: string }`                                                |
| `registration.updated` | `registrations` | A registration is confirmed or canceled (streamed to Organizer dashboard)      | `{ workshopId: string, confirmedCount: number, pendingCount: number, capacity: number, availableSeats: number, lastUpdatedAt: string }` |
| `payment.confirmed`    | `payments`      | Stripe payment confirmed for registered student                                | `{ registrationId: string, workshopId: string, qrPayload: string, qrIssuedAt: string }`                                                 |

## API Logging

Logging is configured in `logback-spring.xml` with a dual-profile strategy to balance developer readability with production-grade observability.

### Local/Dev Profile (Readable Text)

Local and dev environments use a fixed-width, terminal-friendly format optimized for multiplexers and quick scanning.

**Format:** `[LEVEL] traceId METHOD URI -> STATUS (DURATIONms) -> PROCESSED_BY`

```text
[INFO] 7f6f7c9b2d0b4d8c GET /api/v1/workshops -> 200 (12ms) -> unihub-app-1
[INFO] 7f6f7c9b2d0b4d8c POST /api/v1/registrations -> 201 (28ms) -> unihub-app-1
[WARN] 7f6f7c9b2d0b4d8c PATCH /api/v1/workshops/8e4b9b3b -> 404 (7ms) -> unihub-app-2
```

### Production Profile (Structured JSON)

Production uses structured JSON logging compatible with `logstash-logback-encoder` for ELK/Prometheus ingestion. The `traceId` is at the root, while request-specific attributes are emitted under `mdc`.

| Root Field    | Description                              | Example                               |
| :------------ | :--------------------------------------- | :------------------------------------ |
| `@timestamp`  | ISO 8601 UTC timestamp of the log event. | `2026-05-03T12:00:00Z`                |
| `level`       | Log level.                               | `INFO`                                |
| `logger_name` | Logger name.                             | `com.unihub.api.RequestLoggingFilter` |
| `message`     | Fixed log message.                       | `API Request Completed`               |
| `traceId`     | Server-generated trace identifier.       | `7f6f7c9b2d0b4d8c`                    |
| `stack_trace` | Stack trace if present, otherwise null.  | `null`                                |

| MDC Field       | Description                                            | Example                 |
| :-------------- | :----------------------------------------------------- | :---------------------- |
| `correlationId` | Client-provided `X-Correlation-ID` value.              | `req-2d8b9c1a`          |
| `http.method`   | HTTP method.                                           | `POST`                  |
| `http.uri`      | Request path.                                          | `/api/v1/auth/register` |
| `http.status`   | HTTP status code.                                      | `201`                   |
| `duration_ms`   | Request duration in milliseconds.                      | `18`                    |
| `processedBy`   | Instance ID/hostname of the handling Spring Boot node. | `unihub-app-1`          |
| `clientIp`      | Client IP address observed by the gateway/application. | `192.168.1.4`           |
| `userAgent`     | User agent string from the client request.             | `Mozilla/5.0`           |

```json
{
  "@timestamp": "2026-05-03T12:00:00Z",
  "level": "INFO",
  "logger_name": "com.unihub.api.RequestLoggingFilter",
  "message": "API Request Completed",
  "traceId": "7f6f7c9b2d0b4d8c",
  "stack_trace": null,
  "mdc": {
    "correlationId": "req-2d8b9c1a",
    "http.method": "POST",
    "http.uri": "/api/v1/auth/register",
    "http.status": "201",
    "duration_ms": "18",
    "processedBy": "unihub-app-1",
    "clientIp": "192.168.1.4",
    "userAgent": "Mozilla/5.0"
  }
}
```
