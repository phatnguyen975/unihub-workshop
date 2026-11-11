# Web Experience Map

> **Status:** APPROVED<br>
> **Revision:** 1<br>
> **Approval:** Human-approved web scope, journeys and screen/state inventory on 2026-11-11, aligned with delivery inventory revision 1. UI baseline and capability implementation bundles require separate approval.

## Product References and Roles

[Product Overview](../../product-overview.md), [Product Features](../../product-features.md), [Client Decisions](../adrs/README.md) and [Client Capability Planning](../feature-planning.md) define scope and interpretation. Source ownership follows [Source Structure](source-structure.md). Contracts are provider references; they do not replace the client requirement/design process.

Visitors browse public workshops/details. Students activate their own eligible account, register/pay and use tickets. Organizers manage only their organization and workshops, members, staff assignments, roster/attendance, brochures and permitted ingestion reports. Staff use web only for assigned-workshop information, account/security and notifications; scanning/capture/sync occur on mobile.

## Scope and Exclusions

Cover the full scoped product experience through the 22 capabilities in the delivery catalog. No infra UI, CSV upload, independent refund console, interactive seat selection or browser attendance scanner. Supporting navigation/error/feedback UI belongs to the owning journey, not extra feature rows.

## Priority Journeys and Navigation

| Journey ID | Actor              | Entry, Flow and Reviewable Outcome                                                                                            | Capability Sequence                                                                                                            |
| ---------- | ------------------ | ----------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| WJ-01      | Visitor/Student    | Public catalog → filters/search → detail → free registration → confirmed wallet ticket; login/activation resumes intent       | `web-workshop-discovery` → `web-access`, `web-student-onboarding` → `web-free-registration` → `web-ticket-wallet`              |
| WJ-02      | Student            | Workshop → reserve/pay → action/redirect/pending recovery → canonical result/ticket; no duplicate charge                      | `web-workshop-discovery` → `web-paid-registration` → `web-ticket-wallet`                                                       |
| WJ-03      | Student            | Wallet/history → inspect registration/payment → permitted cancellation or status recovery → consistent ticket/status          | `web-ticket-wallet` → `web-registration-cancellation`, `web-payment-history`                                                   |
| WJ-04      | Organizer          | Login/temporary-password change → create/edit draft → review → publish → permitted update/reschedule/cancel                   | `web-access` → `web-workshop-authoring` → `web-workshop-publishing` → `web-workshop-rescheduling`, `web-workshop-cancellation` |
| WJ-05      | Organizer          | Member provisioning → workshop selection → staff assignment; assigned staff sees permitted workshop information               | `web-member-administration` + `web-workshop-publishing` → `web-staff-assignment`; `web-staff-workshops` is a separate consumer |
| WJ-06      | Organizer          | Published workshop → registration roster → attendance monitoring → stale/reconnect recovery                                   | `web-workshop-publishing` → `web-registration-roster` → `web-attendance-monitoring`                                            |
| WJ-07      | Organizer          | Draft brochure → summary request → processing/failure → review/apply suggestion                                               | `web-workshop-authoring` → `web-brochure-summary`                                                                              |
| WJ-08      | Organizer          | Reports entry or report notification → scoped job report list/detail → authorized artifact download or expiry/access recovery | `web-access` → `web-ingestion-reports`; `web-notifications` is an optional entry                                               |
| WJ-09      | Authenticated Role | Profile/password/sessions or notification → safe permitted destination; expiry returns to access                              | `web-access` → `web-account-settings`/`web-notifications`                                                                      |
| WJ-10      | Staff              | Login → assigned workshop list/detail → continue check-in on mobile; web has no queue/scanner                                 | `web-access` → `web-staff-workshops`                                                                                           |

Navigation uses a public catalog, student discovery/tickets/payments, organizer workshop/member/operational entrypoints and a limited staff assigned-workshop area. Account and notification entrypoints are available to their authorized roles. Root role pages may summarize available actions; new analytics are not implied.

Journey references describe participating capabilities, not additional hard dependency edges. The delivery catalog owns build dependencies; optional notification entry points do not add blockers.

## Screen and State Inventory

Screen IDs below name logical views, not mandated routes or separate features. Related dialogs and detail states remain within the capability. Reuse global loading/error/access UI while retaining scenario-specific outcomes.

| Screen ID | Capability                      | Logical Views                         | Required State Families                                                                                                                                                        |
| --------- | ------------------------------- | ------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| WS-01     | `web-access`                    | Access and Role Navigation            | Initial, loading, empty where applicable, valid/invalid input, success, recoverable failure, access/expiry                                                                     |
| WS-02     | `web-workshop-discovery`        | Workshop Discovery and Details        | Initial, loading, empty where applicable, valid/invalid input, success, recoverable failure, access/expiry                                                                     |
| WS-03     | `web-student-onboarding`        | Student Registration and Verification | Initial, loading, empty where applicable, valid/invalid input, success, recoverable failure, access/expiry                                                                     |
| WS-04     | `web-account-recovery`          | Account Recovery                      | Initial, loading, empty where applicable, valid/invalid input, success, recoverable failure, access/expiry                                                                     |
| WS-05     | `web-account-settings`          | Profile and Account Security          | Initial, loading, empty where applicable, valid/invalid input, success, recoverable failure, access/expiry                                                                     |
| WS-06     | `web-ticket-wallet`             | Registrations and QR Tickets          | Initial, loading, empty where applicable, valid/invalid input, success, recoverable failure, access/expiry                                                                     |
| WS-07     | `web-free-registration`         | Free Workshop Registration            | Initial, loading, empty where applicable, valid/invalid input, success, recoverable failure, access/expiry                                                                     |
| WS-08     | `web-paid-registration`         | Paid Workshop Registration            | Initial, loading, empty where applicable, valid/invalid input, success, recoverable failure, access/expiry; payment action, pending, timeout/retry, conflict and final outcome |
| WS-09     | `web-registration-cancellation` | Registration Cancellation             | Initial, loading, empty where applicable, valid/invalid input, success, recoverable failure, access/expiry; eligibility, impact confirmation, rejected transition              |
| WS-10     | `web-payment-history`           | Payment History and Status            | Initial, loading, empty where applicable, valid/invalid input, success, recoverable failure, access/expiry                                                                     |
| WS-11     | `web-member-administration`     | Organization Member Administration    | Initial, loading, empty where applicable, valid/invalid input, success, recoverable failure, access/expiry                                                                     |
| WS-12     | `web-workshop-authoring`        | Workshop Draft Authoring              | Initial, loading, empty where applicable, valid/invalid input, success, recoverable failure, access/expiry                                                                     |
| WS-13     | `web-workshop-publishing`       | Workshop Publishing and Updates       | Initial, loading, empty where applicable, valid/invalid input, success, recoverable failure, access/expiry; eligibility, impact confirmation, rejected transition              |
| WS-14     | `web-workshop-rescheduling`     | Workshop Rescheduling                 | Initial, loading, empty where applicable, valid/invalid input, success, recoverable failure, access/expiry                                                                     |
| WS-15     | `web-workshop-cancellation`     | Workshop Cancellation                 | Initial, loading, empty where applicable, valid/invalid input, success, recoverable failure, access/expiry; eligibility, impact confirmation, rejected transition              |
| WS-16     | `web-staff-assignment`          | Workshop Staff Assignment             | Initial, loading, empty where applicable, valid/invalid input, success, recoverable failure, access/expiry                                                                     |
| WS-17     | `web-registration-roster`       | Organizer Registration Roster         | Initial, loading, empty where applicable, valid/invalid input, success, recoverable failure, access/expiry                                                                     |
| WS-18     | `web-attendance-monitoring`     | Organizer Attendance Monitoring       | Initial, loading, empty where applicable, valid/invalid input, success, recoverable failure, access/expiry                                                                     |
| WS-19     | `web-staff-workshops`           | Staff Assigned Workshop Portal        | Initial, loading, empty where applicable, valid/invalid input, success, recoverable failure, access/expiry                                                                     |
| WS-20     | `web-notifications`             | In-App Notification Inbox             | Initial, loading, empty where applicable, valid/invalid input, success, recoverable failure, access/expiry                                                                     |
| WS-21     | `web-brochure-summary`          | AI Brochure Summary Review            | Initial, loading, empty where applicable, valid/invalid input, success, recoverable failure, access/expiry                                                                     |
| WS-22     | `web-ingestion-reports`         | Student Ingestion Reject Reports      | Initial, loading, empty where applicable, valid/invalid input, success, recoverable failure, access/expiry                                                                     |

Capability specs/prototypes expand these families into named acceptance scenarios and justify inapplicable states. Do not show meaningless empty states for login forms or claim native verification from this inventory.

## Capabilities and Dependencies

The [Delivery Catalog](feature-delivery.md#delivery-index) is the single inventory for stable IDs, suggested order, size, direct prerequisites and lifecycle/readiness. Its reverse-edge table identifies blocking consumers. Capabilities sharing an organization, ticket, event or scenario fixture are not automatically implementation dependencies.

## Cross-Capability Verification

Use coordinated fixtures for existing accounts, workshops, tickets, payments, assignments and event outcomes. Product mutations update one owned state projection; session/role changes do not leak another user's data. Test rate-limit/server-unavailable feedback, reconnect and repeated actions in the relevant journeys. No UI implementation or provider integration has been verified by discovery.

## Decisions Requiring Human Input

The visitor/student/organizer/limited-web-staff and staff-only-mobile role boundaries are confirmed. Full scoped inventory rather than an MVP-only cut is requested. The web capability split, order, dependencies and journey inventory are approved at revision 1. UI direction, library choices and capability-specific policy ambiguities are resolved at their owning gates before dependent implementation; see the planning guide for reserved decisions.
