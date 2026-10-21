# API Contract: Payment History and Status

> **Status:** APPROVED  
> **Spec:** `specs/payment-history-and-status/SPEC.md`  
> **Last Revised:** 2026-10-21

## 1. Overview

This contract exposes read-only payment history, payment detail, and an organization-scoped currency summary for the payments area. It is derived from the approved specification and is the approved network contract for implementation.

## 2. Global Standards Applied

- **URI structure / versioning:** URI path versioning under `/api/v1`; organizer routes use the established `/api/v1/admin/...` namespace.
- **Pagination convention:** Opaque Base64 keyset cursor in `cursor`, optional `size` with default `20` and maximum `100`, and `data.items` plus `data.cursor.nextCursor` and `data.cursor.hasMore`. Payment collections sort by `createdAt,desc` with payment ID as the tie-breaker.
- **Success response shape:** Standard `{ success, message, data, metadata }` JSON envelope; null values are omitted.
- **Error response format:** Standard `{ success: false, error: RFC 7807 Problem Details, metadata }` envelope with `application/problem+json`.
- **Idempotency-key convention:** Not required for any endpoint because all endpoints are read-only and side-effect free.
- **Auth header convention:** `Authorization: Bearer <JWT>` and standard `X-Correlation-ID`; `Accept-Language` controls localized problem details.
- **Date/time formatting convention:** ISO 8601 UTC timestamps with a trailing `Z`.

## 3. Endpoints

### GET /api/v1/payments/me

**Description:** Returns the authenticated student's own payment history.  
**Required Role / Auth:** Authenticated `STUDENT`; ownership is derived from the caller identity.  
**Idempotency Requirement:** Not Required — read-only request with no side effect.

#### Request

**Headers:**

| Header           | Required | Description                               |
| :--------------- | :------- | :---------------------------------------- |
| Authorization    | Yes      | Bearer JWT for an active STUDENT account. |
| X-Correlation-ID | Yes      | Client-generated correlation identifier.  |
| Accept-Language  | Yes      | Requested language for error details.     |

**Query Parameters:**

| Parameter | Type    | Required | Description                                                                       |
| :-------- | :------ | :------- | :-------------------------------------------------------------------------------- |
| status    | enum    | No       | One of `PENDING`, `COMPLETED`, `FAILED`, `REFUNDED`; absent returns all statuses. |
| cursor    | string  | No       | Opaque cursor returned by an earlier request using the same status filter.        |
| size      | integer | No       | Requested page size; default `20`, inclusive range `1`–`100`.                     |

**Validation Rules:**

- `status` must be one of the declared values when supplied.
- `cursor` must be a valid opaque keyset cursor issued for `createdAt` descending and the same `status` filter.
- `size` must be an integer from `1` through `100`.

#### Response

**Success — 200 OK:**

```json
{
  "success": true,
  "message": "Payment history page",
  "data": {
    "items": [
      {
        "paymentId": "0a1d51bc-6689-45c0-9e1d-734899f6e129",
        "registration": {
          "id": "fa4c10c2-7994-4c31-a586-a88e7795d899",
          "workshopId": "5be67c0e-6495-4a35-846e-f1889d21ddbe",
          "status": "CONFIRMED"
        },
        "amount": 50.0,
        "currency": "USD",
        "status": "COMPLETED",
        "providerReferenceMasked": "••••a1b2",
        "statusOccurredAt": "2026-10-21T05:14:00Z",
        "createdAt": "2026-10-21T05:12:00Z",
        "updatedAt": "2026-10-21T05:14:00Z"
      }
    ],
    "cursor": {
      "nextCursor": "eyJpZCI6IjBhMWQ1MWJjLTY2ODktNDVjMC05ZTFkLTczNDg5OWY2ZTEyOSIsInNvcnRGaWVsZCI6ImNyZWF0ZWRBdCIsInByaW1hcnlWYWx1ZSI6IjIwMjYtMTAtMjFUMDU6MTI6MDBaIiwic2Vjb25kYXJ5VmFsdWUiOm51bGx9",
      "hasMore": false
    }
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

`statusOccurredAt` is the timestamp of the current visible status (`createdAt` for `PENDING`, otherwise the terminal-status timestamp). `providerReferenceMasked` contains only the final four characters of the gateway reference.

**Error Responses:**

| Status | Error Identifier                       | Condition                                                                                   |
| :----- | :------------------------------------- | :------------------------------------------------------------------------------------------ |
| 400    | `urn:unihub:error:bad-request`         | `cursor` is malformed, is not a valid keyset cursor, or was issued for another sort/filter. |
| 401    | `urn:unihub:error:unauthorized`        | JWT is missing, invalid, expired, revoked, or the account is inactive.                      |
| 403    | `urn:unihub:error:forbidden`           | Authenticated caller is not a STUDENT.                                                      |
| 422    | `urn:unihub:error:validation`          | `status` is unsupported or `size` is not in the inclusive range 1–100.                      |
| 429    | `urn:unihub:error:rate-limited`        | Default API token bucket is exhausted.                                                      |
| 500    | `urn:unihub:error:internal`            | Unexpected server failure.                                                                  |
| 503    | `urn:unihub:error:service-unavailable` | Required database read infrastructure is unavailable.                                       |

#### Side Effects

None.

#### Spec Traceability

- **Satisfies:** FR-01, FR-02, FR-04, FR-10, FR-12, FR-13, FR-14; Scenarios 1, 2, and 6.

### GET /api/v1/payments/:paymentId

**Description:** Returns one visible payment record to its student owner or to an organizer whose organization owns the linked workshop.  
**Required Role / Auth:** Authenticated `STUDENT` or `ORGANIZER`; student ownership and organizer organization scope are enforced before returning data.  
**Idempotency Requirement:** Not Required — read-only request with no side effect.

#### Request

**Headers:**

| Header           | Required | Description                                            |
| :--------------- | :------- | :----------------------------------------------------- |
| Authorization    | Yes      | Bearer JWT for an active STUDENT or ORGANIZER account. |
| X-Correlation-ID | Yes      | Client-generated correlation identifier.               |
| Accept-Language  | Yes      | Requested language for error details.                  |

**Path Parameters:**

| Parameter | Type | Description                          |
| :-------- | :--- | :----------------------------------- |
| paymentId | UUID | Identifier of the requested payment. |

**Validation Rules:**

- `paymentId` must be a UUID.
- A STUDENT receives a student payment shape; an ORGANIZER receives the same payment fields plus the minimal `student` object.

#### Response

**Success — 200 OK:**

```json
{
  "success": true,
  "message": "Payment retrieved",
  "data": {
    "paymentId": "0a1d51bc-6689-45c0-9e1d-734899f6e129",
    "registration": {
      "id": "fa4c10c2-7994-4c31-a586-a88e7795d899",
      "workshopId": "5be67c0e-6495-4a35-846e-f1889d21ddbe",
      "status": "CONFIRMED"
    },
    "amount": 50.0,
    "currency": "USD",
    "status": "COMPLETED",
    "providerReferenceMasked": "••••a1b2",
    "statusOccurredAt": "2026-10-21T05:14:00Z",
    "createdAt": "2026-10-21T05:12:00Z",
    "updatedAt": "2026-10-21T05:14:00Z"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

For an ORGANIZER, `data` additionally contains:

```json
{
  "student": {
    "fullName": "Nguyen Van A",
    "studentNumber": "S1234567"
  }
}
```

No other student identity fields are included.

**Error Responses:**

| Status | Error Identifier                       | Condition                                                                                                                                       |
| :----- | :------------------------------------- | :---------------------------------------------------------------------------------------------------------------------------------------------- |
| 400    | `urn:unihub:error:bad-request`         | `paymentId` is not a UUID.                                                                                                                      |
| 401    | `urn:unihub:error:unauthorized`        | JWT is missing, invalid, expired, revoked, or the account is inactive.                                                                          |
| 403    | `urn:unihub:error:forbidden`           | Authenticated caller has neither STUDENT nor ORGANIZER role.                                                                                    |
| 404    | `urn:unihub:error:not-found`           | Payment does not exist or is not visible to the caller; ownership and organization mismatches are intentionally indistinguishable from absence. |
| 429    | `urn:unihub:error:rate-limited`        | Default API token bucket is exhausted.                                                                                                          |
| 500    | `urn:unihub:error:internal`            | Unexpected server failure.                                                                                                                      |
| 503    | `urn:unihub:error:service-unavailable` | Required database read infrastructure is unavailable.                                                                                           |

#### Side Effects

None.

#### Spec Traceability

- **Satisfies:** FR-03, FR-04, FR-07, FR-08, FR-10, FR-11, FR-12, FR-13; Scenarios 3, 5, and 6.

### GET /api/v1/admin/payments

**Description:** Returns payment history for workshops in the authenticated organizer's organization.  
**Required Role / Auth:** Authenticated `ORGANIZER`; all results are restricted to the caller's organization.  
**Idempotency Requirement:** Not Required — read-only request with no side effect.

#### Request

**Headers:**

| Header           | Required | Description                                                            |
| :--------------- | :------- | :--------------------------------------------------------------------- |
| Authorization    | Yes      | Bearer JWT for an active ORGANIZER account with an organization claim. |
| X-Correlation-ID | Yes      | Client-generated correlation identifier.                               |
| Accept-Language  | Yes      | Requested language for error details.                                  |

**Query Parameters:**

| Parameter  | Type    | Required | Description                                                                       |
| :--------- | :------ | :------- | :-------------------------------------------------------------------------------- |
| workshopId | UUID    | No       | Restricts results to one workshop in the caller's organization.                   |
| status     | enum    | No       | One of `PENDING`, `COMPLETED`, `FAILED`, `REFUNDED`; absent returns all statuses. |
| cursor     | string  | No       | Opaque cursor returned by an earlier request using the same filters.              |
| size       | integer | No       | Requested page size; default `20`, inclusive range `1`–`100`.                     |

**Validation Rules:**

- `workshopId` must be a UUID and, when supplied, must identify a workshop visible to the caller's organization.
- `status`, `cursor`, and `size` follow the same validation rules as `GET /api/v1/payments/me`.

#### Response

**Success — 200 OK:**

Uses the same pagination container and payment fields as `GET /api/v1/payments/me`; every item additionally contains:

```json
{
  "student": {
    "fullName": "Nguyen Van A",
    "studentNumber": "S1234567"
  }
}
```

**Error Responses:**

| Status | Error Identifier                       | Condition                                                                            |
| :----- | :------------------------------------- | :----------------------------------------------------------------------------------- |
| 400    | `urn:unihub:error:bad-request`         | `workshopId` or `cursor` is malformed, or cursor was issued for another sort/filter. |
| 401    | `urn:unihub:error:unauthorized`        | JWT is missing, invalid, expired, revoked, or the account is inactive.               |
| 403    | `urn:unihub:error:forbidden`           | Authenticated caller is not an ORGANIZER or has no organization scope.               |
| 404    | `urn:unihub:error:not-found`           | Supplied `workshopId` does not exist or is not visible to the caller's organization. |
| 422    | `urn:unihub:error:validation`          | `status` is unsupported or `size` is not in the inclusive range 1–100.               |
| 429    | `urn:unihub:error:rate-limited`        | Default API token bucket is exhausted.                                               |
| 500    | `urn:unihub:error:internal`            | Unexpected server failure.                                                           |
| 503    | `urn:unihub:error:service-unavailable` | Required database read infrastructure is unavailable.                                |

#### Side Effects

None.

#### Spec Traceability

- **Satisfies:** FR-05, FR-06, FR-08, FR-10, FR-11, FR-12, FR-13, FR-14; Scenarios 4, 5, and 6.

### GET /api/v1/admin/payments/summary

**Description:** Returns an organization-scoped payment operational summary, separately grouped by currency.  
**Required Role / Auth:** Authenticated `ORGANIZER`; summary rows are restricted to workshops owned by the caller's organization.  
**Idempotency Requirement:** Not Required — read-only request with no side effect.

#### Request

**Headers:**

| Header           | Required | Description                                                            |
| :--------------- | :------- | :--------------------------------------------------------------------- |
| Authorization    | Yes      | Bearer JWT for an active ORGANIZER account with an organization claim. |
| X-Correlation-ID | Yes      | Client-generated correlation identifier.                               |
| Accept-Language  | Yes      | Requested language for error details.                                  |

The endpoint accepts no query parameters.

#### Response

**Success — 200 OK:**

```json
{
  "success": true,
  "message": "Payment summary",
  "data": {
    "byCurrency": [
      {
        "currency": "USD",
        "pending": { "count": 2, "amount": 100.0 },
        "completed": { "count": 18, "amount": 900.0 },
        "failed": { "count": 1, "amount": 50.0 },
        "refunded": { "count": 3, "amount": 150.0 }
      }
    ]
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

Each `amount` is the sum of records in exactly that `currency` and status bucket. `byCurrency` is an empty array when the organization has no payment records; no cross-currency grand total is returned.

**Error Responses:**

| Status | Error Identifier                       | Condition                                                              |
| :----- | :------------------------------------- | :--------------------------------------------------------------------- |
| 401    | `urn:unihub:error:unauthorized`        | JWT is missing, invalid, expired, revoked, or the account is inactive. |
| 403    | `urn:unihub:error:forbidden`           | Authenticated caller is not an ORGANIZER or has no organization scope. |
| 429    | `urn:unihub:error:rate-limited`        | Default API token bucket is exhausted.                                 |
| 500    | `urn:unihub:error:internal`            | Unexpected server failure.                                             |
| 503    | `urn:unihub:error:service-unavailable` | Required database read infrastructure is unavailable.                  |

#### Side Effects

None.

#### Spec Traceability

- **Satisfies:** FR-09, FR-10, FR-11, FR-12, FR-13; Scenarios 4, 6, and 7.

## 4. Rate Limiting Behavior

All endpoints use the platform Default Tier: 100 tokens per authenticated caller, refilled at 100 tokens per 60 seconds. On exhaustion, the API returns `429` with the standard rate-limit headers and RFC 7807 error envelope.

## 5. Idempotency Behavior

Not applicable. These four endpoints are safe to repeat because they are read-only and create no records, events, cache mutations, or external calls.

## 6. Cross-Endpoint Consistency Notes

- All payment resources use the same names and types for `paymentId`, `registration`, `amount`, `currency`, `status`, `providerReferenceMasked`, `statusOccurredAt`, `createdAt`, and `updatedAt`.
- All collection endpoints use the shared cursor contract; `cursor` must not be combined with changed filters.
- Student data is absent from student-visible resources and limited to `fullName` and `studentNumber` for organizer-visible resources.
- All visibility failures for an existing payment or workshop use `404` where stated, preventing cross-student and cross-organization resource enumeration.
- The contract deliberately omits `providerPaymentId`, `providerRefundId`, `paymentClientSecret`, QR data, `failureCode`, and `failureReason` from every response.
