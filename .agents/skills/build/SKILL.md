---
name: build
description: Implement one task from an approved plan, layer by layer, test-first. Run once per task in tasks.md, in order.
---

# /build — Implement One Task

## Usage

```
/build <feature-name> <task-id>
```

Run once per task listed in `specs/<feature-name>/tasks.md`, in the order the plan specifies. `<task-id>` must refer to a task already present in an `APPROVED` `tasks.md`.

## Preconditions

`specs/<feature-name>/tasks.md` must exist and be `APPROVED`. The specific `<task-id>` must exist in it. If either fails, stop and direct the human back to `/plan`.

## What This Command Does

Skills invoked, conditionally — evaluate each one against the current task's actual content, not reflexively:

| Skill                                    | Always runs? | Condition                                                                                               |
| :--------------------------------------- | :----------- | :------------------------------------------------------------------------------------------------------ |
| `incremental-implementation`             | Yes          | Every task                                                                                              |
| `test-driven-development`                | Yes          | Every task — Red → Green → Refactor, strictly per this project's testing policy                         |
| `modular-boundary-enforcement`           | Conditional  | Task involves a cross-module call or a new module-level class                                           |
| `database-schema-evolution`              | Conditional  | Task adds/modifies a table, column, index, or constraint                                                |
| `idempotency-and-exactly-once-design`    | Conditional  | Task is a mutation with retry risk (payment, registration, webhook, batch sync)                         |
| `security-and-hardening`                 | Conditional  | Task touches auth, RBAC, payment, webhook, file upload, or any external input                           |
| `performance-and-efficiency-engineering` | Conditional  | Task touches a database query, a collection loop with per-item I/O, or an external/slow dependency call |

Step-by-step instructions for completing a task:

1. **Confirm the task's full scope** per its entry in `tasks.md`: the capability it delivers, every layer it touches, its acceptance criteria, and its expected file list. This is the task's boundary — do not silently expand or narrow it.
2. **Order this task's layers by dependency** (innermost first — commonly domain, then application, then infrastructure, then presentation, or this project's equivalent names) per `incremental-implementation`.
3. Read the task's acceptance criteria. Load relevant context (existing code, patterns, types).
4. **Build each layer step using Red-Green-Refactor** per `test-driven-development`: a failing test exists before the layer step's production code does. Verify the build and the relevant test suite immediately after each layer step — not batched until the end.
5. **Apply conditional skills exactly where their condition is met** — e.g. run `database-schema-evolution`'s migration discipline only for the specific layer step that touches schema, not for the whole task reflexively.
6. **Complete every layer step for this task before considering it done.** The task is not finished after one or two layers — it is finished when its capability is verifiable end-to-end, exactly as its acceptance criteria in `tasks.md` describe (e.g. through the real endpoint, not only through an internal unit test).
7. **Run the full unit test suite** before reporting the task complete — including this project's static module-boundary check, if it has one, as part of that same suite run.
8. Mark the task as complete and stop if the acceptance criteria are met.

## Handling Mid-Build Discoveries

**If a test fails unexpectedly** (not a deliberate Red step), apply `debugging-and-root-cause-analysis` inline before continuing: reproduce → localize → hypothesize → fix the root cause → add a regression test. This is not a formal review finding; resolve it within the task.

**If you identify a way the current approach in `SPEC.md`, `API_CONTRACT.md`, `tasks.md`, or the project's own documentation could be improved** — a better design, a stronger pattern, or a constraint discovered only during implementation — stop coding immediately. Do not implement the improvement first and explain it afterward. Instead:

1. Write the proposal into `specs/<feature-name>/improvement-proposal.md`, stating explicitly **what** would change and in which file(s), and **why** — a concrete, verifiable justification (a cited best practice, an existing pattern already used elsewhere in this codebase, a documented limitation discovered while coding, or a direct conflict with an existing decision record). A proposal with no verifiable justification, or based only on stylistic preference, must not be submitted.
2. **Gate 1 — Wait for the human to Approve or Reject the proposal.**
   - Reject → Discard the proposal, continue implementing exactly per the existing approved spec. No files are changed.
   - Approve → Proceed to Gate 2. Do not touch any code yet.
3. **Gate 2 — Update every affected file in `specs/<feature-name>/` and every affected file in the project's documentation (usually in `/docs`), in the same pass**, then present the diff and wait for the human to Approve or Reject it.
   - Reject → Correct per the human's feedback and re-present. Repeat until approved.
   - Approve → The updated artifacts are now the source of truth. Resume coding the current task **only if** the approved change does not require a new task. If it requires a new task (or changes the existing task breakdown), update `tasks.md` accordingly and **stop** — do not start a new task on your own initiative. The human must explicitly invoke `/build <feature-name> <new-task-id>`.

There is no separate "sync docs" step later — this two-gate process is the only mechanism that keeps specs and project documentation consistent with the implementation, and it happens at the moment drift is discovered, not deferred.

## Human Verification Gate

Not a hard gate between every sub-step within a single task, but the human is expected to review the diff after each task before the next one starts. Do not silently start the next task without this being clear in your output — end your turn after reporting a task complete, rather than immediately invoking `/build` again for the next task.

## Notes for the Agent

- Stay within this task's declared file scope. If something unrelated is noticed along the way, record it under "Noticed But Not Touching" (per `incremental-implementation`) rather than fixing it inline.
- Never weaken an existing security control, test, or architectural boundary check to make this task's build pass.
- Run this task's relevant quality checklist(s) — from `incremental-implementation` and `test-driven-development` at minimum — before reporting the task complete.
