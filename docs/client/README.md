# UniHub Workshop — Client Documents

## Scope

Client documents define platform architecture, delivery progress, experience maps and global UI criteria. Web and mobile own separate design baselines. Capability specifications, prototypes and verification belong to the corresponding namespace under `specs/client/`.

## Structure

```text
docs/client/
├── README.md
├── adrs/
│   ├── README.md
│   └── ADR-NNN-slug.md
├── web/
│   ├── source-structure.md
│   ├── feature-delivery.md
│   ├── experience-map.md           # Authored by client-discover
│   ├── ui-design.md                # Authored by client-foundation
│   └── foundation-prototype/
│       ├── index.html              # Authored with the UI baseline
│       └── assets/                 # Local assets when required
└── mobile/
    ├── source-structure.md
    ├── feature-delivery.md
    ├── database-schema.md
    ├── experience-map.md           # Authored by client-discover
    ├── ui-design.md                # Authored by client-foundation
    └── foundation-prototype/
        ├── index.html              # Authored with the UI baseline
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
