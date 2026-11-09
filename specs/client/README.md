# Client Specifications

## Scope

Web and mobile own independent capability bundles. A capability references product feature IDs and the owning platform's experience map and UI baseline. Capability names describe the approved client scope.

## Structure

```text
client/
├── README.md
├── web/
│   └── <capability-id>/
└── mobile/
    └── <capability-id>/
        ├── SPEC.md
        ├── tasks.md
        ├── prototype/
        ├── prototype-review.md
        ├── review-notes.md
        ├── verification.md
        └── integration-plan.md
```

Both platform bundles use the same artifact layout. Create an instance directory when its capability ID is established; create conditional artifacts when their stage applies.

## Artifacts

| Artifact            | Responsibility                                                                  |
| ------------------- | ------------------------------------------------------------------------------- |
| SPEC.md             | Behavior, acceptance criteria, screen/state matrix and client data/action needs |
| tasks.md            | Vertical slices, dependencies, file scope and verification plan                 |
| prototype/          | Interactive design preview and its local assets                                 |
| prototype-review.md | Scenarios, design evidence, revisions and approval scope                        |
| review-notes.md     | Technical findings and caller/human disposition                                 |
| verification.md     | Checks performed, visual/native evidence, pending checks and acceptance         |
| integration-plan.md | Approved contract revision, mappings, adapter tasks and integration checks      |

## References

- [Product Features](../../docs/product-features.md)
- [Web Feature Delivery](../../docs/client/web/feature-delivery.md)
- [Mobile Feature Delivery](../../docs/client/mobile/feature-delivery.md)
- [Client Templates](../../.agents/templates/client/README.md)
- [Shared Templates](../../.agents/templates/shared/README.md)
- [HTTP Contract Registry](../../contracts/http/README.md)

Platform namespaces: [Web Capabilities](web/) and [Mobile Capabilities](mobile/). Platform design criteria live in docs/client/<platform>/ui-design.md; capability documents reference rule IDs and revisions and add local requirements.

## Workflow

Follow [Client Workflow](../../.agents/workflows/client.md). Scope and foundation approval precede the capability design bundle. Approve its spec/prototype/task revisions before implementation. Build, technical review and fixes run within approved scope; material changes require approval. Record human acceptance separately. Integration requires an explicit invocation, mock acceptance, an approved public contract and an approved integration plan.

Run-specific screenshots and logs belong in .agent-work/client/<platform>/<capability>/<run>/. Durable verification records identify scenarios, revisions, commands, results and acceptance decisions.
