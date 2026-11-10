# UniHub Workshop — Repository Structure

## Scope

UniHub is a monorepo with independently owned applications, public contracts, specifications, infrastructure and agent instructions. This document maps directory responsibilities; app source guides define internal module layout and dependency boundaries.

## Structure

```text
UniHub/
├── AGENTS.md
├── README.md
├── LICENSE
├── .agents/
│   ├── README.md
│   ├── skill-catalog.md
│   ├── rules/
│   ├── workflows/
│   ├── skills/{shared,server,client}/
│   └── templates/{shared,server,client}/
├── apps/
│   ├── backend/
│   ├── workers/
│   ├── web/
│   └── mobile/
├── docs/
│   ├── README.md
│   ├── product-overview.md
│   ├── product-features.md
│   ├── adrs/
│   ├── server/
│   ├── client/
│   ├── data/
│   └── diagrams/
├── specs/{server,client}/
├── contracts/http/
├── infra/
└── scripts/
```

## Ownership

| Location                            | Responsibility                                                                   | Guide                                                        |
| ----------------------------------- | -------------------------------------------------------------------------------- | ------------------------------------------------------------ |
| `apps/backend/` and `apps/workers/` | Provider implementations, app configuration and server tests                     | [Server Source Structure](server/source-structure.md)        |
| `apps/web/`                         | Web feature modules, app composition and web tests                               | [Web Source Structure](client/web/source-structure.md)       |
| `apps/mobile/`                      | Mobile feature modules, local persistence, device adapters and tests             | [Mobile Source Structure](client/mobile/source-structure.md) |
| `docs/`                             | Product scope, scoped decisions, architecture, data design and delivery catalogs | [Documentation Map](README.md)                               |
| `specs/`                            | Behavior/design bundles, tasks, review and verification records                  | [Specifications](../specs/README.md)                         |
| `contracts/`                        | Approved public interfaces and HTTP conventions                                  | [Public Contracts](../contracts/README.md)                   |
| `infra/`                            | Local infrastructure topology and component configuration                        | [Infrastructure Guide](../infra/README.md)                   |
| `.agents/`                          | Task routing, policy, workflow, techniques and artifact templates                | [Agent Configuration](../.agents/README.md)                  |
| `scripts/`                          | Workspace automation with an established task owner                              | Repository task/configuration requirements                   |

Product feature IDs link shared scope to server/web/mobile delivery records. Capability IDs and progress belong to their owning app. Providers and consumers share approved contracts without importing one another's implementation internals.

## Artifact Locations

Directory trees define ownership and artifact schemas, including files authored by later workflow stages. [Client Document Structure](client/README.md#structure) assigns experience maps, platform `ui-design.md` files and foundation prototypes. [Contract Structure](../contracts/README.md#structure) assigns `openapi.yaml`, referenced operation definitions and reusable components. [Specifications](../specs/README.md) assigns server/client artifact bundles; [Artifact Templates](../.agents/templates/README.md) defines their document formats.

Fixed directories may be empty until their artifacts are authored. Parameterized names such as `<capability-id>` identify instances and are not literal folder names. Files in a structure diagram do not imply approval or completion.

## References

- [Product Overview](product-overview.md)
- [Product Features](product-features.md)
- [Documentation Map](README.md)
- [HTTP Contract Registry](../contracts/http/README.md)
