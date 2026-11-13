# Web Feature Delivery

## Scope

This catalog tracks proposed and approved web capability bundles and their implementation, verification and human acceptance. Capability IDs belong to this platform and reference applicable product feature IDs.

## References

- [Product Features](../../product-features.md)
- [Source Structure](source-structure.md)
- [Client Specifications](../../../specs/client/README.md)
- [Client Workflow](../../../.agents/workflows/client.md)
- [Client Templates](../../../.agents/templates/client/README.md)

The platform experience map and UI baseline live in `docs/client/web/experience-map.md` and `ui-design.md`; their revisions are recorded in capability artifacts.

## Lifecycle and Readiness

[Client Capability Planning](../feature-planning.md#lifecycle-readiness-and-check-results) is authoritative for lifecycle, readiness, mode/revision history and check-result meanings. This catalog records values for each capability; it does not redefine them. Scope approval alone does not set a capability to `APPROVED`: implementation also requires its approved dependency bundle.

## Delivery Index

> **Inventory Revision:** 1. **Inventory Status:** APPROVED. **Approval:** Human confirmed the 22 web capabilities, scope, recommended order and dependencies on 2026-11-11, aligned with experience-map revision 1. **Mode:** MOCK. Capability lifecycle remains `DRAFT`; local implementation tooling/code/browser smoke is verified under [Web Tooling](tooling.md). Human approved [Web UI Design](ui-design.md) revision 6 and its foundation prototype revision 6 on 2026-11-12. Readiness is `WAITING_BUNDLE`: each capability still requires compatible spec/prototype/task approval and its declared prerequisites. Platform scope/foundation approval does not approve capability implementation or acceptance.

Dependency and size definitions are owned by [Client Capability Planning](../feature-planning.md). [Experience Map](experience-map.md) owns the journey/screen/state inventory. Order is recommended; only named prerequisites are hard edges. All capabilities also require the common scope/tooling/foundation/bundle gates.

| Order | Capability ID                   | Outcome                               | Size | Hard Prerequisites                                                                  | State   | Readiness        |
| ----- | ------------------------------- | ------------------------------------- | ---- | ----------------------------------------------------------------------------------- | ------- | ---------------- |
| 01    | `web-access`                    | Access and Role Navigation            | M    | None                                                                                | `DRAFT` | `WAITING_BUNDLE` |
| 02    | `web-workshop-discovery`        | Workshop Discovery and Details        | M    | None                                                                                | `DRAFT` | `WAITING_BUNDLE` |
| 03    | `web-student-onboarding`        | Student Registration and Verification | M    | `web-access`                                                                        | `DRAFT` | `WAITING_BUNDLE` |
| 04    | `web-account-recovery`          | Account Recovery                      | M    | `web-access`                                                                        | `DRAFT` | `WAITING_BUNDLE` |
| 05    | `web-account-settings`          | Profile and Account Security          | M    | `web-access`                                                                        | `DRAFT` | `WAITING_BUNDLE` |
| 06    | `web-ticket-wallet`             | Registrations and QR Tickets          | M    | `web-access`                                                                        | `DRAFT` | `WAITING_BUNDLE` |
| 07    | `web-free-registration`         | Free Workshop Registration            | M    | `web-access`, `web-workshop-discovery`, `web-ticket-wallet`                         | `DRAFT` | `WAITING_BUNDLE` |
| 08    | `web-paid-registration`         | Paid Workshop Registration            | L    | `web-access`, `web-workshop-discovery`, `web-ticket-wallet`                         | `DRAFT` | `WAITING_BUNDLE` |
| 09    | `web-registration-cancellation` | Registration Cancellation             | M    | `web-ticket-wallet`                                                                 | `DRAFT` | `WAITING_BUNDLE` |
| 10    | `web-payment-history`           | Payment History and Status            | M    | `web-access`, `web-ticket-wallet`                                                   | `DRAFT` | `WAITING_BUNDLE` |
| 11    | `web-member-administration`     | Organization Member Administration    | M    | `web-access`                                                                        | `DRAFT` | `WAITING_BUNDLE` |
| 12    | `web-workshop-authoring`        | Workshop Draft Authoring              | L    | `web-access`                                                                        | `DRAFT` | `WAITING_BUNDLE` |
| 13    | `web-workshop-publishing`       | Workshop Publishing and Updates       | M    | `web-workshop-authoring`                                                            | `DRAFT` | `WAITING_BUNDLE` |
| 14    | `web-workshop-rescheduling`     | Workshop Rescheduling                 | M    | `web-workshop-publishing`                                                           | `DRAFT` | `WAITING_BUNDLE` |
| 15    | `web-workshop-cancellation`     | Workshop Cancellation                 | M    | `web-workshop-publishing`                                                           | `DRAFT` | `WAITING_BUNDLE` |
| 16    | `web-staff-assignment`          | Workshop Staff Assignment             | M    | `web-member-administration`, `web-workshop-publishing`                              | `DRAFT` | `WAITING_BUNDLE` |
| 17    | `web-registration-roster`       | Organizer Registration Roster         | M    | `web-workshop-publishing`                                                           | `DRAFT` | `WAITING_BUNDLE` |
| 18    | `web-attendance-monitoring`     | Organizer Attendance Monitoring       | M    | `web-workshop-publishing`, `web-registration-roster`                                | `DRAFT` | `WAITING_BUNDLE` |
| 19    | `web-staff-workshops`           | Staff Assigned Workshop Portal        | M    | `web-access`                                                                        | `DRAFT` | `WAITING_BUNDLE` |
| 20    | `web-notifications`             | In-App Notification Inbox             | M    | `web-access`, `web-ticket-wallet`, `web-workshop-publishing`, `web-staff-workshops` | `DRAFT` | `WAITING_BUNDLE` |
| 21    | `web-brochure-summary`          | AI Brochure Summary Review            | M    | `web-workshop-authoring`                                                            | `DRAFT` | `WAITING_BUNDLE` |
| 22    | `web-ingestion-reports`         | Student Ingestion Reject Reports      | M    | `web-access`                                                                        | `DRAFT` | `WAITING_BUNDLE` |

When implementation artifacts exist, add links to their compatible spec/plan/prototype/verification revisions and the explicit human acceptance; do not link to missing files. Add a separate integration record only when integration work is scoped.

## Capability Scope and Traceability

### Access and Role Navigation

**Capability:** `web-access`. **Source Owner:** `identity`.

Visitor entry, role-specific login, initial temporary-password change, session restore/expiry, logout and access/error navigation. No separate dashboard with invented analytics.

**Product References:** `authentication-and-session-management`.

**MOCK Design Record:** Human approved [Capability Spec](../../../specs/client/web/web-access/SPEC.md) revision 1 on 2026-11-14, including login/navigation, tab-scoped synthetic persistence and logout-recovery requirements. Approval covers the specification only. The capability prototype and task plan are not yet prepared; bundle approval and implementation remain pending. Capability lifecycle/readiness stays `DRAFT` / `WAITING_BUNDLE`.

### Workshop Discovery and Details

**Capability:** `web-workshop-discovery`. **Source Owner:** `workshops`.

Public catalog, text/filter search, pagination, workshop schedule/speaker/location/brochure details and available-seat presentation; preserve intended destination when login is needed.

**Product References:** `workshop-catalog-browsing`, `workshop-full-text-search`.

### Student Registration and Verification

**Capability:** `web-student-onboarding`. **Source Owner:** `identity`.

Student registration, directory eligibility feedback, identity verification/OTP and activation/retry outcomes. Staff/organizer self-registration excluded.

**Product References:** `user-registration-and-identity-verification`.

### Account Recovery

**Capability:** `web-account-recovery`. **Source Owner:** `identity`.

Forgot-password request, verification, reset and return to login with anti-enumeration and expired/reused challenge states.

**Product References:** `password-reset`.

### Profile and Account Security

**Capability:** `web-account-settings`. **Source Owner:** `identity`.

Own profile/avatar, authenticated password change, active sessions and session revocation for supported web roles. Temporary-password entry remains owned by access.

**Product References:** `user-profile-management`, `active-session-management`.

### Registrations and QR Tickets

**Capability:** `web-ticket-wallet`. **Source Owner:** `registrations`.

Own registration list/detail, pending/confirmed/cancelled outcomes, QR ticket visibility and workshop reference. Historical fixtures make this usable before booking UIs.

**Product References:** `free-workshop-registration`, `paid-workshop-registration`, `registration-reconciliation-and-cancellation`.

### Free Workshop Registration

**Capability:** `web-free-registration`. **Source Owner:** `registrations`.

From workshop detail, confirm eligible free registration, prevent duplicate submissions, show capacity/conflict/rate-limit outcomes and reach the confirmed ticket.

**Product References:** `free-workshop-registration`.

### Paid Workshop Registration

**Capability:** `web-paid-registration`. **Source Owner:** `payments + registrations`.

Reservation/payment UI, user action/redirect recovery, pending/failure/timeout and canonical final status/ticket; retry safely without duplicate charges. No client-created payment confirmation.

**Product References:** `paid-workshop-registration`, `payment-history-and-status`.

### Registration Cancellation

**Capability:** `web-registration-cancellation`. **Source Owner:** `registrations`.

Inspect cancellation eligibility and consequences, confirm once and reconcile the resulting ticket/registration state; existing paid fixtures cover restrictions without requiring the payment UI.

**Product References:** `registration-reconciliation-and-cancellation`.

### Payment History and Status

**Capability:** `web-payment-history`. **Source Owner:** `payments`.

Own payment list/detail, related registration navigation, pending/failure/reconciled status and empty/error states; no finance administration or unsupported refund actions.

**Product References:** `payment-history-and-status`.

### Organization Member Administration

**Capability:** `web-member-administration`. **Source Owner:** `identity`.

Organization-scoped member list/detail, staff/organizer provisioning and active-status management with confirmation and self/role restrictions. No cross-tenant powers.

**Product References:** `organization-member-management`.

### Workshop Draft Authoring

**Capability:** `web-workshop-authoring`. **Source Owner:** `workshops`.

Organizer workshop list, create/save/edit draft, validation, speakers/schedule/location/capacity/pricing and supported media/brochure attachment. Split tasks by vertical slice, not separate unrelated screen features.

**Product References:** `workshop-creation-and-draft-management`.

### Workshop Publishing and Updates

**Capability:** `web-workshop-publishing`. **Source Owner:** `workshops`.

Review publish readiness, publish and inspect/manage the allowed post-publish fields/transitions. Reuse authoring UI with lifecycle constraints.

**Product References:** `workshop-publishing-and-update`.

### Workshop Rescheduling

**Capability:** `web-workshop-rescheduling`. **Source Owner:** `workshops`.

Review schedule change, affected participants and allowed lifecycle constraints, confirm and display resulting schedule/notification outcome without implementing notification delivery.

**Product References:** `workshop-rescheduling`.

### Workshop Cancellation

**Capability:** `web-workshop-cancellation`. **Source Owner:** `workshops`.

Review event cancellation impact, confirm destructive action and present final lifecycle plus applicable registration/payment consequences; no invented automatic refunds.

**Product References:** `workshop-cancellation`.

### Workshop Staff Assignment

**Capability:** `web-staff-assignment`. **Source Owner:** `workshops`.

List eligible organization staff, assign/unassign to an owned workshop and display eligibility/conflict results. User provisioning and workshop context are prerequisite outputs.

**Product References:** `workshop-staff-assignment`.

### Organizer Registration Roster

**Capability:** `web-registration-roster`. **Source Owner:** `registrations`.

Organization/workshop-scoped roster, registration states, supported filters and totals; respect participant privacy. Use fixtures rather than waiting for student booking UI.

**Product References:** `workshop-registration-monitoring-and-roster`.

### Organizer Attendance Monitoring

**Capability:** `web-attendance-monitoring`. **Source Owner:** `attendance`.

Workshop attendance summary/detail alongside participant context, loading/stale/reconnect outcomes and reconciled counts. No organizer scan controls or device queue editor.

**Product References:** `live-attendance-monitoring`, `attendance-sync-reconciliation`.

### Staff Assigned Workshop Portal

**Capability:** `web-staff-workshops`. **Source Owner:** `workshops`.

STAFF list/detail of assigned workshops, schedule/location and preparation guidance; hand off scanning to mobile. No web camera, offline capture or sync queue.

**Product References:** `mobile-offline-checkin`.

### In-App Notification Inbox

**Capability:** `web-notifications`. **Source Owner:** `notifications`.

Role-scoped inbox, unread/read/read-all behavior and safe navigation to accessible tickets/workshops. Typed notification links may add targets as their capabilities are available.

**Product References:** `in-app-notification-management`.

### AI Brochure Summary Review

**Capability:** `web-brochure-summary`. **Source Owner:** `workshops`.

Request summary for attached brochure, inspect processing/success/failure, review suggested text and apply through allowed draft editing. No automatic overwrite or requirement that all events use AI.

**Product References:** `ai-brochure-summarization`.

### Student Ingestion Reject Reports

**Capability:** `web-ingestion-reports`. **Source Owner:** `operations`.

Authorized organizer lists organization-scoped ingestion job reports, inspects outcomes/rejected-record context and downloads the available reject artifact with expiry/access/error feedback. Notifications provide an alternate entry point. No CSV upload, schedule control or infrastructure administration.

**Product References:** `ingestion-reject-report`.

## Prerequisite Output Records

Before a build task consumes a prerequisite, its task plan names the producer capability, required output (view/service/schema/transition), approved artifact revision and verification evidence. The catalog lists capability-level edges; an entire producer need not be accepted when only one approved, verified output is consumed. Common mock fixtures do not claim implemented provider/UI behavior.

## Blocking Relationships

| Prerequisite                | Directly Blocks                                                                                                                                                                                                                                                                                    |
| --------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `web-access`                | `web-student-onboarding`, `web-account-recovery`, `web-account-settings`, `web-ticket-wallet`, `web-free-registration`, `web-paid-registration`, `web-payment-history`, `web-member-administration`, `web-workshop-authoring`, `web-staff-workshops`, `web-notifications`, `web-ingestion-reports` |
| `web-workshop-discovery`    | `web-free-registration`, `web-paid-registration`                                                                                                                                                                                                                                                   |
| `web-ticket-wallet`         | `web-free-registration`, `web-paid-registration`, `web-registration-cancellation`, `web-payment-history`, `web-notifications`                                                                                                                                                                      |
| `web-member-administration` | `web-staff-assignment`                                                                                                                                                                                                                                                                             |
| `web-workshop-authoring`    | `web-workshop-publishing`, `web-brochure-summary`                                                                                                                                                                                                                                                  |
| `web-workshop-publishing`   | `web-workshop-rescheduling`, `web-workshop-cancellation`, `web-staff-assignment`, `web-registration-roster`, `web-attendance-monitoring`, `web-notifications`                                                                                                                                      |
| `web-registration-roster`   | `web-attendance-monitoring`                                                                                                                                                                                                                                                                        |
| `web-staff-workshops`       | `web-notifications`                                                                                                                                                                                                                                                                                |

Leaf capabilities can still be required for platform acceptance even when they block no other build. Transitive blockers follow the prerequisite chain.

## Verification and Acceptance

Record test commands/results, visual inspection, platform/device, artifact revisions and pending checks in the capability verification artifact. Required native evidence is supplied through proven device access or human review. Acceptance is recorded only after an explicit human decision.

## Mode and Revision Records

Use separate rows for each `(Capability ID, Mode)` pair. The plan column links `tasks.md` for `MOCK` and `integration-plan.md` for `INTEGRATION`. Link mode/revision-labelled verification and acceptance entries; preserve accepted mock evidence when integration begins. Required pending checks use `VERIFICATION_PENDING`, not `VERIFIED` or `ACCEPTED`. An accepted historical revision does not approve changed dependencies.

## Platform Mock Acceptance

When `client-accept all-mock` is requested, record the explicit capability inventory/revisions, cross-capability journey checks, pending evidence and human decision here. Link each capability verification record. Keep this section without an acceptance claim until the human confirms the declared scope.
