# API Contract: Resource Reservation With Capacity Limit

> **Status:** APPROVED  
> **Spec:** specs/resource-reservation-with-capacity-limit/SPEC.md  
> **Last revised:** N/A — first version

> [!NOTE]
> This is an illustrative, domain-neutral example showing how to apply this skill. It is the contract for the same generic "reserve a unit of limited capacity" feature used as the worked example in the `spec-driven-development` skill, so the two examples can be read side by side. It does not belong to any specific project, framework, or protocol — substitute your own project's actual conventions for the illustrative ones used here (URI prefix, envelope shape, error format).

## 1. Overview

This contract covers the reservations resource: creating a reservation against a capacity-limited resource, and canceling an existing reservation. It implements the Functional Requirements defined in `specs/resource-reservation-with-capacity-limit/SPEC.md`.

## 2. Global Standards Applied

- **URI structure / versioning:** `/api/v1/...`
- **Pagination convention:** not applicable — no collection endpoint in this contract
- **Success response shape:** `{ "success": true, "data": {...}, "meta": {...} }`
- **Error response format:** RFC 7807 Problem Details (`application/problem+json`)
- **Idempotency-key convention:** client-supplied `Idempotency-Key` header, UUID format
- **Auth header convention:** `Authorization: Bearer <token>`
- **Date/time formatting convention:** ISO 8601 UTC with trailing `Z`

## 3. Endpoints

### POST /api/v1/resources/{resourceId}/reservations

**Description:** Creates a reservation for the authenticated user against the specified resource, if capacity is available.  
**Required Role / Auth:** AUTHENTICATED (any authenticated end user)  
**Idempotency Requirement:** Required — this is a mutating endpoint with genuine retry risk (client network timeout could cause a legitimate retry); without an idempotency key, a retry could be misclassified as a duplicate-reservation attempt or, worse, could double-decrement capacity.

#### Request

**Headers:**

| Header          | Required | Description                                                          |
| :-------------- | :------- | :------------------------------------------------------------------- |
| Authorization   | Yes      | Bearer token identifying the requesting user                         |
| Idempotency-Key | Yes      | Client-generated UUID; same key on retry returns the original result |

**Path Parameters:**

| Parameter  | Type          | Description                               |
| :--------- | :------------ | :---------------------------------------- |
| resourceId | string (UUID) | Identifier of the resource being reserved |

**Query Parameters:**

None.

**Request Body:**

```json
{}
```

> No body fields are required beyond the path parameter and the idempotency header for this minimal example. (A real contract may add fields here, e.g. a requested quantity, with their own validation rules.)

**Validation Rules:**

- `resourceId` (path): must be a syntactically valid UUID; existence is checked server-side and reported via 404 if not found.

#### Response

**Success — 201 Created:**

```json
{
  "success": true,
  "data": {
    "reservationId": "string (UUID)",
    "resourceId": "string (UUID)",
    "status": "ACTIVE",
    "createdAt": "string (ISO 8601 UTC)"
  },
  "meta": {
    "traceId": "string"
  }
}
```

**Error Responses:**

| Status | Error Identifier        | Condition                                                                 |
| :----- | :---------------------- | :------------------------------------------------------------------------ |
| 401    | unauthorized            | Missing or invalid auth token                                             |
| 404    | resource-not-found      | `resourceId` does not refer to an existing resource                       |
| 409    | duplicate-reservation   | The requesting user already holds an active reservation for this resource |
| 409    | capacity-exhausted      | The resource's remaining capacity is zero                                 |
| 422    | missing-idempotency-key | `Idempotency-Key` header was not supplied                                 |

#### Side Effects

- Decrements the resource's remaining capacity by exactly one upon successful creation (FR-04).
- Publishes a `reservation-created` domain event consumed by downstream notification handling (if such a consumer exists in this project — see the feature's `EVENTS.md` / event-design artifact if one was produced separately).

#### Spec Traceability

- **Satisfies:** FR-01, FR-02, FR-03, FR-04, FR-05, Scenario 1, Scenario 2, Scenario 3, Scenario 4, Scenario 5

### DELETE /api/v1/reservations/{reservationId}

**Description:** Cancels an existing active reservation. Only the reservation's owner may cancel it.  
**Required Role / Auth:** AUTHENTICATED, and must be the reservation's owner (enforced server-side, not assumed from client-side gating)  
**Idempotency Requirement:** Not Required — a DELETE that targets an already-canceled reservation is handled as a defined error case (409) rather than needing replay protection; canceling is not a financial or capacity-decrementing operation that risks double effect on retry in the same way creation does.

#### Request

**Headers:**

| Header        | Required | Description                                  |
| :------------ | :------- | :------------------------------------------- |
| Authorization | Yes      | Bearer token identifying the requesting user |

**Path Parameters:**

| Parameter     | Type          | Description                             |
| :------------ | :------------ | :-------------------------------------- |
| reservationId | string (UUID) | Identifier of the reservation to cancel |

**Query Parameters:** None.

**Request Body:** None.

#### Response

**Success — 204 No Content**

No response body.

**Error Responses:**

| Status | Error Identifier      | Condition                                                 |
| :----- | :-------------------- | :-------------------------------------------------------- |
| 401    | unauthorized          | Missing or invalid auth token                             |
| 403    | forbidden             | The requesting user is not the reservation's owner        |
| 404    | reservation-not-found | `reservationId` does not refer to an existing reservation |
| 409    | already-canceled      | The reservation is already in `CANCELED` status           |

#### Side Effects

- Increments the resource's remaining capacity by exactly one upon successful cancellation (FR-06).

#### Spec Traceability

- **Satisfies:** FR-06, FR-07, Scenario 6

## 4. Rate Limiting Behavior

Not applicable to this illustrative contract. A real contract for a high-contention resource (e.g. limited-availability tickets) would specify the rate-limiting mechanism here, scoped per user and/or per resource.

## 5. Idempotency Behavior

For `POST /api/v1/resources/{resourceId}/reservations`: the server stores the supplied `Idempotency-Key` alongside the request's outcome. A retry with the same key and the same authenticated user returns the original `201` response (or the original error response, if the first attempt failed) without re-evaluating capacity or creating a second record. A different idempotency key is treated as a genuinely new request, subject to the duplicate-reservation check in FR-03 on its own merits.

## 6. Cross-Endpoint Consistency Notes

- `reservationId` and `resourceId` are spelled and typed identically (string UUID) in both endpoints above.
- The success envelope (`success` / `data` / `meta`) is reused unchanged from the project's existing convention — no endpoint-specific variation was introduced.
