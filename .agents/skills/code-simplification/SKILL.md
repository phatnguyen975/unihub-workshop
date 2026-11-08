---
name: code-simplification
description: Simplifies code for clarity and maintainability without changing its observable behavior. Use when refactoring code for readability, when code works but is harder to read, extend, or debug than it should be, when a code review flags complexity or duplication, or when consolidating logic that accumulated under time pressure. Do NOT use for adding features, fixing bugs, performance tuning, architectural rewrites, or on code with no test coverage.
metadata:
  author: phatnguyen975
  version: 1.0
---

# Code Simplification

## Overview

Reduce code complexity while preserving behavior exactly. The goal is not fewer lines — it's code a teammate can read, understand, modify, and debug faster than the original. A change that doesn't make comprehension faster isn't a simplification, regardless of how much shorter or more "idiomatic" it looks. Every simplification should pass one test: "Would someone new to this code understand it faster than the original?"

## When to Use

- Code works and tests pass, but the implementation is heavier than it needs to be
- A code review flags readability, duplication, or complexity issues
- Deeply nested logic, long functions, or unclear naming is slowing comprehension
- Code written under time pressure needs cleanup before further work continues
- Related logic has drifted into duplicated or inconsistent forms across files

## When NOT to Use

- The code is already clean — simplifying for its own sake adds risk with no benefit
- The code's behavior isn't understood yet — comprehend it first, simplify second
- The code in scope has no test coverage — there's nothing to verify behavior against, so this is a hard stop, not a judgment call
- The code is performance-critical and a "simpler" version would be measurably slower
- The module is about to be rewritten entirely — simplifying throwaway code wastes effort
- The task's actual goal is a feature, a bug fix, or a performance optimization — those are different goals with different trade-offs, and pursuing them risks changing behavior

## Input

- **Scope** (`scope`, required): the file(s), function(s), or module(s) to simplify. Nothing outside this set is touched.
- **Test command** (`test_command`, required): the command that runs the tests covering the code in scope (e.g. `npm test`, `pytest path/`, `go test ./...`, `gradlew test`). Used to verify behavior after every change.
- **Project conventions** (`project_conventions`, optional): a style guide, linter config, or project instructions file (e.g. `CLAUDE.md`, `AGENTS.md`). If absent, infer convention from neighboring code.

## Output

1. **Simplified code**, applied directly to the files in `scope`, one verified change at a time.
2. **One HTML completion report** per run, built from `assets/report-template.html`, this is the artifact a human reviews.

## Core Principles

1. **Preserve behavior exactly.** A change that alters any observable behavior is not a simplification, however much cleaner it looks. Before keeping any change, confirm:
   - Does this produce the same output for the same input?
   - Does this maintain the same error behavior for the same failure cases?
   - Does this preserve the same side effects (writes, network calls, mutations) and their order?
   - Do all existing tests still pass without modification?
2. **Understand before touching.** Don't change or remove code whose purpose isn't established — if you can't explain why a fence is there, don't tear it down. Before touching anything, answer:
   - What is this code's responsibility?
   - What calls it, and what does it call?
   - What are its edge cases and error paths?
   - Are there tests that define the expected behavior?
   - Why might it have been written this way (performance, a platform constraint, a historical reason)? Check history (e.g. blame/log) if unclear.
3. **No test coverage, no simplification.** Behavior preservation is only verifiable against something. If the code in scope isn't covered by `test_command`, stop and report that back, do not proceed on inspection alone.
4. **Clarity over cleverness.** Simplicity means faster comprehension, not fewer lines or denser syntax. A change that needs a mental pause to parse isn't a simplification even when it's shorter.
5. **Convention first, principles second.** The project's own conventions outrank general heuristics like KISS, DRY, and YAGNI, and outrank general language idioms too.
   ```markdown
   1. Read `CLAUDE.md` / `AGENTS.md` / Project conventions
   2. Study how neighboring code handles similar patterns
   3. Match the project's style for:
      - Import ordering and module system
      - Function declaration style
      - Naming conventions
      - Error handling patterns
      - Type annotation depth
   ```
   Simplification that breaks project consistency is not simplification — it's churn.
6. **Bounded scope.** Touch only code already in `scope`. No drive-by refactors of adjacent files, however tempting — unrequested scope expansion is the most common failure mode in agentic refactoring.
7. **Incremental, reversible steps.** One simplification at a time, verified before the next. Never batch unverified changes — if something breaks, the cause must be unambiguous.
8. **Avoid over-simplification.** Merging unrelated logic into one function, inlining a helper that gave a concept a name, or deleting an abstraction that exists for extensibility are failures of this skill, not successes.
9. **Refactor and behavior-change are separate changes.** A change that does both is two changes, not one — never ship them together.

## Complexity Signals

Scan for these patterns — each one is a concrete signal, not a vague smell:

**Structural complexity:**

| Pattern                    | Signal                             | Simplification                                                            |
| -------------------------- | ---------------------------------- | ------------------------------------------------------------------------- |
| Deep nesting (3+ levels)   | Hard to follow control flow        | Extract conditions into guard clauses, early returns, or helper functions |
| Long functions (50+ lines) | Multiple responsibilities          | Split into focused functions with descriptive names                       |
| Nested ternaries           | Requires mental stack to parse     | Replace with if/else chains, switch, or lookup objects                    |
| Boolean parameter flags    | `doThing(true, false, true)`       | Replace with options objects or separate functions                        |
| Repeated conditionals      | Same `if` check in multiple places | Extract to a well-named predicate function                                |

**Naming and readability:**

| Pattern                    | Signal                                         | Simplification                                                           |
| -------------------------- | ---------------------------------------------- | ------------------------------------------------------------------------ |
| Generic names              | `data`, `result`, `temp`, `val`, `item`        | Rename to describe the content: `userProfile`, `validationErrors`        |
| Abbreviated names          | `usr`, `cfg`, `btn`, `evt`                     | Use full words unless the abbreviation is universal (`id`, `url`, `api`) |
| Misleading names           | Function named `get` that also mutates state   | Rename to reflect actual behavior                                        |
| Comments explaining "what" | `// Increment counter` above `count++`         | Delete the comment — the code is clear enough                            |
| Comments explaining "why"  | `// Retry because the API is flaky under load` | Keep these — they carry intent the code can't express                    |

**Redundancy:**

| Pattern                   | Signal                                                       | Simplification                                            |
| ------------------------- | ------------------------------------------------------------ | --------------------------------------------------------- |
| Duplicated logic          | Same 5+ lines in multiple places                             | Extract to a shared function                              |
| Dead code                 | Unreachable branches, unused variables, commented-out blocks | Remove (after confirming it's truly dead)                 |
| Unnecessary abstractions  | Wrapper that adds no value                                   | Inline the wrapper, call the underlying function directly |
| Over-engineered patterns  | Factory-for-a-factory, strategy-with-one-strategy            | Replace with the simple direct approach                   |
| Redundant type assertions | Casting to a type that's already inferred                    | Remove the assertion                                      |

**The Rule of 500:** If the planned change set exceeds roughly 500 lines, prefer automation (codemods, sed scripts, AST transforms) over manual edits — manual changes at that scale are error-prone and hard to review.

## Workflow

1. **Define scope.** State the exact files/functions in scope. Nothing outside this list gets touched during this run.
2. **Verify the safety net.** Run `test_command` against the code in scope.
   - Passing tests cover it → continue to Step 3.
   - No coverage, or no `test_command` available → **STOP.** Produce the completion report marked as blocked, recommending test coverage before this skill is run again (characterization tests, if none exist at all, per Michael Feathers' definition: tests that pin down current behavior before it is changed). Do not write those tests yourself.
3. **Understand.** Work through Principle 2's questions for anything non-obvious before proceeding.
4. **Identify opportunities.** Match the code in scope against the Complexity Signals above. For idioms, naming, and anything specific to the language in scope: the project's own convention always wins — follow it wherever it exists (style guide, linter config, project conventions, or the patterns already used in neighboring code). Where the project has no convention of its own, fall back to that language's own widely-accepted standards and real-world best practices.
5. **Plan and confirm with the human.** Summarize the proposed changes: what will change, why, and the expected risk level for each. Present this plan before touching any code.
   - If feedback comes back, revise the plan and re-confirm if the revision is material.
   - Proceed to execution only once the plan is approved.
6. **Apply incrementally.** One simplification at a time → run `test_command` (and build/lint if available) → keep the change if it passes, revert and reconsider if it doesn't, before moving to the next. Avoid batching multiple simplifications into a single untested change. If something breaks, you need to know which simplification caused it.
7. **Self-verify.** Run the full Verification below against the final diff — read it as a reviewer would, not just by checking boxes. If the "simplified" version is harder to follow than the original, revert it; not every attempt succeeds.
8. **Generate the completion report.** Read `assets/report-template.html`, do not redesign the layout; fill the placeholders and duplicate the marked repeating blocks per change or file. Before writing the file, confirm the save location with the human — propose `docs/code-simplification-<scope-slug>-<YYYYMMDD>.html` as the default and show it to them; save there if they accept it, or at whatever path or filename they give instead.

## Anti-Patterns

- **Needing to modify a test to make it pass** → the simplification likely changed behavior, not just structure
- **Simplifying code you don't fully understand** → read more first (Principle 2)
- **Scope creep** → simplifying nearby code "while you're here" because it's ugly; flag it instead
- **Unverifiable changes** → proceeding on code with no test coverage (Principle 3) or skipping `test_command` between steps
- **Batching** → applying several simplifications before verifying any of them; if something breaks, the cause becomes ambiguous
- **Behavior drift disguised as style** → changing an equality operator, error-wrapping call, default argument, or type coercion "for consistency" without recognizing it changes behavior
- **Renaming to personal taste** → renaming that reflects the agent's preference rather than project convention (Principle 5)
- **Silent large-diff growth** → a pass that keeps finding "one more thing" well past the task's natural boundary, instead of stopping and reporting
- **Mixing refactor with behavior change** → shipping a simplification and a fix/feature in the same change (Principle 9)
- **Removing error handling "to make it cleaner"** → error handling is behavior, not decoration
- **Inconsistent renaming** → renaming a symbol at its definition without updating every call site atomically

## Common Rationalizations to Reject

| Rationalization                               | Reality                                                                                                        |
| --------------------------------------------- | -------------------------------------------------------------------------------------------------------------- |
| "It's working, no need to touch it"           | Readability debt compounds — but this doesn't license expanding scope elsewhere                                |
| "Fewer lines means simpler"                   | Comprehension speed is the metric, not line count                                                              |
| "I'll just clean up this nearby code too"     | Scope creep — the leading failure mode in agentic refactoring                                                  |
| "The original author must have had a reason"  | Check (git blame, tests, comments), don't assume either way                                                    |
| "This abstraction might be useful later"      | Speculative generality is complexity without current value (YAGNI)                                             |
| "It's shorter, so it must be correct"         | Run the full Verification; brevity isn't proof of correctness                                                  |
| "The types make it self-documenting"          | Types document structure, not intent — a well-named function still carries meaning a signature doesn't         |
| "I'll refactor while I'm adding this feature" | Mixed changes are harder to review, revert, and understand in history later — keep them separate (Principle 9) |

## Verification

Run before producing the completion report. Any unchecked item blocks a "pass" status in the report.

- [ ] All existing tests pass unmodified (`test_command` green)
- [ ] Build/lint/type-check clean, no new warnings
- [ ] No error handling removed or weakened
- [ ] No new behavior or edge-case handling introduced
- [ ] No dead code left behind (unused imports, unreachable branches)
- [ ] Diff touches only files declared in `scope`
- [ ] Each simplification was applied and verified independently, not batched
- [ ] The human-approved plan was followed; any deviation is explained in the report
- [ ] No language-specific behavior-preserving trap was triggered (coercion/equality rules, mutable defaults, closure capture, error-wrapping semantics, and similar gotchas for the language in scope)
- [ ] Read as a whole, the diff is a net improvement a reviewer would approve — genuinely easier to understand, not just shorter
