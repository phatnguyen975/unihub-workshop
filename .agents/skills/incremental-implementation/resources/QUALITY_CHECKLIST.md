# Incremental Implementation Quality Checklist

Run this checklist before reporting a task complete. Every item must pass.

## Scope Discipline

- [ ] The task's full scope (the capability it delivers, across every layer it needs) was stated before implementation began.
- [ ] Implementation stayed within the file scope and acceptance criteria declared in the approved task (`tasks.md`), or any expansion beyond that was explicitly surfaced and approved — not silently absorbed.
- [ ] Nothing outside the task's scope was modified without being recorded under "Noticed But Not Touching" first.
- [ ] No other task from `tasks.md` was started before this task's full vertical slice (every layer it needs) was completed end-to-end.

## Layer Ordering

- [ ] This task's layers were ordered by dependency (innermost first) before implementation started — it was not written as one uninterrupted pass and only retroactively described as "ordered."
- [ ] Each layer step is independently meaningful — it represents one coherent piece of this task's capability, not an arbitrary chunk of lines.
- [ ] No layer step mixes more than one logical change (e.g. this task's new logic bundled with an unrelated refactor or a config update).
- [ ] Any deletion-and-replacement was split into a removal step and an addition step, not combined into a single irrevertible change.

## Verification Cadence

- [ ] The build was run and confirmed passing after **each** layer step — not only once at the very end of the whole task.
- [ ] The relevant test suite was run after each layer step and confirmed passing, including pre-existing tests (not just the new ones added for that layer step).
- [ ] No verification command was re-run redundantly against code that had not changed since the last run of that same command.
- [ ] A full task-level verification was run once every layer step was complete, confirming the combination of layers together leaves the system working **and** that the capability is verifiable the way its acceptance criteria describe (e.g. through the real endpoint, not only through an internal function call).

## Correctness-First Discipline

- [ ] Each layer step implements the obviously-correct, concrete version of its behavior — no speculative abstraction was introduced ahead of a second use case that doesn't exist yet.
- [ ] If an optimization or generalization was applied, it happened _after_ the concrete version was proven correct by tests, not instead of writing the concrete version first.

## Commit / Stopping-Point Hygiene

- [ ] If the project commits per layer step, each commit message describes exactly one logical change, traceable to one layer step.
- [ ] If commits are batched differently, each layer step still represents a safe stopping point — the build and existing test suite are fully green at that point, even though the task's overall capability may not be finished yet.
- [ ] No commit (or stopping point) leaves the build or test suite in a failing state "temporarily, to be fixed by the next layer step."

## Task Completion (Not Just Layer Completion)

- [ ] The task is not considered complete after finishing only one or some of its layers — every layer the task's capability needs (per `tasks.md`) was built.
- [ ] Every acceptance criterion stated in the task's entry in `tasks.md` was individually checked against the final implementation, the way a real caller would check it — not assumed to be satisfied because "most of the layers are done."
- [ ] No layer step was mistaken for a task-completion checkpoint — an agent or engineer picking up work after this task should find the capability fully usable end-to-end, not partially wired across some layers and missing in others.

## Work-in-Progress Handling (if applicable)

- [ ] If a layer step introduces a capability not yet ready for users (because the rest of the task isn't done yet), it is gated behind an explicit flag rather than reachable by default.
- [ ] The default (flag-off) path was verified to behave exactly as it did before this task was introduced.

## Final Gate

- [ ] Any item recorded under "Noticed But Not Touching" during this task was surfaced to the human as an explicit, separate item — not silently dropped.
