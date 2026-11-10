# UniHub Workshop — Shared Templates

## Scope

Formats used across scopes with the caller's architecture, testing policy and output destination.

## Structure

| Template                                                 | Responsibility                                          | Invocation                     |
| -------------------------------------------------------- | ------------------------------------------------------- | ------------------------------ |
| [HTTP Contract](http-contract.md)                        | Requests, responses, errors and side effects            | Public interface authoring     |
| [Review Notes](review-notes.md)                          | Six-axis findings, severity and disposition             | Scoped technical review        |
| [Investigation](investigation.md)                        | Reproduction, evidence, root cause and regression guard | Non-trivial debugging          |
| [Simplification Report](code-simplification-report.html) | Standalone HTML completion report                       | Standalone simplification only |

## Artifacts

Use the caller's approved spec, UI/contract revision, app policy and evidence. Review findings do not themselves authorize code changes. Contract approval governs the interface boundary; implementation follows its owning scope's workflow.

## References

- [Shared Rules](../../rules/shared.md)
- [Skill Catalog](../../skill-catalog.md)
- [Public Contracts](../../../contracts/README.md)

## Workflow

Server review requires human-confirmed findings before `server-fix`. An authorized client build/integration caller may route in-scope defects to `client-fix`. Standalone review is read-only. Workflow simplification writes its caller summary and does not load or generate the standalone HTML report.
