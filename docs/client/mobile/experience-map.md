# Mobile Experience Map

> **Status:** APPROVED<br>
> **Revision:** 1<br>
> **Approval:** Human-approved mobile scope, journeys and screen/state inventory on 2026-11-11, aligned with delivery inventory revision 1. UI baseline and capability implementation bundles require separate approval.

## Product References and Roles

[Product Overview](../../product-overview.md), [Product Features](../../product-features.md), [Client Decisions](../adrs/README.md) and [Client Capability Planning](../feature-planning.md) define scope and interpretation. Source ownership follows [Source Structure](source-structure.md). Contracts are provider references; they do not replace the client requirement/design process.

Only provisioned STAFF use the mobile app. Student/organizer credentials receive explicit unsupported-role feedback. Recovery is a handoff to the web recovery journey; initial temporary-password change remains in mobile access.

## Scope and Exclusions

Cover staff access/security, assigned workshops, persistent offline preparation/capture/sync, assigned attendance status and notifications. No account self-registration, workshop booking/payment, organization management or infrastructure UI.

## Priority Journeys and Navigation

| Journey ID | Actor | Entry, Flow and Reviewable Outcome                                                                                                          | Capability Sequence                                                    |
| ---------- | ----- | ------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------- |
| MJ-01      | Staff | Login → temporary-password change if required → assigned workshop selection                                                                 | `mobile-access` → `mobile-assigned-workshops`                          |
| MJ-02      | Staff | Select assignment → download required data/keys → inspect readiness or actionable preparation failure                                       | `mobile-assigned-workshops` → `mobile-offline-preparation`             |
| MJ-03      | Staff | Ready workshop → camera permission → QR verification → durable local capture → clear pending feedback, including duplicates/invalid tickets | `mobile-offline-preparation` → `mobile-ticket-capture`                 |
| MJ-04      | Staff | Pending capture → network recovery/manual retry → replay same identity → reconciled per-item result, including app restart                  | `mobile-ticket-capture` → `mobile-attendance-sync`                     |
| MJ-05      | Staff | Selected workshop → inspect pending/local versus confirmed status → refresh/reconnect without double counting                               | `mobile-attendance-sync` → `mobile-attendance-monitoring`              |
| MJ-06      | Staff | Notification → permitted assigned workshop; unavailable connection remains visible                                                          | `mobile-access` + `mobile-assigned-workshops` → `mobile-notifications` |
| MJ-07      | Staff | Own profile/password/sessions → confirmed result or expiry recovery; logout does not invent unsent-data policy                              | `mobile-access` → `mobile-account-settings`                            |

Navigation starts at access or restored staff context, then assigned workshops. Selected-workshop context owns preparation, capture, queue and attendance-status destinations; account and notifications are secondary. Offline queue/error states must remain visible outside the camera view. Foreground/relaunch resumes verified local state, not a second in-memory queue.

Journey references describe participating capabilities, not additional hard dependency edges. The delivery catalog owns build dependencies; optional notification entry points do not add blockers.

## Screen and State Inventory

Screen IDs below name logical views, not mandated routes or separate features. Related dialogs and detail states remain within the capability. Reuse global loading/error/access UI while retaining scenario-specific outcomes.

| Screen ID | Capability                     | Logical Views                       | Required State Families                                                                                                                                           |
| --------- | ------------------------------ | ----------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| MS-01     | `mobile-access`                | Staff Access and Session            | Initial, loading, empty where applicable, valid/invalid input, success, recoverable failure, access/expiry                                                        |
| MS-02     | `mobile-account-settings`      | Staff Profile and Security          | Initial, loading, empty where applicable, valid/invalid input, success, recoverable failure, access/expiry                                                        |
| MS-03     | `mobile-assigned-workshops`    | Assigned Workshop Selection         | Initial, loading, empty where applicable, valid/invalid input, success, recoverable failure, access/expiry; offline/stale and permission/lifecycle where relevant |
| MS-04     | `mobile-offline-preparation`   | Offline Readiness and Preparation   | Initial, loading, empty where applicable, valid/invalid input, success, recoverable failure, access/expiry; offline/stale and permission/lifecycle where relevant |
| MS-05     | `mobile-ticket-capture`        | Offline QR Attendance Capture       | Initial, loading, empty where applicable, valid/invalid input, success, recoverable failure, access/expiry; offline/stale and permission/lifecycle where relevant |
| MS-06     | `mobile-attendance-sync`       | Attendance Queue and Reconciliation | Initial, loading, empty where applicable, valid/invalid input, success, recoverable failure, access/expiry; offline/stale and permission/lifecycle where relevant |
| MS-07     | `mobile-attendance-monitoring` | Assigned Workshop Attendance Status | Initial, loading, empty where applicable, valid/invalid input, success, recoverable failure, access/expiry; offline/stale and permission/lifecycle where relevant |
| MS-08     | `mobile-notifications`         | Staff Notification Inbox            | Initial, loading, empty where applicable, valid/invalid input, success, recoverable failure, access/expiry; offline/stale and permission/lifecycle where relevant |

Capability specs/prototypes expand these families into named acceptance scenarios and justify inapplicable states. Do not show meaningless empty states for login forms or claim native verification from this inventory.

## Capabilities and Dependencies

The [Delivery Catalog](feature-delivery.md#delivery-index) is the single inventory for stable IDs, suggested order, size, direct prerequisites and lifecycle/readiness. Its reverse-edge table identifies blocking consumers. Capabilities sharing an organization, ticket, event or scenario fixture are not automatically implementation dependencies.

## Cross-Capability Verification

Use coordinated fixtures for existing accounts, workshops, tickets, payments, assignments and event outcomes. Product mutations update one owned state projection; session/role changes do not leak another user's data. Test rate-limit/server-unavailable feedback, reconnect and repeated actions in the relevant journeys. No UI implementation or provider integration has been verified by discovery.

## Decisions Requiring Human Input

The visitor/student/organizer/limited-web-staff and staff-only-mobile role boundaries are confirmed. Full scoped inventory rather than an MVP-only cut is requested. The mobile capability split, order, dependencies and journey inventory are approved at revision 1. UI direction, library choices and capability-specific policy ambiguities are resolved at their owning gates before dependent implementation; see the planning guide for reserved decisions.
