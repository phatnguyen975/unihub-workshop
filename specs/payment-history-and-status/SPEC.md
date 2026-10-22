# Spec: Payment History and Status

> **Status:** APPROVED  
> **Domain:** 5 — Payment Processing  
> **Owner/area:** `payments` backend module  
> **Last Revised:** 2026-10-22 — first version

## 1. Overview

This feature gives students transparent, read-only visibility of their own paid-workshop payment attempts and gives organizers an organization-scoped operational view of payment activity. It makes the existing payment lifecycle visible without exposing gateway secrets or allowing any payment or registration state to be changed through this feature.

## 2. Actors & Preconditions

| Actor     | Precondition                                                                                                                    |
| :-------- | :------------------------------------------------------------------------------------------------------------------------------ |
| STUDENT   | Has an active authenticated account and may have one or more registrations with payment records.                                |
| ORGANIZER | Has an active authenticated account associated with an organization and may view only workshops belonging to that organization. |

## 3. Functional Requirements

### Student history and detail

- **FR-01:** The system SHALL return a paginated, newest-first history of payment records linked to registrations owned by the authenticated STUDENT.
- **FR-02:** The system SHALL allow an authenticated STUDENT to filter that history by one payment status when a valid status filter is supplied.
- **FR-03:** The system SHALL return an individual payment record to an authenticated STUDENT only when its linked registration is owned by that STUDENT.
- **FR-04:** The system SHALL present each student-visible payment record with its amount, ISO 4217 currency, payment status, masked provider reference, registration context, and timestamps needed to show the current payment progress.

### Organizer history, detail, and summary

- **FR-05:** The system SHALL return a paginated, newest-first history of payment records for workshops belonging to the authenticated ORGANIZER's organization.
- **FR-06:** The system SHALL allow an authenticated ORGANIZER to filter that history by an accessible workshop and by one valid payment status when either filter is supplied.
- **FR-07:** The system SHALL return an individual payment record to an authenticated ORGANIZER only when its linked workshop belongs to that ORGANIZER's organization.
- **FR-08:** The system SHALL include only the student name and student number needed for organizer operations with organizer-visible payment records.
- **FR-09:** The system SHALL return an organization-scoped operational payment summary grouped by ISO 4217 currency, with separate counts and amounts for each payment status.

### Access control and data protection

- **FR-10:** The system SHALL reject unauthenticated requests for payment history, detail, and summary.
- **FR-11:** The system SHALL deny STAFF access to payment history, payment detail, and payment summary.
- **FR-12:** The system SHALL not expose an unmasked gateway payment identifier, payment client secret, QR data, gateway refund identifier, provider failure code, or provider failure reason in any feature response.

### Read consistency and pagination

- **FR-13:** The system SHALL treat payment history and summary as eventually consistent, non-critical read views and may return a short-lived stale result after a payment state transition.
- **FR-14:** The system SHALL use opaque cursor pagination for every payment collection, return the continuation cursor and whether another page exists, and SHALL reject a continuation cursor that was issued for a different filter set.

## 4. Business Rules

- **BR-01:** A payment has exactly one linked registration, and a registration has at most one linked payment.
- **BR-02:** The only payment statuses visible through this feature are `PENDING`, `COMPLETED`, `FAILED`, and `REFUNDED`.
- **BR-03:** The payment history ordering is `createdAt` descending with the payment identifier as the deterministic tie-breaker.
- **BR-04:** Currency values are ISO 4217 codes; monetary amounts from different currencies MUST NOT be added together.
- **BR-05:** Student ownership is determined by the student identity on the linked registration.
- **BR-06:** Organizer visibility is determined by the linked workshop's organization and the caller's organization claim; an inaccessible record is treated as not visible.
- **BR-07:** Payment status and registration status are read-only in this feature. Their transitions remain owned by the established payment outcome and registration reconciliation flows.
- **BR-08:** A masked provider reference reveals only the final four characters of the provider payment reference; the complete value is never returned.
- **BR-09:** When the provider payment reference is shorter than four characters, the entire value is masked so that no provider reference is ever disclosed in full.

## 5. State Transitions

This feature introduces no state transition. It displays the existing lifecycle below and SHALL never trigger one.

| From State       | Event/Trigger                                    | To State        |     Valid?      | Rejection Behavior (if invalid)                                                      |
| :--------------- | :----------------------------------------------- | :-------------- | :-------------: | :----------------------------------------------------------------------------------- |
| PENDING          | Successful payment outcome                       | COMPLETED       |       Yes       | N/A — performed outside this feature.                                                |
| PENDING          | Failed payment outcome or reconciliation failure | FAILED          |       Yes       | N/A — performed outside this feature.                                                |
| COMPLETED        | Successful refund                                | REFUNDED        |       Yes       | N/A — performed outside this feature.                                                |
| PENDING          | Read request                                     | PENDING         | No state change | Return the current read model only.                                                  |
| COMPLETED        | Read request                                     | COMPLETED       | No state change | Return the current read model only.                                                  |
| FAILED           | Read request                                     | FAILED          | No state change | Return the current read model only.                                                  |
| REFUNDED         | Read request                                     | REFUNDED        | No state change | Return the current read model only.                                                  |
| FAILED, REFUNDED | Any payment outcome transition                   | Any other state |       No        | Rejected by the lifecycle owner; this feature has no mutation operation.             |
| COMPLETED        | Payment success outcome                          | COMPLETED       |       No        | Idempotently ignored by the lifecycle owner; this feature has no mutation operation. |

## 6. Non-Functional Requirements

- **Performance:** Collection reads SHALL be keyset-paginated with a default page size of 20 and a maximum of 100; they must not perform unbounded payment scans or per-item dependent reads.
- **Read-path architecture:** Ownership and organization visibility SHALL be evaluated in the same database statement that produces the keyset window, through a read-only projection over `payments` joined to the owning module's tables. Joined tables contribute only a visibility predicate; every response field they own SHALL be resolved through that module's public API. This projection SHALL NOT be used by any write, idempotency, capacity, or state-transition path (see `docs/project-structure.md` §3).
- **Cursor integrity:** A continuation cursor SHALL be bound to the filter set that produced it; replaying it against a different filter set is a client error and SHALL be rejected rather than silently reinterpreted.
- **Consistency:** These read views may route to a replica and may be briefly stale. Authorization and any future state-changing check-then-act operation must not rely on a replica result.
- **Security:** JWT authentication, role enforcement, student ownership, and organizer organization isolation are mandatory. Sensitive gateway, QR, refund, and failure-detail fields are excluded by contract.
- **Idempotency:** No idempotency key is required because all feature operations are read-only and have no side effects.
- **Rate limiting:** The platform default read-route token-bucket tier applies; a limited request receives the standard rate-limit response.
- **Reliability:** An empty history or empty organization summary is a successful response, not an error. A replica or database availability failure uses the standard service failure response and never falls back to exposing stale client-side data as authoritative.
- **Observability:** Requests SHALL carry the standard correlation identifier and return the standard trace metadata. Access-denied and rate-limited requests remain observable through platform logging and metrics.

## 7. Acceptance Criteria

### Scenario 1: Student sees a mixed-status history

```gherkin
Given an authenticated STUDENT owns registrations with PENDING, COMPLETED, FAILED, and REFUNDED payments
When the STUDENT requests payment history
Then the response contains only that STUDENT's payment records in newest-first order
And each record contains its amount, currency, status, masked provider reference, registration context, and progress timestamp
And no record contains a client secret, QR data, full gateway reference, refund reference, or failure detail
```

_Traces to FR-01, FR-04, FR-12, FR-14._

### Scenario 2: Student filters and follows a cursor

```gherkin
Given an authenticated STUDENT has more completed payments than fit in one page
When the STUDENT requests history filtered to COMPLETED with a valid page size
Then the response contains no payment in another status
And it contains an opaque next cursor when another page exists
When the STUDENT submits that cursor unchanged
Then the response returns the next stable page without repeating an earlier item
And a cursor issued for a different status filter is rejected with the standard bad-request response
```

_Traces to FR-02, FR-14._

### Scenario 3: Student cannot view another student's payment

```gherkin
Given an authenticated STUDENT requests the identifier of a payment linked to another STUDENT's registration
When the detail is requested
Then the system responds as if the payment is not visible
And it returns no payment data
```

_Traces to FR-03, FR-10, FR-12._

### Scenario 4: Organizer sees only organization-scoped operational data

```gherkin
Given an authenticated ORGANIZER's organization owns two workshops with payments
And another organization owns a separate workshop with payments
When the ORGANIZER requests payment history and the currency-grouped summary
Then the history and summary contain only the first organization's payment data
And each organizer-visible record contains only the student name and student number as student identity
And no total combines monetary values from different currencies
```

_Traces to FR-05, FR-08, FR-09._

### Scenario 5: Organizer cannot use an inaccessible workshop filter or payment identifier

```gherkin
Given an authenticated ORGANIZER supplies a workshop or payment belonging to another organization
When the ORGANIZER requests payment history or payment detail
Then the system returns no accessible resource
And it does not disclose the other organization's payment data
```

_Traces to FR-06, FR-07._

### Scenario 6: Staff and unauthenticated callers are denied

```gherkin
Given a caller is unauthenticated or has the STAFF role
When the caller requests payment history, detail, or summary
Then the system rejects the request using the standard authentication or authorization response
```

_Traces to FR-10, FR-11._

### Scenario 7: Recently changed payment state can be briefly stale

```gherkin
Given a payment has just transitioned to COMPLETED, FAILED, or REFUNDED
When an eligible caller immediately requests history or summary
Then the caller may receive the previous state for a short replication delay
And a later eligible request reflects the finalized state
```

_Traces to FR-13._

## 8. Out of Scope

- Creating payment intents, charging a payment method, payment retries, refunds, cancellation, reconciliation, or handling provider webhooks.
- Changing a payment or registration status, issuing QR data, or publishing notification and real-time events.
- Exposing gateway identifiers, client secrets, refund identifiers, or provider failure details to any client.
- Cross-organization finance reporting, export files, settlement, accounting conversion, or aggregation across currencies.
- Mobile client screens and worker pipelines.

## 9. Linked References

- `docs/feature-catalog.md` — Feature 5.2 and Appendix A dependency map.
- `docs/overview.md` §§3–4, 7, and 9 — scale, roles, payment resilience, and RBAC goals.
- `docs/system-design.md` §4 Payment Webhook Handling Flow; §5 RBAC Model & Logical Multi-Tenancy; §6 Primary–Replica Read Routing and Traffic Spike Management.
- `docs/database-schema.md` §3 tables `registrations` and `payments`; §4 Replication Lag & Read-After-Write Consistency.
- `docs/adrs.md` ADR-006, ADR-014, ADR-015, and ADR-022.
- `docs/api-standards.md` — pagination, response/error envelopes, auth headers, timestamps, and rate limiting.
- `docs/project-structure.md` §3 Core Backend and Internal Module Communication (including the bounded read-only cross-module projection rule).
