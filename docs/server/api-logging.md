# UniHub Workshop — API Logging

Logging is configured in `logback-spring.xml` with a dual-profile strategy to balance developer readability with production-grade observability.

## Local/Dev Profile (Readable Text)

Local and dev environments use a fixed-width, terminal-friendly format optimized for multiplexers and quick scanning.

**Format:** `[LEVEL] traceId METHOD URI -> STATUS (DURATIONms) -> PROCESSED_BY`

```text
[INFO] 7f6f7c9b2d0b4d8c GET /api/v1/workshops -> 200 (12ms) -> unihub-app-1
[INFO] 7f6f7c9b2d0b4d8c POST /api/v1/registrations -> 201 (28ms) -> unihub-app-1
[WARN] 7f6f7c9b2d0b4d8c PATCH /api/v1/workshops/8e4b9b3b -> 404 (7ms) -> unihub-app-2
```

## Production Profile (Structured JSON)

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
