# Task Plan Quality Checklist

Run this checklist against a drafted `tasks.md` before presenting it for human approval. Every item must pass.

## Pre-Flight

- [ ] `SPEC.md` exists and is `APPROVED`.
- [ ] If the feature exposes a network interface, `API_CONTRACT.md` exists and is `APPROVED`.
- [ ] The project's own architecture/structure documentation was read to determine the actual layering this plan should follow — not assumed or invented.
- [ ] No production code was written during planning. The plan document is the only output.

## Structure

- [ ] The plan's status line starts as `DRAFT`.
- [ ] "Overview" is one paragraph and assumes the reader has already read the spec (it does not restate the spec).
- [ ] "Architecture Decisions for This Plan" contains only sequencing/structuring decisions specific to this plan — not general architectural decisions that belong in the project's own decision records.
- [ ] Tasks are grouped into phases that follow the project's architectural layering, innermost-to-outermost.
- [ ] Every phase ends with a checkpoint.

## Per-Task Checks

- [ ] The task title is one clear sentence describing what it adds or changes — not a vague verb phrase like "implement the feature" or "handle edge cases."
- [ ] The task is sized S or M. No task is sized L or larger — if one was found at this size, it has already been split before this checklist is being run.
- [ ] The task's file scope is listed.
- [ ] The task's dependency on earlier tasks (if any) is stated explicitly, or marked `—` if it has none.
- [ ] Every acceptance criterion is falsifiable — a different person could verify it without asking the implementer what they meant.
- [ ] Every acceptance criterion traces back to a specific Functional Requirement, Acceptance Criteria scenario, or contract endpoint — not a restatement of the task title.
- [ ] The task does not silently bundle more than one distinct behavior. (If it does, split it — bundling is the most common way an S/M-labeled task is secretly an L.)

## Ordering and Dependencies

- [ ] No task depends on a later task (no forward dependency / no circular dependency).
- [ ] The overall sequence matches a sensible build order for the project's architecture — data model and core logic generally precede the interface that exposes them, unless the project's own conventions specify a different order, in which case that order is followed and the deviation is noted.
- [ ] Validation logic and migration/seed-data tasks are placed alongside the layer they belong to, not dumped into an undifferentiated catch-all phase.

## Checkpoints

- [ ] Every checkpoint states a concrete, checkable condition (e.g. "all tests in this phase pass and the build is clean") — not a vague aspiration like "things are looking good."
- [ ] The final checkpoint explicitly requires that every acceptance criterion across every task has been verified, not just that every checkbox is ticked.

## Risks and Open Questions

- [ ] Every identified risk has both an impact level and a concrete mitigation — not just a description of the risk with nothing to do about it.
- [ ] Any ambiguity discovered during planning that the spec did not already resolve is listed as an open question for the human — it was not silently resolved by the agent's own guess.
- [ ] The "Open Questions" section is present even when empty, with an explicit "None." rather than being omitted — so a reviewer can confirm it was consciously checked, not skipped.

## Final Gate

- [ ] The agent has presented the plan as `DRAFT` and stopped — it has not begun implementing any task without explicit human approval.
- [ ] (After approval) The status line has been updated to `APPROVED` before `/build` begins on the first task.
