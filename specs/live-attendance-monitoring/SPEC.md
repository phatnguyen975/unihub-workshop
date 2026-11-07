# Spec: Live Attendance Monitoring

> **Status:** APPROVED  
> **Domain:** 6 — On-Site Check-in & Attendance  
> **Owner/area:** `attendances` — Core Backend  
> **Created:** 2026-11-06

## 1. Overview

Live Attendance Monitoring gives organizers and explicitly assigned check-in staff a read-only baseline of a workshop's server-reconciled attendance: configured capacity, confirmed registrations, valid check-ins, check-in percentage, and a bounded preview of recent successful scans. It supports operational monitoring and historical reporting while preserving organization and assignment boundaries. The attendance area owns the summary, obtains workshop context and registration totals from their owning areas, and consumes the existing attendance-update event and shared real-time transport. Counts reflect attendance accepted by the server, not scans still queued on disconnected devices.

## 2. Actors & Preconditions

| Actor                      | Preconditions and responsibility                                                                                                                                                                                                   |
| :------------------------- | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Organizer (`ORGANIZER`)    | Authenticated with a valid, non-revoked identity and a valid organization context matching the workshop's owning organization. Any organizer of that organization may view it; being its creator is not required.                  |
| Check-in staff (`STAFF`)   | Authenticated with a valid, non-revoked identity and currently assigned to the requested workshop. Organization membership alone does not grant access.                                                                            |
| Monitoring client          | An organizer's web client or an assigned staff member's mobile client consuming the summary and existing live updates. Client consumption requirements are defined here; implementing the clients is outside this backend feature. |
| Attendance synchronization | Already owns reconciliation, durable attendance records, and production of attendance-update events.                                                                                                                               |
| Real-time distribution     | Already owns authenticated live connections, workshop-scoped event distribution, and reconnect framing.                                                                                                                            |
| System                     | Primary is available for current authorization and workshop metadata; the read replica is available for reporting. Revocation verification must be available before an authenticated read can proceed.                             |

An existing workshop may be in any of `DRAFT`, `PUBLISHED`, `COMPLETED`, or `CANCELED`. Neither the start time nor the end time restricts this reporting capability.

## 3. Functional Requirements

### 3.1 Identity, authorization, and workshop eligibility

- **FR-01:** The system SHALL reject a summary request as unauthenticated when the caller's authentication is missing, malformed, expired, signature-invalid, or revoked.
- **FR-02:** The system SHALL reject a summary request as forbidden when the authenticated caller's role is neither `ORGANIZER` nor `STAFF`.
- **FR-03:** The system SHALL reject a summary request as invalid input when the workshop identifier cannot be interpreted as a valid identifier.
- **FR-04:** The system SHALL reject an organizer's summary request as forbidden when the caller's organization context is missing or invalid.
- **FR-05:** The system SHALL reject a summary request as not found when the workshop does not exist on Primary, after the caller has passed the applicable identity and role checks.
- **FR-06:** The system SHALL reject an organizer's summary request as forbidden when the existing workshop belongs to a different organization.
- **FR-07:** The system SHALL reject a staff member's summary request as forbidden when no assignment to the requested workshop exists on Primary at authorization time.
- **FR-08:** The system SHALL permit an otherwise authorized staff member to view the summary based on that assignment without requiring the staff member's organization to match the workshop's organization.
- **FR-09:** The system SHALL resolve workshop existence, ownership, staff assignment when applicable, and workshop metadata from Primary before obtaining reporting values from the read replica.
- **FR-10:** The system SHALL permit an authorized summary read for each of `DRAFT`, `PUBLISHED`, `COMPLETED`, and `CANCELED`, irrespective of the workshop's start or end time.

### 3.2 Summary values

- **FR-11:** The system SHALL include the requested workshop's identifier and configured capacity from the authorized workshop context in a successful summary, representing an absent draft capacity as zero.
- **FR-12:** The system SHALL return the workshop's current confirmed-registration count from the read replica, counting only registrations in `CONFIRMED` status.
- **FR-13:** The system SHALL return the workshop's valid checked-in count from the read replica, counting only persisted attendance in `VALID` status independently of each registration's current status.
- **FR-14:** The system SHALL return the check-in percentage as the valid checked-in count multiplied by 100 and divided by the confirmed-registration count, rounded to two decimal places using half-up rounding, when the confirmed-registration count is positive.
- **FR-15:** The system SHALL return a check-in percentage of zero when the confirmed-registration count is zero, including when valid attendance exists.
- **FR-16:** The system SHALL preserve the actual counts and uncapped percentage when the checked-in count exceeds the confirmed-registration count.
- **FR-17:** The system SHALL return zero counts, a zero percentage, and an empty recent-scan preview when the reporting datasets contain no applicable records for the authorized workshop.
- **FR-18:** The system SHALL scope every reported count and recent-scan record to the requested workshop.

### 3.3 Recent successful scans

- **FR-19:** The system SHALL return the newest ten persisted attendance records in `VALID` status for the workshop, or all eligible records when fewer than ten are visible to the preview read.
- **FR-20:** The system SHALL order that preview by recorded check-in time descending, breaking equal-time ties by attendance identifier descending.
- **FR-21:** The system SHALL include only the attendance identifier, registration identifier, recorded check-in time, and server receipt time in each recent-scan item.
- **FR-22:** The system SHALL preserve the stored check-in and receipt timestamps when presenting a recent scan, rather than replacing check-in time with synchronization or publication time.
- **FR-23:** The system SHALL keep recent-scan entries tied to persisted attendance records when synchronization returns a successful retry outcome without inserting a new record.

### 3.4 Consistency, failures, and read-only behavior

- **FR-24:** The system SHALL obtain registration and attendance reporting values from the read replica after the Primary authorization phase has completed.
- **FR-25:** The system SHALL return a service-unavailable failure with a retry indication when a required Primary or replica read cannot be completed because that datasource is unavailable.
- **FR-26:** The system SHALL reject a summary request as service unavailable when identity revocation cannot be checked, rather than granting access without that check.
- **FR-27:** The system SHALL reject requests exceeding the default admission quota of 100 requests per 60 seconds per authenticated user when quota evaluation is available.
- **FR-28:** The system SHALL allow an otherwise authenticated and authorized request to proceed when only quota evaluation is unavailable.
- **FR-29:** The system SHALL leave workshop, registration, assignment, and attendance business state unchanged when a summary is requested, including repeated requests.
- **FR-30:** The system SHALL provide a new summary read as the baseline recovery mechanism when monitoring opens, reconnects, or needs to reconcile a missing live update, without promising replay of missed events.
- **FR-31:** The system SHALL represent summary timestamps using the project's UTC timestamp convention without adding an inferred last-synchronization timestamp to the summary.

### 3.5 Existing live-update contract

- **FR-32:** The system SHALL retain the existing attendance-update payload containing exactly the workshop identifier, valid checked-in count, configured capacity, and server event-production timestamp.
- **FR-33:** The system SHALL retain the rule that attendance synchronization produces one workshop update after commitment when at least one new attendance record was inserted, and none when the synchronization inserted no new record.
- **FR-34:** The system SHALL retain workshop-scoped delivery of attendance updates to authorized organizers and assigned staff through the existing shared live stream.

FR-32–FR-34 describe dependency contracts already owned by attendance synchronization and real-time distribution. This feature documents and consumes those contracts; reading a summary does not itself publish an event or create another transport.

**Requirement sources:** FR-01–FR-31 derive from `docs/feature-catalog.md` §6.3, the reporting flow and protection mechanisms in `docs/system-design.md`, and the data/routing standards. FR-32–FR-34 additionally trace to the attendance-sync specification's FR-42–FR-46 and the shared streaming contract. The reference table in §9 identifies the exact supporting sections.

## 4. Business Rules

- **BR-01 — Resource scope:** Organization ownership authorizes organizers; explicit assignment authorizes staff. Never infer staff access from organization membership, workshop visibility in an earlier download, or a client-supplied actor. Assignment existence is the authority; assignments have no independent active-state or expiry field. _(Catalog §6.3; ADR-006; assignment data model.)_
- **BR-02 — Current permission, eventual metrics:** The access decision and workshop metadata use Primary; reporting uses the read replica after the access phase ends. Authorization is current at its check, not locked throughout response delivery. _(Catalog §6.3; ADR-015.)_
- **BR-03 — Distinct populations:** Confirmed registrations exclude `PENDING`, `FAILED`, and `CANCELED`. Valid attendance excludes `INVALIDATED`, but remains counted even if its registration subsequently changes status. Do not redefine either population to force agreement. _(Catalog §6.3; attendance data semantics.)_
- **BR-04 — Percentage meaning:** Attendance percentage uses confirmed registrations as the denominator, not capacity. Half-up rounding is to two decimal places; zero denominator yields zero; values above 100 are retained. This differs from registration occupancy. _(Catalog §6.3; registration-monitoring specification.)_
- **BR-05 — Bounded preview:** The recent preview has a fixed maximum of ten and a fixed descending time/identifier order. It is not a traversable attendance collection and has no pagination, filtering, or sorting controls. _(Catalog §6.3; API standards, bounded aggregate previews.)_
- **BR-06 — Scan provenance:** Check-in time is the stored, clamped device scan time; server receipt time is separate. An old offline scan received recently need not enter the newest-ten preview. _(Attendance data model; synchronization specification.)_
- **BR-07 — No hidden snapshot guarantee:** Primary metadata and replica reporting are not one atomic snapshot. Separate reporting values may also reflect concurrent changes. The percentage is computed from the counts actually returned, but no stronger relationship between preview length and a separately sampled count is promised. _(Reporting flow; ADR-015; routing standards.)_
- **BR-08 — No fallback or partial success:** Do not silently route failed replica reporting to Primary or return a successful partial summary when a required read fails. No cached or fabricated zero replaces an infrastructure failure. _(Catalog §6.3; reporting failure policy.)_
- **BR-09 — Read without mutation:** A summary request creates no attendance, changes no status, emits no domain or live-update event, and requires no request-level deduplication key. Operational request logs and admission accounting remain applicable. _(Catalog §6.3; reporting flow.)_
- **BR-10 — Consumer recovery:** Consumers load the baseline on opening and reconnecting and automatically refetch after an attendance update for the displayed workshop. Coalesce and throttle these refreshes, keep at most one summary request in flight for that view, and honor admission/retry indications. Periodic polling is a fallback for unavailable streaming, not a second refresh loop while streaming is healthy. _(Catalog §6.3; ADR-031; API streaming standards.)_
- **BR-11 — Event versus baseline freshness:** Keep the live counter distinct from the replica baseline so a lagging summary cannot overwrite it as if it were newer. An event identifier or publication time is not a shared database version. No durable replay or immediate replica catch-up is promised. _(Catalog §6.3; streaming standards.)_
- **BR-12 — Event ownership and timestamps:** Synchronization owns event production; real-time distribution owns delivery. A successful retry without a new attendance row does not create another event. Event-production time is not a persisted attendance timestamp and must not be reconstructed as a summary field. _(Catalog §6.2–§6.3; synchronization specification FR-42–FR-46.)_

BR-10–BR-11 define the consumer protocol that accompanies the backend contract. Implementing web/mobile rendering, refresh scheduling, or local queue handling is outside this feature.

## 5. State Transitions

**N/A for mutations — this feature introduces no state transition.** Reads leave every observed entity unchanged. The complete workshop eligibility matrix is:

| Workshop state | Authorized summary read | Resulting state | Restriction                                                            |
| :------------- | :---------------------- | :-------------- | :--------------------------------------------------------------------- |
| `DRAFT`        | Allowed                 | `DRAFT`         | No schedule required; absent capacity is represented as zero.          |
| `PUBLISHED`    | Allowed                 | `PUBLISHED`     | Before, during, or after the scheduled session.                        |
| `COMPLETED`    | Allowed                 | `COMPLETED`     | Historical reporting remains available.                                |
| `CANCELED`     | Allowed                 | `CANCELED`      | Reporting remains available; synchronization eligibility is unchanged. |

| Observed entity state                           | Reporting treatment                                        | State after read |
| :---------------------------------------------- | :--------------------------------------------------------- | :--------------- |
| Registration `CONFIRMED`                        | Included in confirmed count                                | Unchanged        |
| Registration `PENDING`, `FAILED`, or `CANCELED` | Excluded from confirmed count                              | Unchanged        |
| Attendance `VALID`                              | Included in checked-in count; eligible for bounded preview | Unchanged        |
| Attendance `INVALIDATED`                        | Excluded from checked-in count and preview                 | Unchanged        |

An unauthenticated or unauthorized read is rejected without a state change in every row above. This feature offers no operation to transition any of these states; their existing owners retain lifecycle authority.

## 6. Non-Functional Requirements

- **Performance:** Reporting performs scoped aggregates and one bounded preview, with no per-attendee or per-registration identity lookup and no unbounded record load for application-side counting. The number of business reads must not grow per returned attendee. The documented valid-attendance reporting index must support the workshop/time access path before that path is relied on. No new latency percentile target is invented beyond existing platform requirements.
- **Concurrency:** Concurrent reads and synchronization must not introduce writes, locks that serialize all monitoring reads, or another check-in. Readers tolerate replica lag and concurrent changes as specified in BR-07.
- **Idempotency:** Repeating a summary read has no business side effect; it may return newer reporting values. No mutation claim or stored-response replay is introduced.
- **Admission control:** Default quota is 100 requests per 60 seconds per authenticated user, shared with other default-tier requests. Quota evaluation fails open; revocation verification fails closed. Existing live-stream handshake quota is separate.
- **Security:** Authenticate and check scope before returning any reporting values. Expose no student profile, email, device identifier, checking-in staff identifier, ticket payload, or signature in the preview. Standard transport hardening and request tracing apply.
- **Reliability:** A failed access or reporting read returns a failure rather than incomplete success. A lost live event does not change durable attendance; a later successful baseline read recovers the replica-visible state without a replay guarantee.
- **Representation:** Use the existing project success/error conventions, omit absent optional values, and represent required empty results explicitly. The summary's counts and preview are required values, not nullable substitutes for failed reads.
- **Observability:** Use existing request logging and metrics for operation, status, duration, trace identifier, and processing node. Do not log tokens, ticket contents, or student identity data. No new custom metric is required by this feature.
- **Verification:** Use unit tests with external dependencies replaced and static architecture checks; no live database, message service, or application-context integration test is introduced.

## 7. Acceptance Criteria

### Scenario 1: Organizer retrieves their organization's summary

```gherkin
Given an organizer of organization "A" and a workshop of organization "A" with capacity 100
And the replica has 80 confirmed registrations, 7 pending, 3 failed, and 4 canceled
And the replica has 50 valid attendance records and 2 invalidated attendance records
When the organizer requests that workshop's summary
Then the result has capacity 100, confirmed count 80, checked-in count 50, and percentage 62.50
And the recent preview contains at most 10 valid attendance records from that workshop
```

_Satisfies: FR-09, FR-11–FR-14, FR-18–FR-19._

### Scenario 2: Assigned staff access does not depend on organization membership

```gherkin
Given an authenticated staff member with a Primary assignment to the requested workshop
And the staff member's organization differs from the workshop's organization
When that staff member requests the summary
Then access is allowed
And the summary contains only the requested workshop's values
```

_Satisfies: FR-08–FR-09, FR-18._

### Scenario 3: Invalid identity or prohibited role

```gherkin
Scenario Outline: Deny a caller before business reporting
  Given a caller with <condition>
  When the caller requests a workshop summary
  Then the request fails as <failure>
  And no reporting values are returned

  Examples:
    | condition                           | failure         |
    | missing authentication              | unauthenticated |
    | malformed authentication            | unauthenticated |
    | expired authentication              | unauthenticated |
    | an invalid authentication signature | unauthenticated |
    | revoked authentication              | unauthenticated |
    | a valid STUDENT identity            | forbidden       |
```

_Satisfies: FR-01–FR-02._

### Scenario 4: Invalid workshop identifier or organizer context

```gherkin
Scenario Outline: Deny invalid request context
  Given an authenticated organizer with <organizationContext>
  When the organizer requests a summary using <workshopIdentifier>
  Then the request fails as <failure>

  Examples:
    | organizationContext     | workshopIdentifier | failure       |
    | a valid organization    | a malformed value  | invalid input |
    | no organization         | a valid identifier | forbidden     |
    | an invalid organization | a valid identifier | forbidden     |
```

_Satisfies: FR-03–FR-04._

### Scenario 5: Distinguish missing workshop from denied workshop scope

```gherkin
Scenario Outline: Resolve existence before resource permission
  Given an authenticated caller with an allowed role and valid required identity context
  And <workshopCondition>
  When the caller requests that workshop's summary
  Then the request fails as <failure>
  And replica reporting is not performed

  Examples:
    | workshopCondition                                                         | failure   |
    | the workshop does not exist on Primary                                    | not found |
    | an organizer requests another organization's existing workshop            | forbidden |
    | a staff member has no assignment to the existing workshop                 | forbidden |
    | a staff member belongs to the workshop organization but has no assignment | forbidden |
```

_Satisfies: FR-05–FR-07, FR-09._

### Scenario 6: Revoked assignment cannot authorize through replica lag

```gherkin
Given a staff assignment has been revoked on Primary before authorization
And the replica still contains the old assignment
When that staff member requests the summary
Then the request fails as forbidden
And no replica reporting values are returned
```

_Satisfies: FR-07, FR-09._

### Scenario 7: Every workshop state supports authorized historical reporting

```gherkin
Scenario Outline: Read without a lifecycle or schedule gate
  Given an authorized caller and an existing workshop in state <state>
  And the workshop is unscheduled, upcoming, in progress, or past as applicable
  When the caller requests the summary
  Then access is allowed
  And the workshop remains in state <state>

  Examples:
    | state     |
    | DRAFT     |
    | PUBLISHED |
    | COMPLETED |
    | CANCELED  |
```

_Satisfies: FR-10, FR-29._

### Scenario 8: Empty reporting data and absent draft capacity

```gherkin
Given an authorized organizer and an existing draft workshop with no configured capacity
And the reporting datasets contain no applicable records
When the organizer requests the summary
Then capacity, confirmed count, checked-in count, and percentage are zero
And the recent preview is an empty array
And the result is not a not-found failure
```

_Satisfies: FR-11, FR-15, FR-17._

### Scenario 9: Percentage uses confirmed registrations and half-up rounding

```gherkin
Scenario Outline: Calculate from the returned counts
  Given an authorized workshop with capacity 200
  And the replica reports <confirmed> confirmed registrations and <checkedIn> valid check-ins
  When the caller requests the summary
  Then the percentage is <percentage>
  And both counts retain their actual values

  Examples:
    | confirmed | checkedIn | percentage |
    | 32        | 1         | 3.13       |
    | 3         | 2         | 66.67      |
    | 2         | 3         | 150.00     |
    | 0         | 1         | 0.00       |
```

_Satisfies: FR-12–FR-16._

### Scenario 10: Attendance is independent of current registration status

```gherkin
Given one confirmed registration and one canceled registration for the workshop
And both registrations have persisted valid attendance
And another attendance record is invalidated
When an authorized caller requests the summary
Then confirmed count is 1 and checked-in count is 2
And percentage is 200.00
And only the two valid attendance records are eligible for the preview
```

_Satisfies: FR-12–FR-16, FR-19._

### Scenario 11: Preview ordering, bound, and deliberate fields

```gherkin
Given more than 10 valid attendance records for the requested workshop
And some records share the same check-in timestamp
And newer invalidated records and another workshop's records also exist
When an authorized caller requests the summary
Then the preview contains exactly the newest 10 eligible records
And equal-time records are ordered by attendance identifier descending
And each item contains only attendance identifier, registration identifier, check-in time, and server receipt time
And no invalidated record or other-workshop record appears
```

_Satisfies: FR-18–FR-22._

### Scenario 12: Late offline arrival and a successful retry do not distort the preview

```gherkin
Given a persisted scan has an older check-in time and a newer server receipt time than the newest ten scans
And retrying that scan succeeded without inserting another attendance record
When an authorized caller requests the summary
Then the older scan does not displace a more recently scanned record because of its arrival time
And the retry produces no duplicate preview entry or additional checked-in count
And stored timestamps are preserved
```

_Satisfies: FR-13, FR-20, FR-22–FR-23._

### Scenario 13: Reporting remains replica-backed after Primary authorization

```gherkin
Given Primary authorizes the caller and reports workshop capacity 100
And Primary has 60 valid check-ins while the replica currently has 58
When the caller requests the summary
Then authorization completes before replica reporting begins
And capacity is 100 and checked-in count is 58
And the result does not promise an atomic or immediately current snapshot
```

_Satisfies: FR-09, FR-11, FR-24._

### Scenario 14: Required datasource or revocation-check unavailability

```gherkin
Scenario Outline: Fail without partial success or a hidden datasource fallback
  Given an otherwise valid summary request
  And <dependency> is unavailable when required
  When the request is processed
  Then the request fails as service unavailable with a retry indication
  And no successful partial summary is returned
  And reporting does not fall back to Primary

  Examples:
    | dependency                           |
    | Primary workshop authorization reads |
    | replica confirmed-registration reads |
    | replica attendance reads             |
    | identity revocation verification     |
```

_Satisfies: FR-25–FR-26._

### Scenario 15: Admission quota and its distinct failure policy

```gherkin
Given an authenticated user has exhausted the shared default quota
When the user requests another summary while quota evaluation is available
Then the request is rejected as rate limited
But when only quota evaluation is unavailable and identity verification succeeds
Then an otherwise authorized summary request may proceed
```

_Satisfies: FR-27–FR-28._

### Scenario 16: Repeated reads and reconnect recovery are side-effect free

```gherkin
Given the caller is authorized and disconnected while attendance changed
When the caller requests a fresh summary after reconnecting and then requests it again
Then each read returns the applicable replica-visible baseline
And no missed-event replay or request-level replay key is required
And no workshop, assignment, registration, or attendance is changed by the reads
And no attendance-update event is published by either read
And no inferred last-synchronization timestamp appears in the summary
And all returned timestamps follow the UTC convention
```

_Satisfies: FR-29–FR-31._

### Scenario 17: Dependency events retain the established monitoring contract

```gherkin
Given a synchronization commits at least one new valid attendance record
When its owning capability produces a workshop attendance update
Then one event contains the workshop identifier, valid checked-in count, capacity, and server production time
And it is distributed through the existing stream to authorized organizers and assigned staff
But a synchronization that inserts no new record produces no additional event
And requesting the summary produces no event
```

_Satisfies: FR-29, FR-32–FR-34; confirms the dependency contracts rather than introducing a new producer._

## 8. Out of Scope

- Full attendance listing, cursor traversal, caller-selected preview size/order, filtering, searching, or exporting.
- Student profile enrichment, attendance detail views, staff/device identity exposure, and ticket/QR content.
- Attendance scanning, reconciliation decisions, online check-in, invalidation, reinstatement, registration changes, and workshop lifecycle changes.
- A new worker, materialized summary store, dedicated live connection, expanded attendance-event payload, durable event replay, or cross-datasource snapshot guarantee.
- Web/mobile UI, rendering, refresh scheduler implementation, and offline queue changes. Their consumption obligations are documented, not implemented here.
- Reopening completed dependency features as new business capabilities. The already-confirmed implementation corrections concerning event timestamp representation, assignment-provisioning freshness, and the registration-summary timestamp fallback remain inputs to the later task plan; their governing requirements stay in their existing feature specifications.

## 9. Linked References

| Source                                                       | Relevant sections and purpose                                                                                                                                             |
| :----------------------------------------------------------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `docs/feature-catalog.md`                                    | §6.3 and Appendix A: readiness, ownership, fields, access, calculation, preview, recovery, dependency boundaries; §6.2 and §10.5: event production and transport.         |
| `docs/overview.md`                                           | Objectives, roles, on-site check-in, and concurrency goals.                                                                                                               |
| `docs/system-design.md`                                      | §4 Offline Check-in & Synchronization; Read-Only Operational Reporting Flow; §5 RBAC; §6 routing, admission tiers, realtime distribution and recovery.                    |
| `docs/adrs.md`                                               | ADR-005 authentication, ADR-006 resource scope, ADR-010 failure policies, ADR-015 consistency routing, ADR-024 reconciliation authority, ADR-031 transient live delivery. |
| `docs/database-schema.md`                                    | Workshops, assignments, registrations, attendances, reporting semantics/indexing, and replication/read-after-write requirements.                                          |
| `docs/project-structure.md`                                  | Module public boundaries, composed read workflows, and ownership of business data.                                                                                        |
| `docs/api-standards.md`                                      | Representation conventions, bounded previews, errors, UTC timestamps, and stream recovery. Concrete network design is defined in the companion API contract.              |
| `docs/system-architecture.md`                                | Backend/replica/live-distribution topology.                                                                                                                               |
| `specs/attendance-sync-reconciliation/SPEC.md`               | FR-25–FR-46, BR-07–BR-09, reliability: persisted/retry semantics, timestamp provenance, event trigger, and recovery.                                                      |
| `specs/real-time-event-streaming-hub/SPEC.md`                | Scoped routing, transient delivery, and reconnect behavior.                                                                                                               |
| `specs/workshop-staff-assignment/SPEC.md`                    | Explicit assignments and their lifecycle.                                                                                                                                 |
| `specs/mobile-offline-checkin/SPEC.md`                       | Primary-backed provisioning and its separation from attendance reporting.                                                                                                 |
| `specs/free-workshop-registration/SPEC.md`                   | Meaning of confirmed free registrations.                                                                                                                                  |
| `specs/registration-reconciliation-and-cancellation/SPEC.md` | Registration status changes and late payment recovery.                                                                                                                    |
| `specs/workshop-registration-monitoring-and-roster/SPEC.md`  | Existing occupancy denominator and reporting conventions; distinct from attendance percentage.                                                                            |
