# Client Rules — Web and Mobile

## Scope and Dependencies

Use product requirements, platform experience map and ADRs, the approved platform `ui-design.md`, and the capability bundle. Use `docs/product-overview.md` and `docs/product-features.md` for product scope, then the matching platform `source-structure.md` and client artifacts. Server catalog/design are optional provider references; server readiness/DONE does not describe client progress or block client discovery. Do not import server entities, DTOs or internal packages.

Client models and service interfaces own UI state/data needs. Implement deterministic resettable service fakes/scenarios first; HTTP adapters map the approved public contract only during `client-integrate`. Mock requests must not leak to the live backend. Scenario controls for review belong outside the product UI. Do not invent a public endpoint from a fake, or silently add roles, money flows or business transitions.

## Source Boundaries

Follow the platform source guide: web uses Vite `src/main.tsx` with `src/app/` composition; mobile reserves `src/app/` for Expo Router routes/layouts and uses `src/bootstrap/` for composition. Feature modules own cohesive product responsibilities and may implement multiple capabilities. Plans map capability IDs to source owners; do not split modules by role, screen or endpoint. UI uses injected feature service contracts; mock/HTTP/local adapters remain behind that boundary. Shared primitives do not import features or composition.

## Approval and Execution

Use `.agents/workflows/client.md`. Approve scope/journeys, platform foundation, and the coherent capability spec/prototype/task bundle before dependent production implementation. Within that approved scope, complete implement/run/inspect/test/review/fix/verify autonomously. A task-id narrows the requested scope; it does not grant permission to start another task outside it. Material spec/UI/scope changes require an amendment/impact review before dependent code.

Keep human acceptance separate from technical completion. Mock acceptance stops at the approved mock baseline; real API integration requires its own invocation, approved contract and integration plan. Standalone review writes only its declared findings/evidence, without editing application code or approved dependencies; an authorized build/integration caller may route in-scope implementation defects to `client-fix`.

## UI Authority

Web uses `docs/client/web/ui-design.md`; mobile uses `docs/client/mobile/ui-design.md`. Foundation drafts these files with a representative interactive prototype; never fill in or mark a product baseline approved during infrastructure setup. Capability artifacts reference rule IDs/revision and add only local requirements. Reuse existing rules/components before adding one. Promote reusable patterns to the matching app baseline after approval; scoped exceptions need explicit ownership/approval. Update approved rules at their source, assess dependent prototypes/components/capabilities/visual golden files, and avoid duplicate/conflicting rules. Web changes do not automatically change mobile.

HTML prototypes are design artifacts separate from React/React Native application code. Show real navigation, controls and relevant validation/loading/empty/error/success/permission/offline states. Keep product copy user-facing and omit implementation explanations.

## Testing and Verification

Server unit-only policy does not apply to client. Use Red-Green-Refactor for testable validation, state transitions, queues, retry/mapping logic and behavior fixes. Exploratory prototypes, pure styling and config changes do not require a test per line. Select unit/component, client-internal integration, mock E2E, visual and native checks by risk and observable acceptance criteria; do not add tests that merely mirror CSS or implementation details.

Fixtures/time/state are deterministic and resettable. Include relevant failure/empty/loading/permission/offline/retry conditions, not only happy paths. Mock E2E proves client behavior for a simulated scenario, not provider correctness. Component tests are not native render/device evidence. Persistent tests need real assertions; a screenshot or successful click does not replace them. Do not heal failures by changing approved behavior, skipping regressions or overwriting golden screenshots.

Read the actual installed versions before applying runner/framework APIs. Record checks/commands/results, viewport/device, scenario, baseline revision and pending evidence. Never report a check that did not run as passing. Technical review covers correctness, design, security, performance, test coverage and style under this app's policy.

## TypeScript and Security

Configure client TypeScript with strict: true. No any without an explicit justifying comment; prefer discriminated unions over optional-field-heavy state types. Use clear cohesive functions, immutable defaults and the selected stack; do not add dependencies or architecture patterns merely because a skill example uses them. Java Lombok/MapStruct/layer rules are server-specific.

Treat client bundles/env as public. Apply `security-review` only to relevant client concerns (untrusted input/URLs/content, auth/payment UI, secrets, sensitive local data and dependencies). Storage/cookies/CSRF follow the approved auth model/contract; role UI is not a server authorization boundary. Native storage/deep-link/SQLite/permission details require the actual RN/Expo/library documentation. Do not change BE/infra, enable cloud services or run audit fix/update from a generic security example.

## Refactor and Git

For a real readability opportunity on covered green code, invoke `code-simplification` with this approved scope/test command in workflow mode. No HTML report, template loading to produce that report, save-location prompt or duplicate routine-refactor gate. Re-verify observable behavior and include evidence in the caller summary. Use shared Git delivery rules without committing other user changes or ignored evidence.

## Delivery Records

The platform `feature-delivery.md` owns capability progress. Record mode, approved revisions, technical status and explicit human acceptance separately; use the lifecycle/readiness/check definitions in `docs/client/feature-planning.md`. The catalog records those definitions for the declared scope/mode/revision; artifact approval, capability implementation and per-check status remain separate.
