# UniHub Workshop — Server Templates

## Scope

Formats for server feature requirements, ordered implementation tasks and proposed amendments.

## Structure

| Template                                        | Output                                              | Stage                       |
| ----------------------------------------------- | --------------------------------------------------- | --------------------------- |
| [Specification](SPEC.md)                        | `specs/server/<feature-id>/SPEC.md`                 | `server-spec`               |
| [Task Plan](tasks.md)                           | `specs/server/<feature-id>/tasks.md`                | `server-plan`               |
| [Improvement Proposal](improvement-proposal.md) | `specs/server/<feature-id>/improvement-proposal.md` | Approved artifact amendment |

## Artifacts

Requirements, business rules and acceptance criteria use stable IDs. Task plans specify vertical slices and externally observable acceptance. Amendments identify affected artifacts, justification, impact and each approval decision.

## References

- [Shared Templates](../shared/README.md)
- [Server Feature Delivery](../../../docs/server/feature-delivery.md)
- [Template Catalog](../README.md)

## Workflow

Follow [Server Workflow](../../workflows/server.md). Public interfaces use the shared HTTP contract template; review findings use the shared review template. Each server stage retains its explicit invocation and human gates.
