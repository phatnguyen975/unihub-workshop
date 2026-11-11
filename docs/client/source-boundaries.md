# Client Source Boundaries

## Scope

Shared architecture rules for the web and mobile source guides. Each app owns its implementation and platform baseline; these rules describe dependency and data ownership, not a shared source package or selected library stack.

## Feature Ownership

Organize source by cohesive product responsibility, not by screen, role, endpoint or spec file. A capability ID identifies an approved delivery slice; a feature module owns enduring behavior/data and may implement several capabilities. Map each planned capability to its owning module(s) in its task plan. Create a new module only for a distinct responsibility; do not create every folder in this guide up front.

| Responsibility            | Ownership Guidance                                                                                                         |
| ------------------------- | -------------------------------------------------------------------------------------------------------------------------- |
| Identity and Session      | Login/recovery/session UX and session lifecycle; app composition supplies scoped identity/access context to other features |
| Workshops                 | Discovery, details and event administration; role-specific views remain under the owning responsibility                    |
| Registrations and Tickets | Reservation/cancellation/ticket state; keep one owner for each transition and current ticket projection                    |
| Payments                  | Payment initiation/status/history and UI recovery; confirmation follows approved provider outcomes                         |
| Attendance                | Capture, queue, synchronization and attendance results, with relevant organizer/staff views                                |
| Notifications             | In-app inbox/read state and its own event projections                                                                      |

These are allocation guidelines from product scope, not a completed capability inventory or new requirements. Use only the responsibilities needed by the approved platform scope. Organization/user settings, upload and report UI belong to their actual owner; extract separate modules when that responsibility warrants it. Do not mirror server package names or make Student/Organizer/Staff copies of the same mutable data.

## Dependency Rules

Composition imports feature public surfaces and explicitly selected adapters. Features import their own internals and reusable shared primitives; they do not import another feature's internals, another app, or server implementation code. Cross-feature journeys are coordinated by composition through explicit public inputs/results/callbacks. Public services and adapter factories have explicit named exports; keep dependency direction acyclic and use type-only imports where applicable. Avoid an all-features barrel.

Within a feature, UI uses model/service operations or hooks; it does not instantiate HTTP/mock/local adapters. The model owns client types, validation and pure transitions. Services own injectable operation contracts and behavior hooks. Data adapters implement those contracts and may depend on the model and generic shared infrastructure, but not UI. A small feature need not invent classes, repositories and use-case layers for simple rendering or validation. Add service seams for external I/O, durable state or independently testable workflows.

`shared/` imports neither features nor composition/routes. Keep it limited to demonstrated reusable UI/infrastructure; business concepts do not become shared merely because two screens use them. Authentication tokens/session lifecycle retain one feature owner; generic HTTP receives credential/session callbacks from composition rather than importing that feature.

## Client Models, Mocks and Integration

Service interfaces and client models own view data needs. `data/mock/` supplies deterministic asynchronous service fakes with resettable fixtures, latency/failure scenarios and typed outcomes. Composition selects implementations; no component-level mock flags or hardcoded fixture arrays inside views. A platform scenario combines compatible workshop/registration/payment/attendance fixtures under one reset boundary, avoiding contradictory counts and IDs across features. Development scenario controls are excluded from production product flows.

Mock mode must not instantiate live provider adapters. During explicitly approved integration, `data/http/` owns wire DTOs, endpoint-specific validation/mapping, pagination and error translation. Generic HTTP/SSE mechanics belong in `shared/transport/`; global OpenAPI output, when generation is approved, belongs in `shared/transport/generated/` and remains private to adapters. Preserve service fake scenarios for regression; a mock service does not define a public endpoint. Contract gaps block dependent integration and do not authorize provider edits.

## State and Live Updates

Keep form drafts and view-only state local to the screen/feature; put validation schemas beside their owning model or form. Keep remote resource/cache state distinct from drafts and durable local state. Derive computed values and avoid synchronizing duplicate stores for the same entity. Library selection for queries, forms, schema validation and UI tokens follows the package setup plan; this directory layout does not install or require a particular library.

Identify resources by the relevant account/organization/workshop scope. Session/workshop changes cancel or isolate obsolete work and clear sensitive projections as required by approved behavior. In integration mode, composition controls authenticated connection lifetime; shared transport implements mechanics, while owning features interpret events and reconcile projections. A registration/payment/attendance outcome may invalidate another feature's read projection through explicit composition coordination. Preserve idempotency, ordering and reconnect semantics from approved requirements; client visibility/counts are not provider authorization or capacity enforcement.

## Verification and Artifacts

Colocate feature behavior/component tests with their subject. `testing/` contains reusable builders/helpers, not an alternative directory for all tests. Cross-feature journeys and coordinated scenarios live under `tests/`. Map acceptance criteria to model/service/component/journey checks and applicable visual/accessibility/native evidence. Verify mock behavior with fixtures; verify mappings and approved provider journeys separately during integration.

Product design/spec/prototype/approval artifacts remain in `docs/client/<platform>/` and `specs/client/<platform>/<capability>/`. Code folders do not establish approval. Enforce dependency/import boundaries with tooling selected during setup; add fixtures, adapters and test folders when their files are needed. Existing minimal starter entrypoints can remain until the approved foundation/setup work relocates their responsibilities.
