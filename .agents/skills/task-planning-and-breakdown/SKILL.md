---
name: task-planning-and-breakdown
description: Produces an ordered task list with explicit acceptance criteria and dependency order. Use when an approved spec (and, if applicable, an approved API contract) exists and needs to be decomposed into small, independently implementable, independently verifiable units of work before any code is written. Do not use for a single-file change with obvious scope, or when the work is already small enough to implement in one sitting without a written plan.
---

# Task Planning and Breakdown

## Overview

This skill turns an approved specification into a concrete, ordered list of small tasks — each one scoped to be implementable, testable, and verifiable in a single focused session. Good task breakdown is what separates an agent (or engineer) that delivers a feature reliably, one reviewable slice at a time, from one that disappears for hours and returns with an enormous, unreviewable diff.

This skill assumes a [`spec-driven-development`](../spec-driven-development/SKILL.md) spec already exists and is approved, and an [`api-contract-design`](../api-contract-design/SKILL.md) contract exists and is approved if the feature exposes a network interface. This skill does not invent scope — it decomposes scope that has already been agreed.

This skill produces exactly one artifact: `specs/<feature-name>/tasks.md`.

## When to Use

- An approved spec exists and contains more than one logically distinct piece of work.
- The feature touches more than one architectural layer (e.g. data model, business logic, and an external-facing interface).
- The work is large enough that attempting it as one continuous, unplanned session risks losing track of scope, producing an unreviewable diff, or leaving partial, unverified work in an unclear state if interrupted.

## When NOT to Use

- A single-file change with obviously narrow scope (e.g. fixing one validation rule).
- The spec itself is trivial enough that it already implies one task.
- No spec exists yet — write the spec first (it is the input to this skill, not a substitute for it).

## Core Principles

1. **Plan in read-only mode.** This skill produces a planning document, never implementation code. Writing code while "planning" defeats the purpose of having a separate, reviewable plan — the human cannot evaluate a plan that has already been partially executed before they saw it.
2. **A task is a vertical slice, never a horizontal layer.** This is the single most important rule in this skill. A task is organized around one complete, testable **capability** — one thing a caller can actually do and verify end-to-end — and it cuts through every architectural layer that capability touches (commonly something like domain logic, orchestration, persistence, and the interface that exposes it). A task is never "the data model for this feature," "the business logic for this feature," and "the API for this feature" as three separate tasks — that is horizontal slicing, and it is explicitly rejected by this skill. See **Vertical vs. Horizontal Slicing** section below for why this matters and what it looks like in practice.
3. **Tasks are sized for one focused session, not one calendar day.** A task that cannot realistically be implemented, tested, and verified in a single sitting is too large — split it. Split along capability boundaries (one task per distinct, independently testable capability), never by carving a single capability apart into its constituent layers. (See **Sizing Tasks** section below for a concrete sizing model.)
4. **Order follows dependency between capabilities, not convenience.** Tasks are sequenced so each one can build on a capability already proven to work — e.g. "create a resource" before "cancel a reservation against that resource," because the second capability's tests need the first one to already exist and work. This is dependency between _slices_, not a layer-by-layer build order within one slice (that ordering is `incremental-implementation`'s concern, not this skill's).
5. **Every task has explicit, falsifiable acceptance criteria, stated as externally observable behavior.** A task that says "implement the feature" or "handle the edge cases" without stating exactly what "done" looks like is not yet a task — it's still a vague intention wearing a checkbox. Because every task is a vertical slice, its acceptance criteria should be verifiable the way a real caller would verify them (e.g. "calling this endpoint with this input produces this response"), not "this internal function returns this value" — that level of detail belongs to the tests written _during_ implementation, not to the task's own acceptance criteria.
6. **Acceptance criteria sit on top of a project-wide Definition of Done, never replacing it.** Acceptance criteria answer "did this task build the _right_ thing" (traced back to the spec). The Definition of Done answers "is this task _actually finished_" regardless of which task it is — tests pass, nothing else broke, the behavior was actually verified to run, not just to compile. Every task must clear both bars.
7. **Written plans outlive context windows.** A plan that exists only in conversation is lost the moment the session ends, compacts, or hands off to a different agent or engineer. The plan document is what survives — write it as if the next reader has no memory of this conversation.

## Vertical vs. Horizontal Slicing

**Horizontal slicing** (rejected by this skill) organizes tasks by architectural layer: one task for the data model, a separate task for the business logic, a separate task for persistence, a separate task for the interface — all for the _same_ capability. This looks organized, but it has a serious cost: nothing is actually testable end-to-end, and nothing actually works from a caller's perspective, until every one of those layer-tasks is complete. If the persistence task turns out to have a problem, the fix has to ripple forward into whatever was already built on top of it — by which point the API task may already be "done." A defect discovered late this way is expensive precisely because of how much was built on an unverified foundation underneath it.

**Vertical slicing** (required by this skill) organizes tasks by capability instead: one task delivers "create a reservation," end-to-end, through every layer it needs — and at the end of that one task, that capability genuinely works and can be verified the way a real caller would verify it (calling the actual endpoint, not just calling an internal function directly). A second, independent capability — "cancel a reservation" — is a _different_ task, not a continuation of layer-building for the first one.

```text
    HORIZONTAL (rejected)         │    VERTICAL (required)
──────────────────────────────────│─────────────────────────────────────
  Task 1: Data model              │  Task 1: Create a reservation
    (all capabilities)            │    (domain → application →
  Task 2: Business logic          │    infrastructure → interface,
    (all capabilities)            │    for THIS ONE capability,
  Task 3: Persistence             │    end-to-end testable)
    (all capabilities)            │
  Task 4: API layer               │  Task 2: Cancel a reservation
    (all capabilities)            │    (domain → application →
                                  │    infrastructure → interface,
  Nothing is end-to-end testable  │    for THIS ONE capability,
  until Task 4 is done.           │    end-to-end testable)
                                  │
                                  │  Each task is independently
                                  │  end-to-end testable as soon
                                  │  as it's done.
```

This does not mean every task duplicates effort — see **Handling Shared Foundations** section below for how genuinely shared groundwork (e.g. a domain value object several capabilities will use) fits into this model without becoming a horizontal "layer task" in disguise.

This skill decides **where the boundaries between tasks are** (by capability). The internal build order _within_ one task — which layer to write first, in what sequence — is the concern of `incremental-implementation`, not this skill. The two skills divide the problem cleanly: this skill answers "what is a task," that skill answers "how do I build one task correctly."

## Handling Shared Foundations

Some pieces of work are genuinely shared groundwork that more than one capability will depend on — a value object two different use cases both need, a schema migration two different endpoints both read from. This is _not_ an exception to vertical slicing; it's handled by recognizing that the **first** capability that needs that shared piece builds it as part of its own vertical slice, and every _later_ capability that also needs it simply depends on that first task being complete, rather than rebuilding it. Do not invent a separate "shared foundation" task that has no caller-observable behavior of its own — every task should still be traceable to some capability a user/caller actually cares about. If a genuinely large piece of shared groundwork has no sensible owner among the capabilities that need it, treat that as a sign the spec or the architecture itself may need attention (a question for `spec-driven-development` or this project's own architecture documentation) rather than as a task to add here without grounding it in an actual capability.

## Workflow

### Step 1 — Confirm Inputs Exist and Are Approved

Locate `specs/<feature-name>/SPEC.md`. If it does not exist or is not `APPROVED`, stop — this skill has nothing authoritative to decompose. If the feature exposes a network interface, also locate `specs/<feature-name>/API_CONTRACT.md` and confirm it is `APPROVED`.

### Step 2 — Identify the Distinct Capabilities

Before drafting tasks, list out every distinct, independently meaningful capability the spec (and contract, if any) requires — something a real caller could exercise and verify on its own, end to end. A useful test: could you describe this capability's acceptance criteria as "when a caller does X, Y happens and they observe Z" without referencing any internal implementation detail? If yes, it's a capability-shaped unit suitable for one task. If the natural description of "what this delivers" is actually a layer ("the persistence for something") rather than a caller-observable outcome, it's not a capability on its own — it belongs _inside_ a capability's task, not as a separate task (see **Handling Shared Foundations** section).

Also identify, while reading the project's own architecture documentation, which layers each capability is expected to pass through in this project (commonly something like domain/business-logic, data access, and presentation/interface — exact names vary by project). This informs `incremental-implementation`'s internal build order for each task later — it does not become the basis for _this_ skill's task boundaries.

### Step 3 — Decompose Into Tasks

For every capability identified in Step 2:

1. State the task in one sentence, as the capability a caller gets: what they can now do, end to end, that they couldn't before.
2. Confirm the task is a full vertical slice — it touches every layer this capability genuinely needs, and at the end of the task, the capability is verifiable the way a real caller would verify it (e.g. by actually calling the endpoint), not only by calling an internal function directly.
3. Size it per **Sizing Tasks** section below. If a single capability's slice is too large for one sitting, look for a smaller, still-end-to-end-testable capability inside it to peel off as its own earlier task (e.g. "create a reservation, free case only" before "create a reservation, paid case with payment integration") — never resolve oversized-ness by reverting to a horizontal, layer-only split.
4. Write its acceptance criteria as externally observable behavior, traced back to the spec's Functional Requirements / Acceptance Criteria (or the contract's endpoint definitions, if this task implements one).
5. List the files this task is expected to touch across every layer it passes through, as a scope boundary — not an exhaustive guarantee, but a way for a reviewer to immediately sense if implementation drifted beyond what was planned, and a visible confirmation that the task does in fact span multiple layers rather than just one.
6. Note explicit dependencies: which earlier task(s) — i.e. which earlier _capabilities_ — this one requires to already exist and work.

### Step 4 — Group Into Phases (by Risk or Dependency, Not by Layer)

Where a plan has many tasks, phases are still useful for organizing checkpoints — but a phase groups related _capabilities_, ordered by dependency or by risk (the riskiest or most uncertain capability first, so a problem surfaces while it's still cheap to address), never by architectural layer. After each phase, add a **checkpoint** — a short, explicit condition that must hold before moving to the next phase (e.g. "all tasks in this phase pass their acceptance criteria and the build is clean"). A checkpoint is not a new task; it's a gate confirming the phase is actually solid before building further capabilities on top of it.

### Step 5 — Surface Risks and Open Questions

Identify anything in the spec or contract that introduces implementation risk (concurrency, an external dependency, an ambiguous edge case that was deliberately deferred during spec review) and record it with its likely impact and a mitigation approach. Separately, list any question that genuinely needs human input before or during implementation — do not silently resolve an open question by guessing during planning.

### Step 6 — Self-Review Against the Quality Checklist

Run the draft against [`resources/QUALITY_CHECKLIST.md`](resources/QUALITY_CHECKLIST.md) before presenting it.

### Step 7 — Present and Stop

Present `tasks.md` using [`resources/TASKS_TEMPLATE.md`](resources/TASKS_TEMPLATE.md). Call out any task that required an assumption about ordering or scope, and every open question that needs a human answer.

**Then stop.** A human must review the plan for correct ordering, scope, and granularity before implementation of any task begins. This skill never starts implementing the first task itself.

## Sizing Tasks

Use a simple relative sizing model — the exact unit (story points, hours, "number of files") matters far less than the discipline of consciously sizing every task before accepting it into the plan. Because every task is a vertical slice, its file count naturally spans multiple layers — that's expected, not a sign the task is too big on its own.

| Size           | Rough Scope                                                                                                                                        | Typical File Count (across all layers) | Agent/Engineer Fit                              |
| :------------- | :------------------------------------------------------------------------------------------------------------------------------------------------- | :------------------------------------- | :---------------------------------------------- |
| **S** (Small)  | One small, complete capability with a thin path through each layer it touches (e.g. a simple read endpoint with no business rules beyond a lookup) | 3–5                                    | Ideal — high confidence, easy to review         |
| **M** (Medium) | One coherent capability with real business logic in at least one layer (e.g. a mutation with a validation rule and a side effect)                  | 5–8                                    | Good — still reviewable as a single diff        |
| **L** (Large)  | A capability that bundles more than one distinct caller-observable behavior, or has unusually deep logic in multiple layers at once                | 9+                                     | **Too large — split before adding to the plan** |

**Rule:** If a task is sized L or larger, look for a smaller, still-fully-vertical capability inside it to split off first (see Step 3.3) — never resolve an oversized task by flattening it into per-layer pieces. A plan containing an L-sized task, or a plan that "solves" sizing by reverting to horizontal layer tasks, is not a finished plan.

## Quality Checklist

Condensed version — full checklist lives in [`resources/QUALITY_CHECKLIST.md`](resources/QUALITY_CHECKLIST.md):

- [ ] The feature's `SPEC.md` (and `API_CONTRACT.md`, if applicable) exists and is `APPROVED`
- [ ] No code was written during planning — the output is a document only
- [ ] Every task is a complete vertical slice through every layer the capability needs — no task is scoped to a single architectural layer
- [ ] Every task is sized S or M; no L-or-larger task remains unsplit, and no task was "downsized" by flattening it into per-layer pieces
- [ ] Tasks are ordered by dependency between capabilities — a task never depends on a capability that a later task is supposed to provide
- [ ] Every task has explicit, falsifiable acceptance criteria stated as externally observable behavior, traced back to the spec/contract
- [ ] Every task lists its expected file scope across every layer it touches
- [ ] Every task's dependency on earlier tasks (capabilities) is stated explicitly
- [ ] Each phase groups related capabilities (by risk or dependency, not by layer) and ends with a checkpoint condition
- [ ] Risks are listed with impact and mitigation; open questions are listed for the human, not silently resolved
- [ ] The agent presented the plan and stopped — it did not begin implementing task 1 without explicit human approval

## Anti-Patterns

| Anti-Pattern                                                                                                               | Why It Fails                                                                                                                                                                                                                                                                                                   |
| :------------------------------------------------------------------------------------------------------------------------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Splitting a feature into per-layer tasks ("data model," "business logic," "API") instead of per-capability vertical slices | Nothing is end-to-end testable until every layer-task for that capability is complete; a defect discovered late in the sequence (e.g. in persistence) ripples forward into work already considered "done" on top of it — this is the horizontal-slicing anti-pattern this skill specifically exists to prevent |
| Writing code while "planning," to "get a head start"                                                                       | Inverts the purpose of having a separate, reviewable plan — the human can no longer evaluate the plan before work against it has already begun                                                                                                                                                                 |
| A task that says "implement the feature" or "handle remaining cases"                                                       | Provides no falsifiable acceptance criteria; two different implementers (or the same agent on two different days) would interpret it differently                                                                                                                                                               |
| Ordering tasks by what seems easiest or most interesting to do first, ignoring dependency between capabilities             | Produces rework when a later "easy" capability turns out to depend on a capability that should have been built first                                                                                                                                                                                           |
| Accepting an L-sized task into the plan "because it's all one logical thing"                                               | "One logical thing" and "one reviewable, testable unit of work" are not the same claim — bundling them produces an unreviewable diff and a single point of failure for the whole phase                                                                                                                         |
| "Solving" an oversized task by splitting it into per-layer pieces instead of finding a smaller vertical sub-capability     | Trades one anti-pattern for another — the task is now smaller, but it's no longer end-to-end testable on its own, which defeats the entire reason for slicing vertically in the first place                                                                                                                    |
| Silently resolving an ambiguous edge case during planning instead of flagging it                                           | The plan is not the place to make a business decision the spec left open — that decision belongs back in spec review, with the human, not buried in a task's fine print                                                                                                                                        |
| Treating the plan as a one-time artifact, written once and never revisited                                                 | If implementation reveals the plan was wrong (a task was missing, an order was infeasible), the plan should be visibly updated — not silently abandoned while the agent improvises a different sequence no one approved                                                                                        |

## Best Practices

- Write each task's acceptance criteria as something a different person could verify without asking the implementer what they meant — and phrase them as what a caller observes, not as internal implementation detail.
- When in doubt, prefer a smaller, still-fully-vertical capability over a larger one — the cost of an extra checkpoint is far lower than the cost of a stalled L-sized task discovered to be unreviewable halfway through, and far lower than the cost of reverting to horizontal layer tasks just to make the size manageable.
- When a task's file list grows past what its size category implies, look first for a smaller vertical sub-capability to split off, and treat "just remove a layer from this task's scope" as the wrong kind of split.
- Keep the "Architecture Decisions" section of the plan focused on decisions specific to _this_ plan's capability boundaries and sequencing — broader architectural decisions belong in the project's own decision-record documentation, not duplicated here.
- When in doubt about whether something is a risk worth recording or just normal implementation uncertainty, record it. A risk that turns out to be a non-issue costs one unused line; an unrecorded risk that materializes costs a surprised reviewer mid-implementation.

## Common Rationalizations

| Rationalization                                                                              | Reality                                                                                                                                                                                                                                                                    |
| :------------------------------------------------------------------------------------------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| "It's cleaner to do all the database work first, then all the business logic, then the API." | This is horizontal slicing by another name — nothing is end-to-end testable until every layer is done, and a problem found in an earlier layer ripples forward into work already built on top of it. Slice by capability instead; each one stays independently verifiable. |
| "This task is mostly mechanical, it doesn't need real acceptance criteria."                  | "Mostly mechanical" is exactly the kind of task where a missing edge case slips through silently — a falsifiable criterion costs one sentence and catches it.                                                                                                              |
| "I'll just start coding and write the plan as I go, it'll be faster."                        | This is the planning equivalent of writing tests after the code — the plan ends up describing whatever was convenient to build, not what was deliberately decided, and the human never got the chance to review before work started.                                       |
| "This task is large, but splitting it would create artificial-feeling boundaries."           | A boundary that lets you commit, test, and review independently is not artificial — it's the entire value of having a plan. If a clean split genuinely isn't possible, look for a smaller vertical sub-capability rather than falling back to a per-layer split.           |
| "I can resolve this open question myself, it's obviously what the spec intended."            | If it were obvious, it wouldn't have been an open question. Silently resolving it removes the human's chance to catch a wrong assumption while it's still cheap to fix — flag it instead.                                                                                  |

## Output

**Location:** `specs/<feature-name>/tasks.md`

**Template:** [`resources/TASKS_TEMPLATE.md`](resources/TASKS_TEMPLATE.md)

**Worked example:** [`examples/example-tasks.md`](examples/example-tasks.md) shows a complete, filled-in task plan for the same domain-neutral "resource reservation with capacity limit" feature used as the worked example in the `spec-driven-development` and `api-contract-design` skills — illustrative only, not tied to any specific project's domain, language, or framework.
