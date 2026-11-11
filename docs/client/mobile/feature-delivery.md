# Mobile Feature Delivery

## Scope

This catalog tracks proposed and approved mobile capability bundles and their implementation, verification and human acceptance. Capability IDs belong to this platform and reference applicable product feature IDs.

## References

- [Product Features](../../product-features.md)
- [Source Structure](source-structure.md)
- [Client Specifications](../../../specs/client/README.md)
- [Client Workflow](../../../.agents/workflows/client.md)
- [Client Templates](../../../.agents/templates/client/README.md)

The platform experience map and UI baseline live in `docs/client/mobile/experience-map.md` and `ui-design.md`; their revisions are recorded in capability artifacts.

## Lifecycle and Readiness

[Client Capability Planning](../feature-planning.md#lifecycle-readiness-and-check-results) is authoritative for lifecycle, readiness, mode/revision history and check-result meanings. This catalog records values for each capability; it does not redefine them. Scope approval alone does not set a capability to `APPROVED`: implementation also requires its approved dependency bundle.

## Delivery Index

> **Inventory Revision:** 1. **Inventory Status:** APPROVED. **Approval:** Human confirmed the 8 mobile capabilities, scope, recommended order and dependencies on 2026-11-11, aligned with experience-map revision 1. **Mode:** MOCK. Capability lifecycle remains `DRAFT`; required implementation tooling/verification setup is not established, so readiness is `WAITING_TOOLING`. Foundation and capability bundles are also pending. This is scope approval, not implementation approval or acceptance.

Dependency and size definitions are owned by [Client Capability Planning](../feature-planning.md). [Experience Map](experience-map.md) owns the journey/screen/state inventory. Order is recommended; only named prerequisites are hard edges. All capabilities also require the common scope/tooling/foundation/bundle gates.

| Order | Capability ID                  | Outcome                             | Size | Hard Prerequisites                           | State   | Readiness         |
| ----- | ------------------------------ | ----------------------------------- | ---- | -------------------------------------------- | ------- | ----------------- |
| 01    | `mobile-access`                | Staff Access and Session            | M    | None                                         | `DRAFT` | `WAITING_TOOLING` |
| 02    | `mobile-assigned-workshops`    | Assigned Workshop Selection         | M    | `mobile-access`                              | `DRAFT` | `WAITING_TOOLING` |
| 03    | `mobile-offline-preparation`   | Offline Readiness and Preparation   | M    | `mobile-assigned-workshops`                  | `DRAFT` | `WAITING_TOOLING` |
| 04    | `mobile-ticket-capture`        | Offline QR Attendance Capture       | L    | `mobile-offline-preparation`                 | `DRAFT` | `WAITING_TOOLING` |
| 05    | `mobile-attendance-sync`       | Attendance Queue and Reconciliation | L    | `mobile-ticket-capture`                      | `DRAFT` | `WAITING_TOOLING` |
| 06    | `mobile-attendance-monitoring` | Assigned Workshop Attendance Status | M    | `mobile-attendance-sync`                     | `DRAFT` | `WAITING_TOOLING` |
| 07    | `mobile-account-settings`      | Staff Profile and Security          | M    | `mobile-access`                              | `DRAFT` | `WAITING_TOOLING` |
| 08    | `mobile-notifications`         | Staff Notification Inbox            | M    | `mobile-access`, `mobile-assigned-workshops` | `DRAFT` | `WAITING_TOOLING` |

When implementation artifacts exist, add links to their compatible spec/plan/prototype/verification revisions and the explicit human acceptance; do not link to missing files. Add a separate integration record only when integration work is scoped.

## Capability Scope and Traceability

### Staff Access and Session

**Capability:** `mobile-access`. **Source Owner:** `identity`.

STAFF-only login, mandatory initial password change, session restore/expiry/logout and wrong-role handling. Recovery links to the web recovery flow; no self-registration.

**Product References:** `authentication-and-session-management`.

### Assigned Workshop Selection

**Capability:** `mobile-assigned-workshops`. **Source Owner:** `workshops`.

Assigned workshop list/detail, date/location and selected-workshop context with loading/empty/access-loss feedback. Independent of organizer web UI through fixture assignment data.

**Product References:** `mobile-offline-checkin`.

### Offline Readiness and Preparation

**Capability:** `mobile-offline-preparation`. **Source Owner:** `workshops + attendance`.

Obtain/cache required assignment and verification data, check readiness/key freshness, show last successful preparation and download/refresh failure. Persist actual local data while mocking the provider.

**Product References:** `mobile-offline-checkin`, `public-key-distribution`.

### Offline QR Attendance Capture

**Capability:** `mobile-ticket-capture`. **Source Owner:** `attendance`.

Camera permission/recovery, scan and verify ticket against selected workshop, reject invalid/wrong-workshop/duplicate cases, atomically persist capture with stable identity and show locally recorded/pending feedback, not server-confirmed attendance.

**Product References:** `mobile-offline-checkin`.

### Attendance Queue and Reconciliation

**Capability:** `mobile-attendance-sync`. **Source Owner:** `attendance`.

Inspect durable pending/result records, sync/retry safely, reconcile accepted/duplicate/invalid/failure results and recover from network interruption/relaunch without losing captures or changing replay identity.

**Product References:** `attendance-sync-reconciliation`.

### Assigned Workshop Attendance Status

**Capability:** `mobile-attendance-monitoring`. **Source Owner:** `attendance`.

Assigned-workshop status with explicitly separate local pending and provider-confirmed totals, stale/offline state and refresh/live reconnect feedback. No organization-wide dashboard.

**Product References:** `live-attendance-monitoring`, `attendance-sync-reconciliation`.

### Staff Profile and Security

**Capability:** `mobile-account-settings`. **Source Owner:** `identity`.

Own profile/avatar, password change and active-session revocation; account-switch/unsent-data decisions obey approved retention policy, not silent queue deletion.

**Product References:** `user-profile-management`, `active-session-management`.

### Staff Notification Inbox

**Capability:** `mobile-notifications`. **Source Owner:** `notifications`.

Staff-relevant inbox/read state and safe workshop navigation. Explicit offline/unavailable behavior; notification delivery channels are not mobile infrastructure features.

**Product References:** `in-app-notification-management`.

## Prerequisite Output Records

Before a build task consumes a prerequisite, its task plan names the producer capability, required output (view/service/schema/transition), approved artifact revision and verification evidence. The catalog lists capability-level edges; an entire producer need not be accepted when only one approved, verified output is consumed. Common mock fixtures do not claim implemented provider/UI behavior.

## Blocking Relationships

| Prerequisite                 | Directly Blocks                                                                |
| ---------------------------- | ------------------------------------------------------------------------------ |
| `mobile-access`              | `mobile-account-settings`, `mobile-assigned-workshops`, `mobile-notifications` |
| `mobile-assigned-workshops`  | `mobile-offline-preparation`, `mobile-notifications`                           |
| `mobile-offline-preparation` | `mobile-ticket-capture`                                                        |
| `mobile-ticket-capture`      | `mobile-attendance-sync`                                                       |
| `mobile-attendance-sync`     | `mobile-attendance-monitoring`                                                 |

Leaf capabilities can still be required for platform acceptance even when they block no other build. Transitive blockers follow the prerequisite chain.

## Verification and Acceptance

Record test commands/results, visual inspection, platform/device, artifact revisions and pending checks in the capability verification artifact. Required native evidence is supplied through proven device access or human review. Acceptance is recorded only after an explicit human decision.

## Mode and Revision Records

Use separate rows for each `(Capability ID, Mode)` pair. The plan column links `tasks.md` for `MOCK` and `integration-plan.md` for `INTEGRATION`. Link mode/revision-labelled verification and acceptance entries; preserve accepted mock evidence when integration begins. Required pending checks use `VERIFICATION_PENDING`, not `VERIFIED` or `ACCEPTED`. An accepted historical revision does not approve changed dependencies.

## Platform Mock Acceptance

When `client-accept all-mock` is requested, record the explicit capability inventory/revisions, cross-capability journey checks, pending evidence and human decision here. Link each capability verification record. Keep this section without an acceptance claim until the human confirms the declared scope.
