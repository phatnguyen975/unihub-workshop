# UniHub Workshop - API Standards

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

| Status Code | Reason Phrase         | Use Case in UniHub                                                                |
| :---------- | :-------------------- | :-------------------------------------------------------------------------------- |
| 200         | OK                    | Standard successful `GET` or mutation response.                                   |
| 201         | Created               | Resource created (workshop, registration, payment intent).                        |
| 202         | Accepted              | Async request accepted (AI summarization, CSV ingestion) returning a Job ID.      |
| 204         | No Content            | Successful update or deletion with no response body to reduce bandwidth.          |
| 207         | Multi-Status          | Mobile offline sync bulk upsert with per-item outcomes.                           |
| 400         | Bad Request           | Invalid payload, missing required fields, malformed JSON.                         |
| 401         | Unauthorized          | Missing or invalid JWT.                                                           |
| 403         | Forbidden             | RBAC or tenant boundary violation.                                                |
| 404         | Not Found             | Resource ID does not exist or is not visible to the requester.                    |
| 409         | Conflict              | Optimistic locking failure or seat-capacity conflict.                             |
| 422         | Unprocessable Entity  | State machine or business rule violation (publish/cancel/registration conflicts). |
| 429         | Too Many Requests     | Bucket4j rate limit exceeded.                                                     |
| 500         | Internal Server Error | Unhandled or unexpected backend failures.                                         |

### Mandatory Headers

- `Authorization: Bearer <token>`
- `X-Correlation-ID` (client-generated for cross-boundary tracing)
- `Accept-Language` (localized RFC 7807 error messages)
- `Idempotency-Key` (required on business-critical `POST`/`PUT`/`PATCH` endpoints such as payments, registrations, and offline-sync; UUIDv4 string, max 64 chars)

### Content Types

- Success responses must use `Content-Type: application/json`.
- Error responses must use `Content-Type: application/problem+json` (RFC 7807).
- Upload endpoints must use `Content-Type: multipart/form-data` (PDF brochures, CSV rosters).

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

### Error Response - RFC 7807 (4xx, 5xx)

Error responses are not wrapped in the custom envelope. They must use RFC 7807 Problem Details at the root with `Content-Type: application/problem+json`.

**Error Response (422 Unprocessable Entity)**

```json
{
  "type": "urn:unihub:error:state",
  "title": "Unprocessable Entity",
  "status": 422,
  "detail": "Workshop cannot be published from DRAFT when required fields are missing.",
  "instance": "/api/v1/workshops/8e4b9b3b-9c1a-4a8d-9f6b-3c8b7f9f2c10/publish",
  "traceId": "7f6f7c9b2d0b4d8c",
  "validationErrors": [
    "coverImageUrl: is required",
    "startsAt: must be in the future"
  ]
}
```

**Error Response (404 Not Found)**

```json
{
  "type": "urn:unihub:error:not-found",
  "title": "Not Found",
  "status": 404,
  "detail": "Workshop not found.",
  "instance": "/api/v1/workshops/00000000-0000-0000-0000-000000000000",
  "traceId": "7f6f7c9b2d0b4d8c"
}
```

## API Logging

Logging is configured in `logback-spring.xml` with a dual-profile strategy to balance developer readability with production-grade observability.

### Local/Dev Profile (Readable Text)

Local and dev environments use a fixed-width, terminal-friendly format optimized for multiplexers and quick scanning.

**Format:** `[LEVEL] [traceId] [METHOD] [URI] -> [STATUS] ([DURATION]ms) -> [PROCESSED_BY]`

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
