---
name: code-review-and-quality
description: Runs a structured six-axis review — correctness, design, security, performance, test coverage, and style — and classifies every finding by severity so the human reviewer knows what's blocking versus what's optional. Use before merging any change, after a feature's implementation tasks are complete, or when evaluating code written by another agent or engineer. Do not use as a substitute for the implementation's own test suite; this skill reviews what was built, it does not verify behavior the way a test does.
---

# Code Review and Quality

## Overview

A review that just says "looks good" or silently rewrites half the diff without explaining why is not a review — it's either a rubber stamp or an unreviewable surprise. This skill structures a review into six explicit axes, sizes the change being reviewed so it's actually reviewable, and classifies every finding by how blocking it is, so a human can act on the output in minutes rather than re-deriving the reviewer's reasoning from scratch.

The standard this skill applies is pragmatic, not perfectionist: a change should leave the codebase in a measurably better state than before it — not be flawless. Holding every change to a perfection standard is what causes reviews to stall indefinitely or get rubber-stamped out of reviewer fatigue; neither outcome serves the codebase.

## When to Use

- Before merging any change.
- After a feature's implementation tasks (per `tasks.md`, if this project uses `task-planning-and-breakdown`) are all complete.
- Evaluating code written by another agent, another engineer, or a previous session — any code you didn't just personally finish writing and already know intimately.

## When NOT to Use

- As a substitute for running the test suite. This skill reviews what was built and how; it does not execute the code's behavior the way `test-driven-development`'s tests do. Both are needed; neither replaces the other.

## Core Principles

1. **The bar is "better," not "perfect."** A change that measurably improves the codebase's health should be approved, even if it isn't the reviewer's own ideal version of the change. Holding every change to a perfection standard either stalls delivery indefinitely or trains reviewers (human or agent) to stop reviewing carefully because nothing ever clears the bar anyway.
2. **Reviewability is a property of the change's size, not just its content.** A change that's too large to hold in working memory gets skimmed, not reviewed — and a skimmed review catches nothing. Size the change deliberately (see **Change Sizing** section below) rather than treating "the diff is what it is."
3. **Every finding gets a severity, not just a description.** A reviewer who lists ten observations with no indication of which ones block merge forces the recipient to guess — and under time pressure, the guess is usually wrong in one direction or the other. Severity is not optional polish; it's what makes the review actionable.
4. **Six axes, every time, not whichever ones feel relevant today.** Correctness, design, security, performance, test coverage, and style are reviewed as six distinct lenses on the same change — skipping an axis because "this change obviously doesn't touch that" is itself a judgment call that deserves to be made explicitly, not silently assumed.
5. **A review references the artifacts that define "correct," not just the reviewer's own opinion.** Correctness is evaluated against the feature's approved spec/contract (if one exists), not against a reviewer's unstated mental model of what the code should do.
6. **Findings describe the problem and its impact, not just an instruction to change something.** "Change X to Y" without explaining what breaks if it stays as X teaches the recipient nothing and invites a debate about taste instead of a fix grounded in a concrete consequence.

## The Six Axes

Every review evaluates the change through six explicit lenses.

### 1. Correctness

Does the change actually do what it's supposed to do?

- Does the implementation satisfy the feature's approved spec (Functional Requirements, Business Rules, Acceptance Criteria) — see `spec-driven-development` — not just "does it run without error"?
- Are edge cases and error paths handled, not just the happy path?
- Does the change do what its own commit message / task description claims it does, with no silent scope expansion or contraction?
- For a bug fix specifically: does it address the root cause, or does it patch the symptom in a way that could resurface under a slightly different input? (See `debugging-and-root-cause-analysis`.)

### 2. Design

Does the change fit the codebase's intended structure, or does it fight it?

- Does the change respect the project's architectural layering (e.g. Domain/Application/Infrastructure/Presentation, or this project's equivalent) — see `incremental-implementation` and `test-driven-development` for what belongs at each layer?
- Does any cross-module interaction go only through a module's declared public surface — see `modular-boundary-enforcement`? A change that reaches into another module's internals is a design problem regardless of whether it happens to work.
- Is the abstraction level appropriate — neither a speculative generalization for a need that doesn't exist yet, nor a copy-pasted near-duplicate that should have been a shared, well-named piece of logic?
- Does the change introduce or worsen tech debt without acknowledging it, where a small amount of extra effort would have avoided it entirely?

### 3. Security

Does the change introduce or fail to address a security risk?

- If the change touches authentication, authorization, payment, webhook handling, file upload, or any external input — was `security-and-hardening` actually applied, not just "probably fine"?
- Is every external input validated at the boundary, not assumed valid deeper in the call stack?
- Is every privileged action protected by an explicit, server-side authorization check?
- Are secrets, keys, and credentials kept out of source code, logs, and the diff itself?

### 4. Performance

Does the change introduce avoidable performance risks?

Apply `performance-and-efficiency-engineering` when the change touches: database queries, collection processing, external service calls, caching, frequently executed paths, operations that scale with data volume. Check for: N+1 query patterns, missing or ineffective index support, unbounded result processing, blocking I/O in latency-sensitive paths, unnecessary repeated computation, algorithmic complexity issues.

Performance findings should describe the concrete impact, such as increased query count, degraded response time as data grows, or unnecessary resource consumption.

### 5. Test Coverage

Does the change have the right tests, at the right layer, proving the right things?

- Does test coverage match the architectural layer the change lives in — e.g. a Domain-layer change has Domain-layer unit tests with zero mocking, an Infrastructure-layer change has tests verifying query/mapping logic with the client mocked — per this project's `test-driven-development` policy?
- Was the test actually written _before_ the implementation (Red before Green), where that discipline is the project's stated practice — and if there's no way to tell from the diff alone, was this confirmed with whoever wrote it rather than assumed?
- Does the test suite cover the failure/edge cases identified during spec review, not only the happy path?
- For a bug fix: is there a regression test that fails before the fix and passes after?
- Did the full surrounding test suite (not just the new tests) get run and confirmed passing — the Beyoncé Rule from `test-driven-development`?

### 6. Style

Is the change consistent with how the rest of the codebase is written?

- Naming: do identifiers describe intent, consistent with this codebase's existing naming conventions — not a one-off convention introduced just for this change?
- Formatting: does the change match the project's established formatting (ideally enforced by an automated formatter, in which case this check is "did the formatter actually run," not a manual line-by-line read)?
- Are comments explaining _why_, where the _why_ isn't obvious from the code itself — not restating _what_ the code already says plainly?
- Is dead code, commented-out code, or a leftover debug statement present anywhere in the diff?

## Change Sizing

A review's quality degrades sharply as the size of what's being reviewed grows — past a certain size, a reviewer (human or agent) stops reading carefully and starts skimming, which defeats the purpose entirely.

| Size       | Rough Line Count          | Reviewability                                                                                                                                     |
| :--------- | :------------------------ | :------------------------------------------------------------------------------------------------------------------------------------------------ |
| Ideal      | ~100 lines changed        | Fully reviewable in one focused pass                                                                                                              |
| Acceptable | Up to a few hundred lines | Reviewable, but worth checking whether it could have been split                                                                                   |
| Too large  | 1000+ lines changed       | Not reviewable as a single unit — split before requesting review, or review must be explicitly scoped to sub-sections with that limitation stated |

If a change under review is in the "too large" range, say so explicitly as a finding in its own right (see **Anti-Patterns** section) — don't silently attempt a thorough review of something too large to actually hold in working memory and call the result complete.

## Severity Labels

Every finding gets exactly one of these labels. A review with findings but no labels is not a finished review.

| Label          | Meaning                                                                                                                                                                              | Blocks Merge?                                                            |
| :------------- | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :----------------------------------------------------------------------- |
| **Critical**   | A correctness, security, or data-integrity defect that will cause real harm if merged as-is                                                                                          | Yes                                                                      |
| **Should-fix** | A real problem (design debt, missing test coverage, a style violation that will compound) that should be addressed, but doesn't make the change actively harmful if deferred briefly | Generally yes, unless the human reviewer explicitly accepts deferring it |
| **Nit**        | A minor, optional polish suggestion — naming, formatting, a slightly cleaner alternative                                                                                             | No                                                                       |
| **FYI**        | Not a request for a change at all — context, an observation, or a heads-up for the future                                                                                            | No                                                                       |

## Workflow

### Step 1 — Confirm Reviewability

Check the change's size against **Change Sizing** section. If it's too large to review as a single unit, say so as the review's first finding and either request a split or explicitly scope the review to what can actually be covered in this pass.

### Step 2 — Gather the Reference Artifacts

Locate the feature's approved `SPEC.md` and `API_CONTRACT.md` (if any), the relevant `tasks.md` entry, and this project's own architecture/structure and coding-convention documentation. These are what "correct" and "consistent with this codebase" are measured against — not the reviewer's own unstated assumptions.

### Step 3 — Review Each of the Six Axes

Work through **Correctness** , **Design**, **Security**, **Performance**, **Test Coverage**, and **Style** sections in turn, deliberately — not in whatever order findings happen to surface while reading. For any axis with no findings, note that explicitly (this project's `review-notes.md` convention should have an entry per axis, even if it's "No issues found").

### Step 4 — Classify Every Finding

Assign each finding exactly one severity label from **Severity Labels** section. State the specific problem and its concrete impact — not just an instruction with no rationale.

### Step 5 — Write the Review Output

Use [`resources/REVIEW_TEMPLATE.md`](resources/REVIEW_TEMPLATE.md). Summarize the finding counts by severity at the top, so a human can gauge the change's overall state before reading the detail.

### Step 6 — Self-Review Against the Quality Checklist

Run through [`resources/QUALITY_CHECKLIST.md`](resources/QUALITY_CHECKLIST.md) before presenting the review.

### Step 7 — Present and Stop

Present the review. **Do not apply any fix.** This skill produces findings for a human to confirm or dispute; applying a fix is a separate, explicit step (in this project's pipeline, that's `debugging-and-root-cause-analysis` invoked via a dedicated fix step — not this skill silently rewriting the code it just reviewed).

## Quality Checklist

Condensed version — full checklist lives in [`resources/QUALITY_CHECKLIST.md`](resources/QUALITY_CHECKLIST.md):

- [ ] The change's size was checked against the reviewability guidance, and flagged explicitly if too large.
- [ ] The feature's spec/contract/task artifacts were located and used as the correctness reference — not the reviewer's own assumption of intended behavior.
- [ ] All six axes were reviewed deliberately, with an explicit "no issues found" recorded for any axis with nothing to report.
- [ ] Every finding has exactly one severity label.
- [ ] Every finding states the concrete impact, not just an instruction.
- [ ] No fix was applied by this skill — only findings were produced.
- [ ] The review's summary states the finding count by severity before the detail.

## Anti-Patterns

| Anti-Pattern                                                                                          | Why It Fails                                                                                                                                                                                                                      |
| :---------------------------------------------------------------------------------------------------- | :-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Reviewing a 1000+ line change as if it were reviewable in one pass                                    | Produces the illusion of review coverage without the substance — findings will reliably miss things a properly-sized review would have caught                                                                                     |
| Listing findings with no severity label                                                               | Forces the recipient to guess what's actually blocking — under time pressure, the guess is wrong often enough that this defeats the purpose of having a review at all                                                             |
| Giving an instruction ("rename this") with no stated reason                                           | Invites a debate about taste instead of a fix grounded in a concrete, demonstrable consequence; also teaches the recipient nothing for next time                                                                                  |
| Holding every change to a "this could be even better" standard with no severity distinction           | Either stalls merges indefinitely or trains everyone to stop taking the Should-fix/Nit distinction seriously, since everything gets treated as equally blocking                                                                   |
| Silently rewriting the code instead of producing a finding for the human to act on                    | Removes the human's chance to understand and confirm the issue before a fix is applied — and conflates "review" with "fix," which are deliberately separate steps in this skill                                                   |
| Reviewing correctness against the reviewer's own mental model instead of the approved spec/contract   | Two different reviewers (or the same reviewer on two different days) will reach different conclusions about what "correct" means, because there's no shared reference being checked against                                       |
| Skipping the Security or Test Coverage axis because "this change is mostly about styling/refactoring" | The exact kind of change most likely to accidentally introduce a missing-validation or now-untested edge case — a "mostly cosmetic" change is not exempt from any axis without that exemption being a deliberate, stated decision |

## Best Practices

- State the axis-by-axis structure explicitly in the output, even when an axis has nothing to report — a silently-skipped axis is indistinguishable from a forgotten one to the person reading the review.
- When a Critical finding is identified, lead with it — don't bury the most important finding under a long list of Nits.
- Prefer pointing at the specific line(s) and the specific consequence over a general statement like "this could be improved" — specificity is what makes a finding actionable rather than merely true.
- When reviewing code written by an AI agent specifically, pay particular attention to whether tests were genuinely written test-first (often invisible from the diff alone — worth asking, not assuming) and whether the change silently expanded beyond its stated task scope (see `incremental-implementation`'s scope-boundary discipline).
- Treat a "no issues found" axis as a real, positive statement worth recording — not as nothing to write down. It tells the reader the axis was actually checked, not omitted.

## Common Rationalizations

| Rationalization                                                                                  | Reality                                                                                                                                                                                                                                                              |
| :----------------------------------------------------------------------------------------------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| "This change is small, a full six-axis review is overkill for it."                               | Size affects how _much_ there is to review per axis, not whether the axis applies. A two-line change to an authorization check still needs the Security axis walked — it just won't take long.                                                                       |
| "I'll just fix the obvious issues myself instead of writing them up as findings."                | Silently fixing what you were asked to review removes the human's chance to understand and confirm the issue, and conflates two deliberately separate steps (review, then fix) into one the human never approved skipping.                                           |
| "Everything in this diff is basically fine, I don't need to assign severities."                  | "Basically fine" is not the same claim as "zero findings of any kind" — and even a review with zero findings should say so explicitly per axis, rather than a vague overall impression.                                                                              |
| "The tests are there, so Test Coverage is covered, I don't need to check what layer they're at." | A test that exists is not the same as a test at the _correct_ layer testing the _right_ thing — a Domain-layer rule re-verified only by an end-to-end-style test, with nothing at the Domain layer itself, is a coverage gap this axis exists specifically to catch. |
| "This 1500-line diff is what got submitted, I'll just do my best."                               | Doing your best against an unreviewable size produces a review that feels thorough and isn't — flagging the size issue explicitly is more valuable than a confident-sounding but necessarily incomplete pass.                                                        |

## Output

**Location:** `specs/<feature-name>/review-notes.md` (when reviewing a feature against its own spec artifacts), or presented inline when reviewing an ad hoc change with no associated feature spec.

**Template:** [`resources/REVIEW_TEMPLATE.md`](resources/REVIEW_TEMPLATE.md)

**Worked example:** [`examples/example-review.md`](examples/example-review.md) shows a complete, filled-in review of the `CreateReservationUseCase` slice from the `incremental-implementation` skill's worked example — continuing the domain-neutral "resource reservation" example used across the other skill examples. Illustrative only, not tied to any specific project's domain, language, or framework.
