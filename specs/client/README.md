# UniHub Workshop — Client Specifications

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
        │   ├── index.html
        │   ├── styles.css
        │   ├── app.js
        │   ├── fixtures/
        │   │   └── scenarios.json
        │   └── assets/
        ├── prototype-review.md
        ├── review-notes.md
        ├── verification.md
        └── integration-plan.md
```

Both platform bundles use the same artifact layout. Create an instance directory when its capability ID is established; create conditional artifacts when their stage applies.

## Artifacts

| Artifact              | Responsibility                                                                  |
| --------------------- | ------------------------------------------------------------------------------- |
| `SPEC.md`             | Behavior, acceptance criteria, screen/state matrix and client data/action needs |
| `tasks.md`            | Vertical slices, dependencies, file scope and verification plan                 |
| `prototype/`          | Interactive design preview and its local assets                                 |
| `prototype-review.md` | Scenarios, design evidence, revisions and approval scope                        |
| `review-notes.md`     | Technical findings and caller/human disposition                                 |
| `verification.md`     | Checks performed, visual/native evidence, pending checks and acceptance         |
| `integration-plan.md` | Approved contract revision, mappings, adapter tasks and integration checks      |

## Prototype Artifacts

Each capability owns `specs/client/<platform>/<capability-id>/prototype/`. Its prototype covers the capability's screen inventory, navigation, controls and relevant validation/loading/empty/error/success/permission/offline states. One capability can span multiple screens; it does not require one prototype directory per screen or source module.

The platform foundation at `docs/client/<platform>/foundation-prototype/` follows the same file conventions but demonstrates representative global patterns. Capability prototypes reference the approved platform baseline and add only scoped requirements. Foundation approval does not approve individual capability behavior.

| Path                      | Responsibility                                                         |
| ------------------------- | ---------------------------------------------------------------------- |
| `index.html`              | Review entrypoint and semantic screen markup                           |
| `styles.css`              | Prototype styles aligned with platform UI rule IDs and tokens          |
| `app.js`                  | Local navigation, controls, validation and simulated state transitions |
| `fixtures/scenarios.json` | Named deterministic synthetic datasets and scenario inputs             |
| `assets/`                 | Local images, icons and other required design assets                   |

Use plain HTML, CSS and JavaScript without React, Expo or a required application build step. Additional pages, scripts, styles and JSON files may be added when the review scope warrants them. Create populated assets and fixture files when needed; do not generate dummy content solely to fill the tree.

JSON fixtures supply data, while JavaScript implements observable interactions and state transitions. Keep reset behavior deterministic and scenario controls outside the product UI. Use synthetic data only; prototypes must not call live APIs, perform real payments or require secrets. They demonstrate client needs rather than defining public wire contracts.

Serve previews over local HTTP so JSON loading and asset paths work consistently; do not rely on `file://`. Record the exact preview command, URL, scenario selector, reset steps and artifact revisions in `prototype-review.md`. A preview must work without starting the production app or backend. Capture and inspect the required states and viewports. Mobile HTML previews establish design and flow only; native rendering and device behavior require the mobile verification process.

## References

- [Product Features](../../docs/product-features.md)
- [Web Feature Delivery](../../docs/client/web/feature-delivery.md)
- [Mobile Feature Delivery](../../docs/client/mobile/feature-delivery.md)
- [Client Templates](../../.agents/templates/client/README.md)
- [Shared Templates](../../.agents/templates/shared/README.md)
- [HTTP Contract Registry](../../contracts/http/README.md)

Platform namespaces are `specs/client/web/` and `specs/client/mobile/`. Platform design criteria live in `docs/client/<platform>/ui-design.md`; capability documents reference rule IDs and revisions and add local requirements.

## Workflow

Follow [Client Workflow](../../.agents/workflows/client.md). Scope and foundation approval precede the capability design bundle. Approve its spec/prototype/task revisions before implementation. Build, technical review and fixes run within approved scope; material changes require approval. Record human acceptance separately. Integration requires an explicit invocation, mock acceptance, an approved public contract and an approved integration plan.

Use an operating-system temporary directory for disposable screenshots and logs. Record scenarios, revisions, commands, results and acceptance decisions in `verification.md`; retain selected evidence only at an agreed artifact location or test/CI artifact reference.
