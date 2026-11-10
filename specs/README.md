# UniHub Workshop — Specifications

## Scope

Specifications define approved behavior and implementation tasks. Each scope owns its artifact revisions, approval records, findings and verification evidence. Public interfaces are owned by the contract registry.

## Structure

```text
specs/
├── README.md
├── server/
│   ├── README.md
│   └── <feature-id>/
└── client/
    ├── README.md
    ├── web/
    │   └── <capability-id>/
    └── mobile/
        └── <capability-id>/
```

Angle-bracket names identify artifact instances. Establish the feature/capability ID before creating its directory.

## Artifacts

| Scope               | Artifact Guide                            | Delivery Source                                                                                                                       |
| ------------------- | ----------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| Backend and Workers | [Server Specifications](server/README.md) | [Server Feature Delivery](../docs/server/feature-delivery.md)                                                                         |
| Web and Mobile      | [Client Specifications](client/README.md) | [Web Feature Delivery](../docs/client/web/feature-delivery.md) · [Mobile Feature Delivery](../docs/client/mobile/feature-delivery.md) |

## References

- [Product Features](../docs/product-features.md)
- [HTTP Contract Registry](../contracts/http/README.md)
- [Artifact Templates](../.agents/templates/README.md)

## Workflow

Use the owning scope's workflow and approved revisions before implementation. Product scope, design approval, technical verification and human acceptance are recorded independently. Update artifacts through the applicable approval process; a commit does not grant approval.
