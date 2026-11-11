# UniHub Workshop — Client Documents

## Scope

Client documents define platform architecture, delivery progress, experience maps and global UI criteria. Web and mobile own separate design baselines. Capability specifications, prototypes and verification belong to the corresponding namespace under `specs/client/`.

## Structure

```text
docs/client/
├── README.md
├── source-boundaries.md
├── feature-planning.md
├── adrs/
│   ├── README.md
│   └── ADR-NNN-slug.md
├── web/
│   ├── source-structure.md
│   ├── tooling-plan.md             # Approved web setup scope
│   ├── tooling.md                  # Commands and verification evidence
│   ├── feature-delivery.md
│   ├── experience-map.md           # Authored by client-discover
│   ├── ui-design.md                # Authored by client-foundation
│   └── foundation-prototype/
│       ├── index.html              # Authored with the UI baseline
│       ├── styles.css
│       ├── app.js
│       ├── fixtures/               # Synthetic JSON scenarios
│       └── assets/                 # Local assets when required
└── mobile/
    ├── source-structure.md
    ├── feature-delivery.md
    ├── database-schema.md
    ├── experience-map.md           # Authored by client-discover
    ├── ui-design.md                # Authored by client-foundation
    └── foundation-prototype/
        ├── index.html              # Authored with the UI baseline
        ├── styles.css
        ├── app.js
        ├── fixtures/               # Synthetic JSON scenarios
        └── assets/                 # Local assets when required
```

The tree defines canonical artifact locations, including files produced by later stages. A path does not imply that its content has been drafted or approved. Parameterized names identify instances.

## Artifacts

| Output               | Web                                     | Mobile                                     |
| -------------------- | --------------------------------------- | ------------------------------------------ |
| Scope/journeys       | `docs/client/web/experience-map.md`     | `docs/client/mobile/experience-map.md`     |
| Global design rules  | `docs/client/web/ui-design.md`          | `docs/client/mobile/ui-design.md`          |
| Foundation prototype | `docs/client/web/foundation-prototype/` | `docs/client/mobile/foundation-prototype/` |
| Capability artifacts | `specs/client/web/<capability>/`        | `specs/client/mobile/<capability>/`        |

| Artifact                        | Creation Stage             | Authority                                                                        |
| ------------------------------- | -------------------------- | -------------------------------------------------------------------------------- |
| experience-map.md               | client-discover            | Approved platform scope, journeys and screen/state inventory                     |
| ui-design.md                    | client-foundation          | Human-approved global design rules, tokens, components and verification criteria |
| foundation-prototype/index.html | client-foundation          | Representative interactive preview supporting baseline approval                  |
| Capability Bundle               | client-spec/prototype/plan | Approved capability behavior, design and tasks                                   |

Web uses `docs/client/web/ui-design.md`; mobile uses `docs/client/mobile/ui-design.md`. Capability specs reference the owning baseline's rule IDs and revision and add local requirements. Reusable patterns and scoped exceptions follow baseline approval and impact review across dependent prototypes/components/capabilities/golden images.

## Capability Discovery

[Client Capability Planning](feature-planning.md) defines feature sizing, dependency semantics and readiness gates. The [Web Experience Map](web/experience-map.md) and [Mobile Experience Map](mobile/experience-map.md) define platform journeys; their delivery catalogs own the ordered capability inventory and blocking relationships. Draft discovery does not imply bundle approval or completed implementation.

## Prototype Ownership

Each platform has two design artifact scopes:

| Scope               | Canonical Directory                                  | Approval Coverage                                                                       |
| ------------------- | ---------------------------------------------------- | --------------------------------------------------------------------------------------- |
| Platform foundation | `docs/client/<platform>/foundation-prototype/`       | Representative screens and shared visual/interaction patterns supporting `ui-design.md` |
| Capability          | `specs/client/<platform>/<capability-id>/prototype/` | The capability's screens, navigation, interactions and relevant states                  |

The foundation is a representative design baseline, not a complete implementation of every capability journey. Each capability owns its prototype and references the approved platform UI rules and revision. Capability prototypes may contain multiple screens; source feature modules do not determine prototype boundaries. Approve capability bundles incrementally rather than requiring every platform capability prototype before implementation.

Both prototype scopes use plain HTML, CSS and JavaScript with synthetic JSON fixtures, separate from React/Expo application code. Their file layout and preview requirements are defined in [Client Prototype Artifacts](../../specs/client/README.md#prototype-artifacts). Foundation approval establishes design direction; capability approval establishes the scoped behavior and UI. Neither substitutes for native-device verification.

## Source Ownership

Web uses Vite `src/main.tsx` and `src/app/` composition. Mobile uses Expo Router `src/app/` for routes/layouts only and `src/bootstrap/` for composition/lifecycle. Both use cohesive `src/features/<feature>/` modules and narrowly reusable `src/shared/` primitives; source module IDs need not match capability/spec IDs. [Client Source Boundaries](source-boundaries.md) owns shared import/state rules; platform source guides own layout and composition details. Web/mobile share public contract semantics; source-code sharing requires an explicit package/ownership decision rather than importing one another's app files.

## References

Platform decisions: [client ADRs](adrs/README.md) and [shared ADRs](../adrs/README.md); [mobile persistence](mobile/database-schema.md). Decisions and architecture targets do not claim that an application has been implemented. Feature-oriented architecture targets: [web structure](web/source-structure.md) and [mobile structure](mobile/source-structure.md). Framework/SDK versions and runtime/tooling are established during app setup.

HTML prototypes demonstrate design and flow. Native acceptance needs physical-device evidence or the declared human-review fallback; browser/component results do not certify native rendering. Public integration consumes the [contract registry](../../contracts/http/README.md) through client service adapters after the integration gate.

- [Web Delivery](web/feature-delivery.md)
- [Mobile Delivery](mobile/feature-delivery.md)
- [Client Specifications](../../specs/client/README.md)
- [Client Templates](../../.agents/templates/client/README.md)
- [Product Overview](../product-overview.md)
- [Product Features](../product-features.md)
- [UI Design Template](../../.agents/templates/client/ui-design.md)

## Workflow

Follow [Client Workflow](../../.agents/workflows/client.md). Discovery produces the experience map; foundation produces each platform's UI baseline and representative prototype for human approval. Capability design/implementation depend on approved revisions. App setup selects compatible framework/SDK versions and runtime tooling; API integration follows its separate contract and integration-plan gates.

Web setup and verification commands: [Web Tooling](web/tooling.md), under the approved [Web Tooling Plan](web/tooling-plan.md).
