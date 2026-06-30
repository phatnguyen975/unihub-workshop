---
name: incremental-implementation
description: Governs the safe build order within that one task (innermost layer first, each layer verified before the next), so the task never sits half-built or unverified between layers. Use when building one task from an approved task plan — a task that is itself already a complete vertical slice through multiple architectural layers. Do not use for a single-file, single-layer change where there's nothing to sequence.
---

# Incremental Implementation

## Overview

This is the execution discipline that makes a task from [`task-planning-and-breakdown`](../task-planning-and-breakdown/SKILL.md) actually land safely. Each task handed to this skill is already a complete **vertical slice** — one capability, cutting through every architectural layer it needs, sized to be independently end-to-end testable. This skill does not re-decide _what_ the task is or split it back into separate per-layer tasks; that boundary was already decided by `task-planning-and-breakdown`, and is explicitly not this skill's concern (see **Relationship to Task Boundaries** section below).

What this skill _does_ govern is _how_ one task's vertical slice gets built safely: layer by layer, in dependency order (innermost first), each layer verified before building the next one on top of it — never the whole task written in one uninterrupted pass and tested only at the end. A plan can be perfectly scoped into clean vertical slices and still go wrong if a single task is implemented as one enormous, uninterrupted burst of code with no checkpoint until everything is "done."

This skill is the _how_ of building; [`test-driven-development`](../test-driven-development/SKILL.md) is the _proof_ that each layer actually works. The two are used together — every layer built under this skill is built using Red-Green-Refactor, not as an afterthought once it's "done."

## Relationship to Task Boundaries

It is essential not to confuse this skill's internal, layer-ordered build steps with task boundaries:

- **`task-planning-and-breakdown` decides _what_ a task is** — one complete, caller-observable capability, spanning every layer it needs. A task is never just "the persistence for this feature" or "the API for this feature" — see that skill's Vertical vs. Horizontal Slicing guidance.
- **This skill decides _how_ to build one task** — by working through that task's own layers in dependency order, verifying each one before building the next. The end of this process is the _same_ task, fully built across all its layers, end-to-end testable — not a collection of separately "finished" layer-pieces waiting for other tasks to complete them.

If you find yourself tempted to stop after finishing only the domain-layer portion of a task and call it complete, or to commit a task's infrastructure layer with no corresponding interface yet and move on to a different task, that is a sign this skill's internal layer ordering is being mistaken for `task-planning-and-breakdown`'s task boundaries. Finish the current task's full vertical slice before starting the next task.

## When to Use

- Building one task from an approved `tasks.md`, where that task spans more than one architectural layer (the normal case for a vertical slice).
- About to write a large amount of code for one task in one uninterrupted pass.
- A task feels big enough that losing track of which layers are already verified vs. still unverified becomes a real risk.

## When NOT to Use

- A single-file, single-layer change where there is nothing to sequence — the task is already as small a unit as this skill would produce.

## Core Principles

1. **Build one task's vertical slice, one layer at a time, in dependency order — never the whole task in one uninterrupted pass.** The task handed to this skill is already a complete vertical slice — one capability, spanning every layer it needs. This skill's job is to build _that one capability_ safely: write the innermost layer first (commonly the domain/business-logic layer), verify it, then the layer that depends on it, verify that, and so on outward until the capability is complete and end-to-end testable. This is sequencing _within_ one capability by layer dependency — it is not a license to split one task back into separate, independently-shippable layer tasks the way `task-planning-and-breakdown` explicitly forbids. The whole task's capability must be complete, end-to-end, by the time this skill is done with it.
2. **Every layer step leaves the system working.** After each layer step, the project must still build and every existing test must still pass. Never leave the codebase in a state where this is not true between layer steps, even temporarily "just for now." (The task itself is allowed to be mid-progress — e.g. the domain layer exists but the interface doesn't yet — as long as the build and the existing test suite are both green at that point.)
3. **Each layer step changes one logical thing.** Don't mix concerns — adding this task's domain logic, refactoring something unrelated in that same layer, and updating configuration are three different concerns, not one layer step.
4. **Prefer additive, independently revertable changes.** A layer step that only adds new files or new functions can be reverted on its own without untangling it from something else. A layer step that deletes something and replaces it in the same step cannot be cleanly reverted — separate the deletion from the replacement into two steps.
5. **Implement the obviously-correct version first; optimize after correctness is proven.** Three similar lines of code, written plainly, beat a premature abstraction that's harder to verify and harder to later prove correct. Optimize once tests confirm the naive version actually works.
6. **Touch only what the current task requires.** If something else needs fixing along the way, note it — don't fix it inline as a drive-by change the reviewer never asked for (see **Noticed But Not Touching** section below).
7. **Re-verify after every layer step, not just at the end of the task.** Running the build and test suite once at the very end of a multi-layer task defeats the purpose — the value of building layer by layer is catching a break at the exact layer that caused it, not several layers later.

## Workflow

### Step 1 — Confirm the Task and Its Boundaries

Before writing anything, restate in one or two sentences exactly what capability this task delivers and what is explicitly out of scope for this pass. If this task came from an approved [`tasks.md`](../task-planning-and-breakdown/resources/TASKS_TEMPLATE.md), its stated acceptance criteria, layer list, and file scope _are_ this boundary — don't silently expand it because something adjacent looks tempting to also fix, and don't silently narrow it by treating one layer as a separate deliverable from the rest.

### Step 2 — Order This Task's Layers by Dependency

Identify the layers this _one_ task's capability needs (per its entry in `tasks.md`), and order them by dependency — innermost first. This is not splitting the task into smaller independent deliverables; it's deciding the safe build _order_ for the one capability this task already is. A common ordering:

```
Layer step 1: Domain/business-logic for this capability
        │
        ▼
Layer step 2: Application/orchestration that uses layer step 1
        │
        ▼
Layer step 3: Infrastructure/persistence that layer step 2 depends on
        │
        ▼
Layer step 4: Presentation/interface that exposes the capability
```

Not every task touches all four — a small task may only need two layer steps. The point is to consciously decide the build order before starting, not to discover it retroactively after writing everything at once. **All layer steps belong to the same task and the same capability — none of them is a stopping point where the task could be considered "done" on its own.** The task is only complete once every layer step for it is built and the capability is verifiable end-to-end, exactly as its acceptance criteria in `tasks.md` describe.

### Step 3 — Implement One Layer Step

Write the obviously-correct version of this layer step — no speculative abstraction, no handling for cases this step doesn't actually need yet. Drive it through Red-Green-Refactor per `test-driven-development`: a failing test exists before this layer step's production code does.

### Step 4 — Verify the Layer Step in Isolation

Run the project's build and the relevant test suite immediately after this layer step, before starting the next one. Confirm:

- The build succeeds.
- All tests pass — the new ones for this layer step, and every pre-existing one (this is the same Beyoncé Rule discipline from `test-driven-development`, applied at the layer-step boundary, not just at the end of the whole task).

Only re-run a verification command after a change that could actually affect it. Re-running the same command against unchanged code adds no information and wastes the verification step's purpose.

### Step 5 — Commit the Layer Step (or Mark It as a Stopping Point)

If the project's workflow commits per layer step, do so now, with a message describing exactly this one logical change — not "various changes" and not a description of the whole task if this was only one layer step of it. If commits are batched differently in this project, at minimum treat this point as a safe stopping point: the build and existing test suite are fully green, so work could pause here without leaving anything broken — even though the task's overall capability isn't finished yet.

### Step 6 — Repeat for the Next Layer Step

Return to Step 3 for the next layer step in the sequence from Step 2. Continue until every layer step for this task is implemented and verified, and the task's capability is complete end-to-end.

### Step 7 — Final Task-Level Verification

Once all layer steps for the task are complete, run the full verification suite once more at the task level (not just the last layer step's narrow scope) to confirm the combination of all layers together still leaves the system working — and, critically, that the capability is actually verifiable the way `tasks.md` says it should be (e.g. by calling the real endpoint, not only by calling an internal function directly). Then compare the result against the task's stated acceptance criteria from `tasks.md` — every one of them should now be demonstrably true, not just plausible.

If you're going to call the real endpoint, first ask the human reviewer whether it's safe to do so in the current environment (e.g. a development environment, not production) before proceeding.

### Step 8 — Self-Review Against the Quality Checklist

Run through [`resources/QUALITY_CHECKLIST.md`](resources/QUALITY_CHECKLIST.md) before reporting the task complete.

## Noticed But Not Touching

While implementing a layer step, it's common to notice something unrelated: an unused import, a confusing name, a missing error message somewhere nearby. Resist fixing it inline — an unrequested drive-by change makes the diff harder to review and conflates two unrelated concerns in one commit (violates **Core Principle #3**).

Instead, record it explicitly and offer it as a separate, opt-in item:

```
NOTICED BUT NOT TOUCHING:
- <file path> has <observation>, unrelated to this task
- <another observation>

Want me to create a follow-up task for either of these?
```

This keeps the current layer step's diff focused on exactly what it claims to do, while still surfacing real issues instead of silently ignoring them.

## Giving and Receiving Scope Boundaries

When a task is handed off — whether to an AI agent or between engineers — state explicitly what's in scope for _this task_ and what is deliberately deferred to a different task. A vague instruction invites scope creep in either direction (doing too much, or stopping short of what was actually asked). A concrete example of a well-scoped instruction:

> "Build task T1 from `tasks.md` end to end — domain, application, infrastructure, and the interface that exposes it. Don't start T2; that's a separate task with its own dependency on T1 being complete. After implementing, run the build and test suite to verify nothing broke before reporting T1 done."

This makes the boundary unambiguous in both directions: which task to build now (in full, across all its layers), which task not to start yet, and what "verified" means before continuing.

## Handling Work-in-Progress That Must Be Merged Before It's User-Ready

Sometimes a layer step needs to merge before the full task is ready to be exposed to real users — for example, to keep individual commits small and reviewable rather than holding a long-lived branch for the whole task. When this happens, gate the unfinished surface behind an explicit flag rather than leaving partially-wired, half-working behavior reachable by default:

```
// Feature flag for work-in-progress
const ENABLE_NEW_CAPABILITY = readFlag("FEATURE_NEW_CAPABILITY")

if (ENABLE_NEW_CAPABILITY) {
    // new, still-in-progress behavior
} else {
    // existing behavior, unchanged
}
```

This lets each layer step's commit merge independently and keep the default path fully working, while the in-progress capability stays inert until the whole task is genuinely complete and intentionally turned on.

## Quality Checklist

Condensed version — full checklist lives in [`resources/QUALITY_CHECKLIST.md`](resources/QUALITY_CHECKLIST.md):

- [ ] The task's scope boundary was stated explicitly before starting — the full capability this task delivers, and what's deliberately deferred to a different task.
- [ ] The task's layers were ordered by dependency before implementation started — not implemented as one uninterrupted pass.
- [ ] Every layer step was verified (build + tests) immediately after it was written, not batched until the end.
- [ ] No layer step left the system in a broken state that the next layer step depended on silently fixing.
- [ ] Each layer step changes one logical thing — no step mixes new logic for this task with an unrelated refactor or config change.
- [ ] Any deletion-and-replacement was split into separate steps, not combined into one irrevertible change.
- [ ] Anything noticed outside the task's scope was recorded under "Noticed But Not Touching," not fixed inline without being asked.
- [ ] The final task-level verification confirmed the capability is verifiable end-to-end (e.g. through the real endpoint), and every acceptance criterion from `tasks.md` was confirmed true — not just assumed.
- [ ] No speculative abstraction was introduced ahead of the behavior that actually requires it.
- [ ] The task was not mistaken for a stopping point at an intermediate layer — the full vertical slice was completed before moving to a different task.

## Anti-Patterns

| Anti-Pattern                                                                                                                         | Why It Fails                                                                                                                                                                                                                                                                 |
| :----------------------------------------------------------------------------------------------------------------------------------- | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Treating one layer of a task as "done" and moving on to a different task before the current task's capability is complete end-to-end | Mistakes this skill's internal layer ordering for `task-planning-and-breakdown`'s task boundaries — leaves the capability unverifiable by any real caller until someone returns to finish the remaining layers, defeating the purpose of vertical slicing in the first place |
| Implementing an entire task in one uninterrupted pass across all its layers, then running tests once at the end                      | If something breaks, the failure could be anywhere across every layer touched — the precise localization that layer-by-layer verification provides is lost                                                                                                                   |
| Mixing this task's new logic with an unrelated refactor or config update in the same layer step                                      | Makes the diff harder to review, harder to revert cleanly, and harder to bisect later if one of the two turns out to have introduced a regression                                                                                                                            |
| Deleting an old implementation and adding its replacement in the same commit                                                         | Cannot be cleanly reverted to either the old or the intermediate state — split into "remove" and "add" as separate, ordered steps                                                                                                                                            |
| Fixing an unrelated issue noticed mid-task because "it was right there anyway"                                                       | Expands the diff beyond what was asked, conflates two unrelated concerns, and removes the reviewer's ability to evaluate the actual task in isolation — use "Noticed But Not Touching" instead                                                                               |
| Building a generic, configurable abstraction for a behavior that has exactly one current use case                                    | Premature abstraction is harder to verify than the obviously-correct concrete version, and frequently guesses wrong about what the _next_ use case will actually need                                                                                                        |
| Leaving the build broken "temporarily" between two layer steps because "the next one will fix it"                                    | Removes the safety net layer-by-layer building is supposed to provide — if work is interrupted at exactly that point, the codebase is left in a state nobody can safely build on                                                                                             |

## Best Practices

- State the task's full scope (every layer it touches) out loud before starting — a boundary that exists only in your head is not a boundary anyone else can verify you respected, and "I only did the domain layer" is not the same claim as "I finished the task."
- When a task's natural layer ordering isn't obvious, default to the innermost-to-outermost direction (domain/business-logic, then application/orchestration, then infrastructure/persistence, then presentation/interface, or this project's equivalent names).
- Keep each commit's message describing exactly one logical change — if the message needs "and" to describe what changed, the layer step (or the commit) probably needs to be split.
- When uncertain whether something is in scope for the current task, default to _not_ touching it and recording it under "Noticed But Not Touching" — scope creep is far easier to add back deliberately than to un-mix from a tangled diff after the fact.
- Run the cheapest, most targeted verification available after each layer step (e.g. the specific test file just touched) and the full verification suite at task completion — this keeps the feedback loop fast per-layer-step while still catching cross-layer regressions at the end.

## Common Rationalizations

| Rationalization                                                                                     | Reality                                                                                                                                                                                                                                                                                                                        |
| :-------------------------------------------------------------------------------------------------- | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| "I finished the domain layer for this task, that's a good place to stop and pick up the next task." | The task isn't done until its capability is verifiable end-to-end, exactly as `tasks.md` describes — a domain-only stopping point is an internal layer-step checkpoint, not a task completion, and starting a different task here leaves this one's capability unusable by any real caller until someone returns to finish it. |
| "It's faster to write the whole task at once and test it all together."                             | It feels faster in the moment and is slower in practice — a failure at the end now requires bisecting every layer touched to localize, instead of pointing precisely at the one layer step that broke.                                                                                                                         |
| "This refactor is small, I'll just fold it into this layer step's commit."                          | "Small" is not the criterion — _unrelated_ is. A small unrelated change still makes the diff harder to review and impossible to revert independently if either part turns out to be wrong.                                                                                                                                     |
| "I'll build the generic version now since I can already see we'll need it later."                   | "Later" hasn't arrived yet, and the shape of "later" is frequently guessed wrong. The concrete version is also what actually gets verified by the tests you're writing now — the generic version is speculation wearing a confident tone.                                                                                      |
| "I noticed this bug while I was in there, so I just fixed it too."                                  | Fixing it is good; fixing it _silently inside an unrelated diff_ is not. Surface it explicitly so the reviewer can evaluate it as its own change, on its own merits, with its own test.                                                                                                                                        |
| "I'll run the tests once everything's done, running them after every little change wastes time."    | The time "wasted" running tests after each layer step is far smaller than the time spent debugging which of several unverified layers introduced a regression once they're all tangled together.                                                                                                                               |

## Output

This skill does not produce a standalone document artifact. Its output is the implementation itself, delivered as a sequence of small, verified, layer-ordered changes that together complete one task's full vertical slice — each change traceable to a specific layer step of a specific task in the approved plan.

**Worked example:** [`examples/example-layer-build.md`](examples/example-layer-build.md) walks through building task **T1** — "A user can create a reservation for a resource with available capacity, and is correctly rejected when capacity or duplication rules are violated" — from the worked `tasks.md` example in the `task-planning-and-breakdown` skill, layer by layer, all the way to an end-to-end testable capability. Illustrative only, with pseudocode rather than any specific language or framework syntax.
