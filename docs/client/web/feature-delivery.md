# Web Feature Delivery

## Scope

This catalog tracks approved web capability bundles and their implementation, verification and human acceptance. Capability IDs belong to this platform and reference applicable product feature IDs.

## References

- [Product Features](../../product-features.md)
- [Source Structure](source-structure.md)
- [Client Specifications](../../../specs/client/README.md)
- [Client Workflow](../../../.agents/workflows/client.md)
- [Client Templates](../../../.agents/templates/client/README.md)

The platform experience map and UI baseline live in `docs/client/web/experience-map.md` and `ui-design.md`; their revisions are recorded in capability artifacts.

## Lifecycle

| State                | Meaning                                                      | Evidence                                       |
| -------------------- | ------------------------------------------------------------ | ---------------------------------------------- |
| DRAFT                | Scope, design or task bundle is being prepared               | Draft artifact revisions                       |
| APPROVED             | The human has approved the implementation dependencies       | Explicit bundle approval and baseline revision |
| IN_PROGRESS          | Implementation is running within approved scope              | Task progress and current mode                 |
| VERIFICATION_PENDING | Required technical or native evidence remains outstanding    | Verification matrix and remaining checks       |
| VERIFIED             | Required technical checks for the declared scope have passed | Fresh verification record                      |
| ACCEPTED             | The human has accepted that scope and mode                   | Explicit acceptance decision                   |

Mode is `MOCK` or `INTEGRATION`. Record the mode independently of lifecycle. Approval, technical verification and acceptance are separate decisions. A mock acceptance does not authorize integration.

## Delivery Index

Add a row when a capability ID and its scope are established. Link approved artifact revisions and update progress through the client workflow. Integration uses its own approved integration plan and keeps the mock record.

| Capability ID | Product Feature IDs | Mode | State | Spec | Plan | Prototype | Verification | Human Acceptance |
| ------------- | ------------------- | ---- | ----- | ---- | ---- | --------- | ------------ | ---------------- |

## Verification and Acceptance

Record test commands/results, visual inspection, platform/device, artifact revisions and pending checks in the capability verification artifact. Required native evidence is supplied through proven device access or human review. Acceptance is recorded only after an explicit human decision.

## Mode and Revision Records

Use separate rows for each `(Capability ID, Mode)` pair. The plan column links `tasks.md` for `MOCK` and `integration-plan.md` for `INTEGRATION`. Link mode/revision-labelled verification and acceptance entries; preserve accepted mock evidence when integration begins. Required pending checks use `VERIFICATION_PENDING`, not `VERIFIED` or `ACCEPTED`. An accepted historical revision does not approve changed dependencies.

## Platform Mock Acceptance

When `client-accept all-mock` is requested, record the explicit capability inventory/revisions, cross-capability journey checks, pending evidence and human decision here. Link each capability verification record. Keep this section without an acceptance claim until the human confirms the declared scope.
