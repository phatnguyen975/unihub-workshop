---
name: plan
description: Break an approved spec (and contract, if any) into an ordered list of vertical-slice tasks.
---

# /plan — Break the Feature Into Tasks

## Usage

```
/plan <feature-name>
```

## Preconditions

`specs/<feature-name>/SPEC.md` must exist and be `APPROVED`. If the feature has a network interface, `specs/<feature-name>/API_CONTRACT.md` must also exist and be `APPROVED`. If either precondition fails, stop and direct the human back to `/spec` — do not draft a plan against an unapproved or missing artifact.

## What This Command Does

1. **Invoke `task-planning-and-breakdown`** (always).
   - Read the approved `SPEC.md` (and `API_CONTRACT.md`, if present).
   - Identify every distinct, independently testable **capability** the spec/contract requires.
   - Decompose into tasks where **each task is a complete vertical slice** — spanning every architectural layer that capability needs (commonly domain logic, application/orchestration, infrastructure/ persistence, and the interface that exposes it) — never a task scoped to a single layer. See the `task-planning-and-breakdown` skill's Vertical vs. Horizontal Slicing guidance; reverting to per-layer tasks is the most common and most costly mistake at this step.
   - Order tasks by dependency between capabilities (not by layer), and group them into phases by risk/dependency, each ending in a checkpoint.
   - Produce `specs/<feature-name>/tasks.md`, using this skill's task template: each task with explicit, externally-observable acceptance criteria, expected file scope across all layers it touches, and its dependency on earlier tasks.
   - Set the document's status to `DRAFT`.
2. **Present the plan and stop.**
   - Call out any task whose sizing required a judgment call, any dependency ordering that wasn't obvious from the spec alone, and any risk identified during planning along with its mitigation.
   - Do **not** proceed to `/build`. Do **not** write any implementation code.

## Human Verification Gate

The human reviews `tasks.md` for:

- **Correct ordering** — No task depends on a capability a later task is supposed to provide.
- **Correct scope** — Every task is a genuine vertical slice; no task was scoped to a single architectural layer.
- **Correct granularity** — No task should span more than ~10 files across all its layers combined; anything larger should already have been split into a smaller, still-fully-vertical capability before this plan was presented.

After that, human will:

- **Approved** → The agent updates the status line from `DRAFT` to `APPROVED`. `/build` may now be invoked by the human, one task at a time.
- **Changes requested** → The agent revises `tasks.md` per the human's feedback and re-presents it. Repeat until approved.

## Notes for the Agent

- If, while decomposing, a piece of work doesn't cleanly belong to any single caller-observable capability (genuinely shared groundwork more than one capability needs), assign it to the _first_ capability that needs it — see the skill's Handling Shared Foundations guidance. Do not invent a separate task with no caller-observable behavior of its own.
- If sizing reveals a task is too large (per the skill's S/M/L model), split it by finding a smaller, still-end-to-end-testable capability inside it — never by carving it apart into per-layer pieces just to make the size manageable.
- Run the skill's own quality checklist before presenting — confirm every task has falsifiable, externally observable acceptance criteria and an explicit dependency statement (or `—` if it has none).
