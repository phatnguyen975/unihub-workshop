# API Contract: Live Attendance Monitoring

> **Status:** APPROVED  
> **Spec:** `specs/server/live-attendance-monitoring/SPEC.md`  
> **Created:** 2026-11-06

## 1. Overview

This contract derives from `SPEC.md` and defines the single summary endpoint owned by backend `attendances` for F-6.3. It also records how consumers combine that baseline with the existing four-field `attendance.updated` event. The authenticated SSE connection and attendance-sync mutation retain their existing contracts; this feature adds neither endpoint nor transport for them.

## 2. Global Standards Applied

- **Versioning and resource naming:** `/api/v1/` and plural workshop/attendance resources, as specified in `docs/server/feature-delivery.md` §6.3.
- **Success representation:** `application/json`, standard `{ success, message, data, metadata }` envelope. `metadata` contains `processedBy` and `traceId`. Null optional values are omitted; all summary data fields below are required.
- **Error representation:** `application/problem+json`, standard `{ success: false, error: { type, title, status, detail, instance }, metadata }` envelope using the common `urn:unihub:error:*` types. No top-level `data` is present on failure.
- **Authentication:** RS256 Bearer JWT with revocation checks. Caller identity, role, and organizer organization context come from the authenticated principal, never body/query parameters.
- **Idempotency:** No `Idempotency-Key` is required for the read-only summary. It does not execute the mutation deduplication workflow.
- **Pagination:** This is a single aggregate resource with a fixed preview of at most 10 scans. No collection pagination envelope, cursor, page size, sort control, or offset is defined.
- **Primitives:** UUID identifiers; nonnegative integer counts/capacity; percentage as a JSON number calculated with two-decimal `HALF_UP` rounding. Timestamps are ISO-8601 UTC strings ending in `Z`, with fractional-second precision permitted.
- **Security and tracing:** Apply standard security response headers and CORS policy. Client correlation and server-generated trace identifiers are distinct; `metadata.traceId` is not promised to equal `X-Correlation-ID`.
- **Routing:** Primary authorizes and supplies workshop metadata. After that phase completes, Replica supplies registration/attendance reporting. This does not promise one atomic snapshot across phases or statements.

## 3. Endpoints

### GET /api/v1/workshops/{workshopId}/attendances/summary

**Description:** Returns the requested workshop's replica-visible attendance baseline and recent successful scans after current resource authorization.  
**Required Role / Auth:** Authenticated `ORGANIZER` whose `jwt.org` matches the workshop's `organizationId`, or authenticated `STAFF` with a Primary assignment for `(workshopId, jwt.sub)`. Organization membership alone never authorizes staff.  
**Idempotency Requirement:** Not required — read-only; a later read may observe newer data.

#### Request

**Headers:**

| Header             | Required                            | Contract                                                                                                                                                          |
| :----------------- | :---------------------------------- | :---------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `Authorization`    | Yes                                 | `Bearer <JWT>`; signature, expiry, and revocation verification apply.                                                                                             |
| `X-Correlation-ID` | Client-required by global standards | Opaque client correlation identifier for tracing. No feature-specific format or missing-header rejection is added; shared tracing supplies the response metadata. |
| `Accept-Language`  | Client-required by global standards | Preferred language for localized errors. No feature-specific validation or missing-header rejection is added.                                                     |

`Content-Type` is not required because there is no request body. Clients may request `application/json`; the successful representation is JSON. No idempotency or streaming-resume header is required for this endpoint.

**Path parameters:**

| Parameter    | Type        | Validation                                                                                                                                                    |
| :----------- | :---------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `workshopId` | UUID string | Required path segment. An unparseable UUID is `400`; a parseable identifier with no Primary workshop is `404`. No new UUID-version restriction is introduced. |

**Query parameters:** None defined. The endpoint supports no pagination, filtering, sorting, caller identity, organization override, or time-range parameter. Clients send no query string and must not rely on undocumented query behavior.

**Request body:** None.

**Authorization and validation:**

1. Shared authentication and admission controls apply; they may terminate a request before business processing.
2. The caller must hold `ORGANIZER` or `STAFF`. An organizer must have a parseable organization context; missing/invalid context is `403`.
3. The path identifier must be parseable before workshop lookup; otherwise return `400`.
4. Resolve workshop existence and metadata through `workshops::api` on Primary. A missing workshop is `404`.
5. For an existing workshop, enforce organizer ownership or the staff assignment on Primary. Failure is `403`; no Replica business-reporting read is then performed.
6. End that Primary phase before Replica reporting through the owning modules. The authorized Primary metadata supplies `capacity`; only counts and the preview are replica-sourced.

Authentication, transport binding, and quota filters retain their platform ordering; this contract does not promise a single error precedence when several independent preconditions fail at once. Within resource access, existence precedes ownership/assignment as above. Authorization is evaluated at the Primary access check, not locked until the response finishes.

**Lifecycle eligibility:** All four states are accepted: `DRAFT`, `PUBLISHED`, `COMPLETED`, `CANCELED`. There is no start/end-time gate and no state-based `409` or `422` for this read. This permission does not change sync eligibility.

#### Response

**Success — `200 OK`:**

```json
{
  "success": true,
  "message": "Attendance summary retrieved",
  "data": {
    "workshopId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
    "capacity": 100,
    "confirmedCount": 32,
    "checkedInCount": 2,
    "checkInPercentage": 6.25,
    "recentScans": [
      {
        "id": "c342b3a0-3510-4b19-9671-fdff8b653193",
        "registrationId": "5adb6f71-8e23-4e63-8c46-c836f6c64881",
        "checkedInAt": "2026-11-06T09:04:02Z",
        "createdAt": "2026-11-06T09:08:00Z"
      },
      {
        "id": "56f3c9d9-49f1-42c8-9aaa-13a6a7e0e8ee",
        "registrationId": "7b86ced9-d9c6-4efc-a629-9583b439483d",
        "checkedInAt": "2026-11-06T09:03:11Z",
        "createdAt": "2026-11-06T09:09:00Z"
      }
    ]
  },
  "metadata": {
    "processedBy": "unihub-backend-1",
    "traceId": "4d36a8a9-c165-455c-8cbc-2c3018f14dfc"
  }
}
```

**Required `data` fields:**

| Field               | JSON type         | Meaning                                                                                                                                                     |
| :------------------ | :---------------- | :---------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `workshopId`        | UUID string       | Requested, authorized workshop identifier.                                                                                                                  |
| `capacity`          | Integer ≥ 0       | Configured capacity from Primary workshop context; `0` when draft capacity is absent.                                                                       |
| `confirmedCount`    | Integer ≥ 0       | Replica count of this workshop's registrations currently in `CONFIRMED`. Excludes `PENDING`, `FAILED`, and `CANCELED`.                                      |
| `checkedInCount`    | Integer ≥ 0       | Replica count of persisted attendance in `VALID` for this workshop. Excludes `INVALIDATED`; does not require the linked registration to remain `CONFIRMED`. |
| `checkInPercentage` | Number ≥ 0        | `checkedInCount * 100 / confirmedCount`, rounded to two decimal places using `HALF_UP`; `0.00` for zero denominator. No upper cap.                          |
| `recentScans`       | Array, 0–10 items | Newest `VALID` attendance records visible to the preview read. Return all when fewer than 10 exist, otherwise the newest 10.                                |

**Required recent-scan item fields:**

| Field            | JSON type           | Meaning                                                                                                  |
| :--------------- | :------------------ | :------------------------------------------------------------------------------------------------------- |
| `id`             | UUID string         | Attendance record identifier, deliberately exposed as the stable item identity and ordering tie-breaker. |
| `registrationId` | UUID string         | Registration referenced by this attendance; not the student's user identifier.                           |
| `checkedInAt`    | ISO-8601 UTC string | Stored check-in time; offline scans preserve the clamped device scan time.                               |
| `createdAt`      | ISO-8601 UTC string | Server receipt/creation time of the attendance record.                                                   |

Order is fixed: `checkedInAt DESC, id DESC`. Equal timestamps are resolved by UUID identifier descending using the database's identifier order, not arrival order. The preview is not sorted by `createdAt`; late offline arrivals may remain outside the newest ten.

No pagination container, `hasMore`, `nextCursor`, summary `lastSyncedAt`, snapshot version, student profile, checking-in staff identifier, device information, or QR/ticket content is returned. Percentage is numeric; consumers must not depend on trailing zero characters in its JSON spelling or property ordering.

**Boundary values:**

| Confirmed | Checked in | Percentage | Required interpretation                                     |
| :-------- | :--------- | :--------- | :---------------------------------------------------------- |
| 32        | 1          | 3.13       | Half-up rounding, not truncation.                           |
| 3         | 2          | 66.67      | Capacity does not determine this percentage.                |
| 2         | 3          | 150.00     | Preserve the observed mismatch and both counts.             |
| 0         | 1          | 0.00       | Zero-denominator rule still applies when attendance exists. |
| 0         | 0          | 0.00       | Empty reporting dataset, with `recentScans: []`.            |

A workshop known on Primary with no replica-visible registrations/attendance is a successful empty baseline, not `404`. Missing draft capacity is `0`, not an omitted/null field. A required read failure is not an empty dataset.

**Consistency:** Capacity and permission use Primary; counts and the preview use Replica. Separate reads may observe concurrent changes, so preview length need not match a separately sampled checked-in count. The percentage always derives from the counts in that response. The response does not claim immediate read-after-sync visibility, a cross-datasource snapshot, a publication watermark, or a replay position.

**Error responses:**

| Status                      | `error.type`                           | Condition                                                                                                                                                                                 |
| :-------------------------- | :------------------------------------- | :---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `400 Bad Request`           | `urn:unihub:error:bad-request`         | `workshopId` cannot be parsed as a UUID.                                                                                                                                                  |
| `401 Unauthorized`          | `urn:unihub:error:unauthorized`        | Missing, malformed, expired, signature-invalid, or revoked authentication. Existing shared authentication subtypes, if emitted, retain the same envelope.                                 |
| `403 Forbidden`             | `urn:unihub:error:forbidden`           | Disallowed role; missing/invalid organizer organization context; organizer organization mismatch; or staff lacks a current Primary assignment.                                            |
| `404 Not Found`             | `urn:unihub:error:not-found`           | No workshop exists on Primary after applicable identity/role checks.                                                                                                                      |
| `405 Method Not Allowed`    | `urn:unihub:error:method-not-allowed`  | A method unsupported by this resource is used; platform method handling supplies `Allow`.                                                                                                 |
| `429 Too Many Requests`     | `urn:unihub:error:rate-limited`        | The user's shared default-tier quota is exhausted; includes retry information.                                                                                                            |
| `500 Internal Server Error` | `urn:unihub:error:internal`            | Unexpected programming, mapping, or processing failure not classified as an unavailable required datasource/security dependency. No technical stack trace or internal message is exposed. |
| `503 Service Unavailable`   | `urn:unihub:error:service-unavailable` | Primary authorization/metadata reads or required Replica reporting reads are unavailable, or revocation verification cannot be completed. Includes `Retry-After`.                         |

The `GET` introduces no business-state conflict, mutation idempotency conflict, input body validation, or partial multi-status response. Platform protocol/security responses retain the global API conventions rather than introducing feature-specific error schemas.

**Example — `403 Forbidden`:**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:forbidden",
    "title": "Forbidden",
    "status": 403,
    "detail": "You do not have permission to view attendance for this workshop.",
    "instance": "/api/v1/workshops/9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d/attendances/summary"
  },
  "metadata": {
    "processedBy": "unihub-backend-1",
    "traceId": "4d36a8a9-c165-455c-8cbc-2c3018f14dfc"
  }
}
```

**Example — `503 Service Unavailable`:**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:service-unavailable",
    "title": "Service Unavailable",
    "status": 503,
    "detail": "Attendance summary is temporarily unavailable. Please retry.",
    "instance": "/api/v1/workshops/9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d/attendances/summary"
  },
  "metadata": {
    "processedBy": "unihub-backend-1",
    "traceId": "4d36a8a9-c165-455c-8cbc-2c3018f14dfc"
  }
}
```

`Retry-After` is a positive delay in seconds from the applicable shared failure policy. The contract does not fix a new delay or promise readiness when that delay expires. Replica failures never cause an automatic Primary reporting fallback or a successful partial summary. Revocation unavailability denies the request even if rate-limit evaluation would fail open.

#### Side Effects

None on business data. No attendance/registration/assignment/workshop writes, idempotency claims, domain events, attendance-update publications, jobs, or live subscriptions are created by reading the summary. Standard request logs, metrics, and admission accounting still apply.

#### Spec Traceability

| Contract concern                                   | Functional requirements | Acceptance scenarios |
| :------------------------------------------------- | :---------------------- | :------------------- |
| Identity, role, resource permission, lifecycle     | FR-01–FR-10             | 2–7                  |
| Summary values, scope, empty results, percentage   | FR-11–FR-18             | 1, 8–10, 13          |
| Preview bound, order, fields, timestamps, retries  | FR-19–FR-23             | 1, 10–12             |
| Routing, failures, admission                       | FR-24–FR-28             | 13–15                |
| Read-only baseline, recovery, timestamp exclusions | FR-29–FR-31             | 7, 16                |

## 4. Rate Limiting Behavior

- The summary consumes the **default tier: 100 tokens per 60 seconds per `jwt.sub`**, shared with the user's other default-tier requests, rather than 100 requests reserved for this endpoint.
- Apply the existing `X-RateLimit-Limit`, `X-RateLimit-Remaining`, and `X-RateLimit-Retry-After` response conventions when quota evaluation is available. A `429` includes `Retry-After`; do not assign a new fixed delay independent of the bucket state.
- Quota-backend unavailability fails open for admission evaluation only. It does not bypass authentication, Primary permissions, or required reads. JTI revocation verification remains fail-closed.
- Existing SSE handshakes retain their dedicated stream tier, 10 per 60 seconds. Frames on an established stream are not summary requests.

## 5. Idempotency Behavior

No request idempotency key, claim lifecycle, or stored-response replay is used by this endpoint. A retry rechecks authorization and obtains the currently applicable baseline. Identical requests can therefore return different counts without creating business side effects.

The request-level and per-entry deduplication mechanisms of the existing sync endpoint remain unchanged. A `SUCCESS` sync outcome may be a retry of an existing record; it must not be interpreted as a new recent-scan item or an increment to the persisted count.

## 6. Existing Event and Cross-Endpoint Consistency

### Attendance event — unchanged dependency contract

Use the existing authenticated `GET /api/v1/stream/events` connection from the Real-Time Event Streaming Hub. No `workshopId` subscription parameter or new attendance-only stream is introduced. The hub routes workshop events to authorized users; clients select the displayed workshop by `payload.workshopId`.

```text
id: 550e8400-e29b-41d4-a716-446655440005
event: attendance.updated
data: {"eventId":"550e8400-e29b-41d4-a716-446655440005","eventType":"attendance.updated","timestamp":"2026-11-06T09:09:01Z","payload":{"workshopId":"9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d","checkedInCount":2,"capacity":100,"lastSyncedAt":"2026-11-06T09:09:01Z"}}
retry: 3000

```

| Payload field    | Type and meaning                                                                                                                                      |
| :--------------- | :---------------------------------------------------------------------------------------------------------------------------------------------------- |
| `workshopId`     | UUID string identifying the workshop.                                                                                                                 |
| `checkedInCount` | Nonnegative integer count of persisted `VALID` attendance, sampled on Primary after the sync commits.                                                 |
| `capacity`       | Nonnegative integer workshop capacity carried by the sync's workshop context.                                                                         |
| `lastSyncedAt`   | ISO-8601 UTC string recording server event-production time. It is not the device scan time, server receipt time, or a durable snapshot/version token. |

F-6.2 owns production: one event after commit if at least one new attendance was inserted, none for zero-new-row retries, and none for rollback. Publication is best effort; failure does not undo committed check-ins. F-10.5 owns connection/authentication, 15-second heartbeat, framing, and distribution. Neither publisher nor hub is replaced by this summary feature. The event contains no `confirmedCount`, percentage, or recent-scan list. Both envelope `timestamp` and payload `lastSyncedAt` must be UTC strings even when the producer supplies timestamp objects internally.

_Traceability: FR-32–FR-34, Scenario 17; dependency contracts in `specs/attendance-sync-reconciliation/` and `specs/real-time-event-streaming-hub/`._

### Consumer baseline and refresh protocol

1. Use one existing multiplexed stream per client session; fetch the summary when opening the workshop's monitoring view.
2. For a matching `attendance.updated`, keep the event's counter as live state and schedule an automatic summary refresh for fields absent from the event. Coalesce/throttle bursts and allow at most one summary request in flight per workshop view; an event arriving during a request can mark that baseline for a coalesced follow-up.
3. Keep live event state distinct from the Replica baseline. A later-arriving summary may report fewer check-ins than the event; it must not replace the live counter as though its arrival establishes freshness. Preserve the summary counts/percentage as one returned baseline rather than claiming a mixture of differently sourced values is atomic. Event timestamps/IDs do not supply a shared ordering guarantee across concurrent publishers and reads.
4. Reload the summary after reconnect. `Last-Event-ID` is a hint, not a replay request backed by event history. Do not infer that a summary has caught up with Primary just because the read completed successfully.
5. Honor `429`/`503` retry hints. Periodic polling is a fallback when streaming is unavailable; it must not duplicate a healthy stream's event-driven refresh path. No new fixed client refresh interval is specified.

These are consumption obligations, not web/mobile implementation tasks under this backend feature. They trace to SPEC BR-10–BR-11 and FR-30.

### Other established contracts

- Workshop existence, ownership, assignment, and metadata are resolved only through `workshops::api`; confirmed counts are obtained through `registrations::api`. Internal facade signatures are implementation design, not additional network endpoints in this contract.
- The summary's `confirmedCount` has the same population as registration monitoring. Its `checkInPercentage` uses that confirmed count, whereas registration monitoring's `occupancyPercentage` uses capacity; the two names/formulas are deliberately distinct.
- Permission to view canceled or draft workshops does not permit syncing them. Staff provisioning may omit canceled workshops while authorized historical summary access still accepts their identifiers.
- This is an additive new read endpoint. Existing summary-independent stream, registration, and sync request/response shapes are not changed.

## 7. References and Assumptions

Sources: `SPEC.md`; `docs/server/feature-delivery.md` §6.3; `contracts/http/standards.md`; `docs/server/system-design.md` reporting/routing/realtime sections; `docs/server/database-schema.md`; [shared ADR-002](../../../docs/adrs/ADR-002-authorization-model.md), [server ADR-006](../../../docs/server/adrs/ADR-006-caching-and-rate-limiting-storage.md), [server ADR-011](../../../docs/server/adrs/ADR-011-primary-replica-routing-strategy.md), [server ADR-025](../../../docs/server/adrs/ADR-025-real-time-event-streaming-via-server-sent-events-sse-and-redis-pubsub.md); and the existing sync, stream, staff-assignment, and registration-monitoring contracts.

No authorization rule, metric formula, lifecycle restriction, or pagination policy is assumed. The concrete summary success message and illustrative localized error messages are representation choices in this draft; clients branch on status and `error.type`, not message text. This contract follows the `$server-spec` workflow by deriving from the completed companion DRAFT specification; both artifacts await joint human review and neither is self-approved.
