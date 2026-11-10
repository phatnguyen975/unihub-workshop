# UniHub Workshop — Agent Configuration

## Scope

Repository instructions define task ownership, artifact authority, execution gates and verification. Root and app `AGENTS.md` files route to these instructions.

## Structure

| Directory           | Responsibility                                                  |
| ------------------- | --------------------------------------------------------------- |
| `rules/`            | Shared policy and server/client/platform constraints            |
| `workflows/`        | Stage inputs, procedures, outputs and approval gates            |
| `skills/shared/`    | Techniques usable with the caller's scope and policy            |
| `skills/server/`    | Backend/workers techniques and server workflow entrypoints      |
| `skills/client/`    | Client workflow entrypoints and installed UI/testing techniques |
| `templates/shared/` | Cross-scope artifact formats                                    |
| `templates/server/` | Server specification and planning formats                       |
| `templates/client/` | Client discovery/design/planning/verification formats           |

## Artifacts

[Skill Catalog](skill-catalog.md) lists entrypoints and conditional techniques. [Artifact Templates](templates/README.md) identifies artifact schemas. Skill checklists and examples remain in the owning skill's supporting resources. Each imported skill contains its LICENSE.

## References

- [Repository Entrypoint](../AGENTS.md)
- [Documentation Map](../docs/README.md)
- [Specifications](../specs/README.md)
- [Public Contracts](../contracts/README.md)

## Workflow

Establish scope, read shared and matching app rules, then follow the requested workflow stage. Load the selected skill and resources for the concern at hand. Templates describe output structure; workflows govern permission and approval. Report checks actually performed and their remaining dependencies.
