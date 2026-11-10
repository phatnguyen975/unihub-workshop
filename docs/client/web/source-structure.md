# Web Source Structure

> Architecture target for `apps/web/`. The source tree is created during application setup and approved capability implementation. Directory names describe responsibilities; build tools, routing, state libraries, styling and test runners are selected in their setup scope.

## Directory Layout

```text
apps/web/
├── AGENTS.md
├── src/
│   ├── app/                        # Application composition and lifecycle
│   │   ├── entry/                  # Selected framework entry adapters
│   │   ├── navigation/             # Route/screen composition and access UX
│   │   ├── providers/              # Dependency and context providers
│   │   ├── services/               # Select fake or approved HTTP adapters
│   │   └── config/                 # Validated public runtime configuration
│   ├── features/
│   │   └── <capability>/           # Create only for an approved capability
│   │       ├── index.ts            # Explicit public exports
│   │       ├── pages/              # Feature entry views
│   │       ├── ui/                 # Feature controls and compositions
│   │       ├── model/              # Client types, validation and state transitions
│   │       ├── services/           # Feature operations and owned service interfaces
│   │       ├── data/
│   │       │   ├── mock/           # Deterministic service fake and scenarios
│   │       │   └── http/           # Approved contract adapters and wire mappers
│   │       └── testing/            # Reusable feature fixtures/builders
│   ├── shared/
│   │   ├── ui/                     # Approved reusable platform design primitives
│   │   ├── lib/                    # Small reusable platform-independent helpers
│   │   ├── transport/              # HTTP/SSE mechanics, cancellation, typed errors
│   │   ├── platform/               # Browser/native capability adapters
│   │   └── testing/                # Common rendering and scenario-reset helpers
│   └── assets/                     # App-owned fonts, icons and static assets
└── tests/
    ├── journeys/                   # Persistent cross-feature UI acceptance tests
    └── fixtures/                   # Cross-feature deterministic scenarios
```

Feature-local tests are colocated with their subject as `*.test.ts` or `*.test.tsx`; `testing/` holds reusable helpers rather than all tests. Test filenames/runner adapters are finalized with tooling. Create subfolders when files need them, not empty trees for every capability. The HTTP folder is introduced for approved integration work; mocks remain available for regression.

## Internal Module Communication

- `app/` composes feature entrypoints, providers, navigation and service implementations. Business rules stay with their owning feature.
- A feature may import its own files and deliberate shared exports. Other features' internals are private. Cross-feature journeys are composed in `app/` through named feature exports and explicit callbacks/interfaces; move a concept to shared only after actual reuse demonstrates common ownership.
- `shared/` cannot import `features/` or `app/`. Keep generic transport mechanics separate from feature-specific requests, mappings and error/state decisions. Avoid application-wide dumping folders for types, hooks, stores or utils.
- `index.ts` exports only the intended feature surface. Use direct module imports internally; do not generate broad re-export barrels or state-store dependencies between unrelated features.
- No client code imports backend/worker packages, persistence entities or server DTO implementations. Web and mobile do not import one another's feature code; a shared package requires a separate evidenced ownership decision.

```mermaid
flowchart TD
  App[App composition] --> Feature[Feature public surface]
  App --> Adapter[Selected mock or HTTP adapter]
  Feature --> Service[Feature-owned service interface]
  Adapter -. implements .-> Service
  Feature --> Shared[Shared platform primitives]
  Adapter --> Shared
```

## Client Data and Service Boundary

The feature owns client models, state transitions, service inputs/results and test seams. Views invoke those operations without knowing whether data came from a fake or HTTP. Composition selects implementations; components never branch on a global mock flag. Mock transport cannot leak requests to a real provider. Scenario controls are outside the product flow, resettable and unavailable in production.

During `client-integrate`, the HTTP adapter maps the approved public contract into client models, including errors, pagination, retry and live-update reconciliation where relevant. Keep wire types at this adapter boundary; generate them only from the approved contract/tooling when available. Missing API capability is a contract gap, not permission to redesign accepted UI or change server code.

Keep state as close as possible to its owner. Derive values when practical; share state only for a demonstrated journey requirement. Data transport/caching and screen draft/interaction state have separate owners. Do not select Redux, a cache library or a dependency injection framework from this directory diagram.

## Verification and Design Artifacts

Use the platform ui-design.md and capability prototype for visual/interaction review. Specs/prototypes/approval records live outside app source in `specs/client/web/`; experience maps and the UI baseline live in `docs/client/web/`. Unit/component tests verify testable model/service/view behavior; journey tests cover composed behavior against resettable fixtures. Mock checks do not certify provider behavior.

Enforce feature/public-surface and shared-dependency rules using tooling selected during app setup. Lint/typecheck and test commands are recorded then; no runner/config is created by this document.

## Browser Entrypoints and Delivery

Route entrypoints stay thin: compose approved feature pages and route-level loading/error/access UX. Navigation controls communicate access state; the provider enforces authorization. Shared browser adapters own capabilities such as URL handling and file selection, while feature rules validate the intended action.

If the selected framework requires file-based routes or a root public-assets directory, retain its conventions and map those entry files to `app/entry` responsibilities rather than duplicating routing trees. Split delivery at route/feature boundaries when supported and measured; do not eagerly import every feature through a single barrel. Responsive, focus, keyboard and screen-reader outcomes follow the approved web baseline and risk-based verification plan.

## References

- [Product catalog](../../product-features.md)
- [Client ADRs](../adrs/README.md)
- [Shared ADRs](../../adrs/README.md)
- [Public contracts](../../../contracts/http/README.md)
- [Client workflow](../../../.agents/workflows/client.md)
- [Feature folders (Redux documentation)](https://redux.js.org/faq/code-structure/)
- [Component responsibility and minimal state (React)](https://react.dev/learn/thinking-in-react)
