# UniHub Workshop — Client Capability Planning

> **Status:** APPROVED<br>
> **Revision:** 1<br>
> **Approval Date:** 2026-11-11

## Scope and Approval

This guide defines the approved capability planning rules and dependency semantics. The platform experience maps and delivery catalogs identify concrete capabilities. Platform roles are confirmed: web supports visitors, students, organizers and a limited staff portal; mobile supports staff only. Platform experience maps and delivery catalogs record their own explicit scope, inventory/order and journey approvals. Those approvals do not approve a UI baseline, capability implementation bundle or completed implementation.

## Feature Size and Ownership

A client capability delivers one coherent user outcome with an entry point, actionable state transitions and a reviewable end state. Several screens can belong to it; a button, dialog, validation schema, data adapter or API endpoint is not normally a feature. A source module may implement multiple capabilities. Discovery/search/detail form one browsing outcome; workshop authoring and publishing are separate because draft authoring and public release have distinct outcomes/constraints. Paid registration and offline capture/sync are larger capabilities requiring vertical tasks, not one giant implementation task.

`M` means a bounded journey with a few related views/states. `L` means a coherent journey with substantial transactional, asynchronous or device-state behavior; its task plan must split the work into verifiable slices before build. Sizes are relative planning indicators, not delivery-time estimates. No capability requires implementing a whole server domain.

## Dependency Semantics

- **Hard prerequisite:** a listed capability supplies an interface, view or behavior the dependent journey uses. Dependency output revisions must be approved and the required outputs implemented/verified for the planned slice. A whole capability need not be human-accepted if only an approved, verified output is required; never claim the entire prerequisite passed when evidence is partial.
- **Suggested sequence:** the catalog order is a topological implementation recommendation and may serialize otherwise independent work to reduce context switching. Adjacent rows do not imply a dependency.
- **Fixture/provider relationship:** shared fixture data, a server feature or another platform's UI is not a hard client mock prerequisite. Roster can use registrations without completed booking UI; assigned workshops can use assignments without completed web administration.
- **Common gates:** scope/journeys, app tooling and platform UI foundation precede dependent capability build; each capability needs its approved spec/prototype/task bundle. Integration additionally needs its mock acceptance, approved contract and integration plan.

## Lifecycle, Readiness and Check Results

These are separate dimensions; neither a blocked prerequisite nor a failed test is an approval decision.

| Lifecycle              | Meaning                                                           | Required Record                                                                              |
| ---------------------- | ----------------------------------------------------------------- | -------------------------------------------------------------------------------------------- |
| `DRAFT`                | Scope or the implementation bundle is being prepared              | Draft revisions; approvals pending                                                           |
| `APPROVED`             | Human approved the implementation dependency bundle for this mode | Scope, foundation and compatible bundle revisions; integration prerequisites when applicable |
| `IN_PROGRESS`          | Authorized implementation or correction is running                | Task scope and current revision                                                              |
| `VERIFICATION_PENDING` | Implementation awaits required checks or review evidence          | Explicit remaining checks/owners; no completion claim                                        |
| `VERIFIED`             | Every required technical check for the declared revision passed   | Fresh commands/results, review disposition and target evidence                               |
| `ACCEPTED`             | Human accepted that exact scope/mode/revision                     | Explicit decision and required UX/native evidence                                            |

Normal progression is `DRAFT → APPROVED → IN_PROGRESS → VERIFICATION_PENDING → VERIFIED → ACCEPTED`; checks may finish within build so a separate pending interval is not mandatory. Required failing checks or implementation findings block `VERIFIED` and acceptance; in-scope correction returns work to `IN_PROGRESS`. Required human design/UX acceptance is separate from technical review. A missing mandatory native check is pending unless human review supplies that check's evidence. No unresolved acceptance-blocking finding can be hidden by a passing command.

A material dependency change creates a new proposed bundle revision in `DRAFT`; preserve prior verified/accepted records rather than rewriting history. Assess affected consumers and stop their dependent work until compatible revisions are approved and required outputs reverified. A routine code fix preserving approved requirements does not invalidate product approval, but requires fresh verification of the changed code.

Readiness is evaluated for the requested build slice against all prerequisites. Record every concrete blocker and display the first unmet gate below; the order is a presentation priority, not a requirement to postpone independent planning.

| Readiness                  | Unmet Gate                                                                               |
| -------------------------- | ---------------------------------------------------------------------------------------- |
| `WAITING_SCOPE`            | Platform journey/capability scope is not approved                                        |
| `WAITING_TOOLING`          | Required app setup or execution capability is unavailable                                |
| `WAITING_FOUNDATION`       | Applicable UI baseline/foundation prototype is not approved                              |
| `WAITING_BUNDLE`           | Compatible spec/prototype/mock task bundle is not approved                               |
| `WAITING_DEPENDENCIES`     | Named prerequisite outputs are missing, incompatible or unverified for this slice        |
| `WAITING_MOCK_ACCEPTANCE`  | Integration scope lacks the applicable human mock acceptance                             |
| `WAITING_CONTRACT`         | Integration lacks approved compatible interface requirements                             |
| `WAITING_INTEGRATION_PLAN` | Integration mappings/tasks/environment/verification plan is not approved                 |
| `READY_FOR_BUILD`          | All applicable gates and prerequisite outputs are satisfied for the requested mode/slice |

Unavailable native automation is not a blanket tooling blocker when the approved human-review fallback and code checks are available. Implement the authorized code slice and retain required native evidence as `VERIFICATION_PENDING`; human review must supply that evidence before the relevant acceptance.

The final three waiting states apply only to `INTEGRATION`; its approved mock bundle remains a baseline and the integration plan supplies new implementation tasks. Readiness does not assert technical completion or start another task. Optional checks do not block work, but cannot be relabelled optional simply because they fail or cannot run. Discovery and configuration-only work follow their own authorized scope; they do not require product prototypes first.

Individual check results are `PASS`, `FAIL`, `NOT_RUN` or `BLOCKED`. Record actual result, revision, target and evidence; `NOT_RUN` means not attempted, `BLOCKED` names the unavailable prerequisite. Human decisions are pending, approved/accepted or rejected for the named artifact/scope; never infer them from lifecycle text, tests or Git commits.

Delivery records are keyed by capability ID and mode, with explicit revision history. Start inventory rows at `DRAFT`; no integration rows are created until integration work is scoped. `MOCK` and `INTEGRATION` retain separate verification and acceptance. Server `READY`/`DONE` do not describe client readiness. Detailed artifacts are linked only when they exist.

## Shared Platform Foundations

App shell/navigation/access fallbacks, public config, error/loading/empty/rate-limit feedback, accessibility primitives, deterministic scenario reset, typed service seams and applicable real-time transport are foundation/task concerns. They do not create standalone product feature rows. Event-driven seat/payment/attendance views reconcile owned state; realtime transport is not a substitute for durable outcomes. Workshop counts are advisory and provider registration outcomes determine allocation.

## Scope Exclusions

No infrastructure/DevOps dashboards, manual CSV upload/scheduling, staff/student self-provisioning outside approved account flows, seat-map booking, new role powers, bank settlement/refund operations, mobile student/organizer workflows or web camera/offline attendance queue. Existing location/room and speaker information is content, not an interactive seating system. Scheduled sweepers, webhooks and worker delivery remain provider responsibilities, represented by client-visible states where relevant.

## Decisions Reserved for Capability Specifications

Offline authorization/key expiry and device-clock behavior, pending queue handling on logout/revocation, notification retention while offline, allowed payment/cancellation consequences and visual direction must be specified from approved requirements before affected implementation. This inventory does not invent those policies or claim existing APIs cover all future client-model needs. Material conflicts require human resolution; the affected spec/build remains blocked until then.

## Delivery Sequence and Platform Acceptance

Each platform has independent discovery, foundation, capability implementation and acceptance. A task targets its declared platform; work on one does not invoke work on the other. There are no cross-platform mock build edges, and client mock work is not gated on server delivery status.

Within each platform, run foundation/tooling before capability implementation. Follow the catalog order initially; ready independent branches can be reordered with the same approved dependencies. Web first establishes access/discovery, then student journeys and organizer operations, followed by complementary notifications, AI and report journeys. Mobile first establishes staff access/assignment/preparation, then the durable capture/sync loop and supporting account/notification/status journeys.

Before `all-mock` acceptance, cover every scoped capability and the experience map's composed journeys, including access recovery, mutation outcomes and cross-role/session isolation. Confirm notification/deep links and back navigation once their target capabilities exist. This is an acceptance milestone, not an extra product feature. No integration or production readiness is inferred from a completed mock platform.

## Capability Completeness Review

Each capability spec identifies entry/exit, actor/tenant boundary, model/service owner, prerequisite outputs, failure/recovery scenarios and code versus human verification. Capability count is not a completeness metric. Cross-cutting acceptance covers:

- **Auth/onboarding/recovery:** temporary/inactive accounts, expired/reused challenges, rate limits, expiry/revocation and return-to-intent. Provisioning is distinct from student self-registration.
- **Discovery/booking/tickets/payments:** filtering/pagination, unpublished/unavailable events, eligibility, changing capacity, duplicate actions, pending/failure/reconciliation and cancellation restrictions. Payment UI cannot manufacture confirmed tickets.
- **Organizer work:** organization isolation, draft/media validation, publishing constraints, schedule/cancellation consequences, staff eligibility and roster privacy. Manual authoring works without successful AI processing.
- **Reports/notifications:** read state, safe links, inaccessible or expired report artifacts, missing target data and reconnect recovery. No manual CSV upload or infrastructure controls.
- **Native attendance:** permission denial/recovery, invalid/wrong-workshop/duplicate scans, stale offline data, durable queue, partial/repeated sync, relaunch and local-versus-confirmed totals.

These are scenario families, not new capabilities or changes to approved business rules. Resolve exact policies and genuine conflicts before dependent code. Platform acceptance rechecks composed journeys after the participating capabilities exist; isolated feature tests alone are insufficient.
