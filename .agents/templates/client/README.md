# UniHub Workshop — Client Templates

## Scope

Formats for web/mobile scope, design dependencies, implementation planning and acceptance evidence.

## Structure

| Template                                | Output                                                        | Stage                            |
| --------------------------------------- | ------------------------------------------------------------- | -------------------------------- |
| [Experience Map](experience-map.md)     | `docs/client/<platform>/experience-map.md`                    | `client-discover`                |
| [UI Design](ui-design.md)               | `docs/client/<platform>/ui-design.md`                         | `client-foundation`              |
| [Specification](SPEC.md)                | `specs/client/<platform>/<capability-id>/SPEC.md`             | `client-spec`                    |
| [Prototype Review](prototype-review.md) | `specs/client/<platform>/<capability-id>/prototype-review.md` | `client-prototype`               |
| [Task Plan](tasks.md)                   | `specs/client/<platform>/<capability-id>/tasks.md`            | `client-plan`                    |
| [Verification](verification.md)         | `specs/client/<platform>/<capability-id>/verification.md`     | Build/review/acceptance          |
| [Integration Plan](integration-plan.md) | `specs/client/<platform>/<capability-id>/integration-plan.md` | `client-plan` --mode integration |

## Artifacts

Identify platform, scope/mode, artifact revisions and actual approval decisions. Capability specs reference UI rule IDs; prototype review records scenarios and evidence. Task plans identify appropriate code, browser, native and human checks. Verification distinguishes technical results, pending evidence and human acceptance.

The UI Design template records platform foundation preview instructions and approval. The Prototype Review template records a capability's independent preview, JSON fixture scenarios and design decision. Both follow [Prototype Artifacts](../../../specs/client/README.md#prototype-artifacts); neither review record substitutes for the other scope's approval.

## References

- [Shared Review Template](../shared/review-notes.md)
- [Client Documents](../../../docs/client/README.md)
- [Template Catalog](../README.md)

## Workflow

Follow [Client Workflow](../../workflows/client.md). Scope/foundation and the capability bundle require the applicable human approvals. Mock acceptance is followed by integration only under a separate approved contract and integration scope.

## Authoring Instructions

Copy the selected template to its canonical output path; template files are schemas, not product approvals. Replace all placeholders, use stable requirement/scenario/task IDs and link exact dependency revisions. Complete every applicable section; write `Not Applicable` with a short reason for excluded concerns. Remove authoring instructions from the final artifact after satisfying them. Leave genuinely unresolved inputs explicit, with owner and blocked work, rather than inventing values or approval.

Metadata approval starts pending. An approval identifies the human decision, scope, mode and compatible artifact revisions. Use the same scenario IDs from discovery/spec through prototype, tasks, review and verification. Do not create empty product artifacts merely because a template exists.
