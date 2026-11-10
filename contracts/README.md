# UniHub Workshop — Public Contracts

## Scope

This directory owns the public interfaces between UniHub providers and consumers. Contracts define requests, responses, errors, authorization requirements, side effects and retry/consistency semantics. Server and client specifications define how their respective applications implement or consume those interfaces.

## Structure

```text
contracts/
├── README.md
└── http/
    ├── README.md                  # Interface registry
    ├── standards.md               # Shared HTTP conventions
    ├── openapi.yaml               # OpenAPI entrypoint when authored
    ├── features/
    │   └── <feature-id>.md        # Feature interface specification
    ├── behavior/
    │   └── <topic>.md             # Supplemental behavioral semantics
    ├── paths/
    │   └── <resource>.yaml        # Referenced OpenAPI operations
    └── components/
        ├── schemas/<name>.yaml
        ├── parameters/<name>.yaml
        ├── responses/<name>.yaml
        └── securitySchemes/<name>.yaml
```

The tree defines canonical artifact locations. Parameterized names identify instances. Files are authored as part of approved contract work; directory or filename presence does not establish interface authority.

## Artifacts

| Artifact                            | Responsibility                                                                                    |
| ----------------------------------- | ------------------------------------------------------------------------------------------------- |
| [HTTP Registry](http/README.md)     | Selects the contract source and format for each interface scope                                   |
| [HTTP Standards](http/standards.md) | Defines versioning, headers, pagination, envelopes, errors and SSE conventions                    |
| Feature Markdown                    | Specifies feature operations, schemas and behavior at `http/features/<feature-id>.md`             |
| OpenAPI Entrypoint                  | Composes operations and reusable definitions through `http/openapi.yaml`                          |
| Path and Component Definitions      | Supply referenced OpenAPI content under `http/paths/` and `http/components/`                      |
| Behavior Notes                      | Describe supplemental semantics under `http/behavior/`, linked from the relevant interface source |

The [HTTP Contract Template](../.agents/templates/shared/http-contract.md) defines the Markdown format. OpenAPI work selects a compatible specification version and validation tooling before authoring YAML definitions.

## Authority and Approval

- Resolve the interface source through the HTTP registry and check its approval status and applicable revision. Drafts are proposals, not implementation dependencies.
- Each operation has one authoritative schema source. Referenced definitions and supplemental behavior form part of that contract's declared scope.
- A format conversion preserves approved schema and behavioral semantics. Verify parity, obtain approval and update the registry for the converted scope before consumers/providers rely on the replacement source.
- Generated documentation and provider output are implementation evidence; they acquire contract authority only through the same approval and registry process.

## Workflow

Follow [Contract Workflow](../.agents/workflows/contracts.md). Derive interface changes from approved requirements, assess provider/consumer impact and obtain the owning approvals before dependent implementation. Contract-only work does not authorize application or infrastructure changes.

Providers implement approved interfaces through the server workflow. Consumers map approved wire data to client-owned models through service adapters. Mock scenarios express client data needs; they do not define public endpoints. Report interface gaps and resolve them through contract approval before proceeding with dependent work.

## References

- [Server Specifications](../specs/server/README.md)
- [Client Specifications](../specs/client/README.md)
