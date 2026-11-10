# UniHub Workshop — Artifact Templates

## Scope

Templates define reusable document structure. The owning workflow selects applicable sections, artifact paths and approval gates. A completed template is a draft until the required human approval is recorded.

## Structure

| Namespace | Templates                            | Use                                                                         |
| --------- | ------------------------------------ | --------------------------------------------------------------------------- |
| Shared    | [Shared Templates](shared/README.md) | Public contracts, technical findings, investigations and standalone reports |
| Server    | [Server Templates](server/README.md) | Backend/workers feature specifications, task plans and amendments           |
| Client    | [Client Templates](client/README.md) | Platform discovery, design, capability planning and verification            |

## Artifacts

Use stable requirement/task/rule IDs and applicable ownership, revision, status and approval fields. Omit an optional section only with a concise applicability statement. Record evidence separately from acceptance. Supporting checklist methods belong to skills; product values and behavior belong to approved artifacts.

## References

- [Server Specifications](../../specs/server/README.md)
- [Client Specifications](../../specs/client/README.md)
- [Public Contracts](../../contracts/README.md)

## Workflow

Read the scope's workflow before choosing a template. Keep each artifact's canonical content in one location and link dependencies by path and approved revision. Update affected artifacts through the owning workflow's amendment process.
