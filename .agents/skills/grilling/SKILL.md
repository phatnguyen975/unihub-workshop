---
name: grilling
description: Interview the user through structured, round-based questioning to resolve ambiguity in a plan, decision, or idea before any artifact, spec, or code gets created. Use proactively when a request is underspecified or has a fork with more than one reasonable implementation, or when the user asks to be "grilled" or "interviewed" about an already-chosen plan. Not for a single one-off clarifying question — this is structured, multi-round elicitation.
---

# Grilling

## Overview

A structured interview primitive for resolving ambiguity before it turns into rework — asks good questions in an efficient order, refuses vague answers.

- Rework usually traces back to an assumption nobody checked out loud.
- Surfaces those assumptions first: map open decisions as a tree, ask only what's answerable without guessing, batch related questions into rounds instead of trickling them out.
- Produces one deliverable — a **Resolution Summary** (every question asked, every decision made).
- What happens to it after (saved, logged, discarded) is the caller's call — this skill never writes files, never decides that.

## When to Use

Trigger phrases: "grill me", "interview me before we start", "make sure we're not missing anything", or a direct invocation. Typical situations:

- A request is underspecified, or has a fork with more than one reasonable implementation.
- About to produce a spec, design, or plan where a non-trivial assumption would otherwise be silently made.
- Confidence in what's actually wanted is meaningfully below certain — not just the literal words, the underlying goal.

## When NOT to Use

- **No genuine fork.** One reasonable interpretation exists — asking just adds friction.
- **Answer is discoverable, not a judgment call.** Look it up (codebase, docs, tools) instead. See `references/decision-tree-and-frontier.md`.
- **User explicitly wants speed over rigor.** Respect it — but still surface your assumptions in the normal response.
- **Already resolved, unchanged.** Confirm it still applies in one line instead of re-running the tree.
- **Trivial or fully mechanical task.** Any competent execution converges on the same output.

## Input & Invocation

```
grilling --mode=<normal|comprehensive>
<subject — what to interview about, in plain language>
```

_(Or natural language: "grill me thoroughly" → `comprehensive`; "grill me" → `normal`)_

**`--mode`** (optional, default `normal`):

- `normal` — Blocking + Shaping only; Cosmetic items auto-defaulted, declared under "Auto-Defaulted".
- `comprehensive` — everything, Cosmetic items usually pushed to later rounds.
- Not specified → `normal`. Use `comprehensive` only when explicitly asked for.

**Subject** (required) — the plan, request, or idea to interview about, in plain language: whatever was stated with the invocation, or the task already being analyzed. No separate fields; known constraints or a narrower focus are just part of that text. No fabricated hand-off — go straight into Workflow.

## Output

Two shapes:

1. **A Round** — transient, in-session. Same numbered format every time. Full format: `references/decision-tree-and-frontier.md`.
2. **Resolution Summary** — the final deliverable, produced once:

```markdown
## Grilling Summary — <subject>

> **Mode:** normal | comprehensive
> **Rounds:** <N>

### Resolved Decisions

| #   | Question   | Decision       | Priority                      |
| --- | ---------- | -------------- | ----------------------------- |
| 1   | <question> | <final answer> | Blocking / Shaping / Cosmetic |

### Auto-Defaulted

| #   | Item   | Default Applied  | Why                                              |
| --- | ------ | ---------------- | ------------------------------------------------ |
| 1   | <item> | <default chosen> | Cosmetic, normal mode — flag for review if wrong |

### Still Open

| #   | Item   | Why It's Unresolved                        |
| --- | ------ | ------------------------------------------ |
| 1   | <item> | User asked to stop before this was reached |
```

- Omit "Auto-Defaulted" in `comprehensive` mode, or if nothing was defaulted.
- Omit "Still Open" if fully resolved.
- Never omit "Resolved Decisions" once a round ran.

## Core Principles

1. **Never ask ahead of its prerequisite.** If B needs A's answer, don't ask B first — that's asking the user to guess on your behalf. The one rule with zero flexibility here.
2. **Batch over trickle.** Everything askable right now goes in one round.
3. **No silent assumptions.** Blocking/Shaping always get an explicit answer; auto-defaults get declared, never buried.
4. **Concrete over convention.** "The standard way" isn't a decision, it's a placeholder. Push once for specifics. See `references/quality-filters.md`.
5. **Look it up before you ask.** A research task doesn't belong in a round — resolve it yourself first.

## Workflow

A default flow, not a rigid script — use judgment at each step. Full mechanics: `references/decision-tree-and-frontier.md`, `references/quality-filters.md`.

1. **Frame the hypothesis.** One short paragraph: what you believe the user wants, and why. Wait for confirmation before continuing — cheap insurance against burning rounds on the wrong premise.
2. **Build the decision tree.** Identify forks and ambiguity, note dependencies, tag priority (Blocking / Shaping / Cosmetic).
3. **Compute the frontier, ask a round.** Frontier = nodes whose dependencies are resolved. `normal` excludes Cosmetic; `comprehensive` includes it, usually later. One numbered round, each question with a short recommended default.
4. **Process answers.** Concrete, or a buzzword/hedge in disguise (`references/quality-filters.md`)? Push once if vague. Update the tree — answers can unblock nodes, prune others, or surface a fork you hadn't seen yet.
5. **Repeat** until the frontier is empty or the user signals done.
6. **Stop gracefully.** Early stop → list what's left under "Still Open", nothing vanishes silently.
7. **Emit the Resolution Summary.** Ends the session.

## Anti-Patterns

| Anti-Pattern                                                             | Fix                                                       |
| ------------------------------------------------------------------------ | --------------------------------------------------------- |
| Asking one question at a time when a full round is answerable            | Always batch the frontier                                 |
| Asking about a node whose prerequisite is still open                     | Wait until the frontier includes it                       |
| Quietly picking an answer because it "seemed obvious"                    | If it's obvious, it's a fast question — ask it anyway     |
| Accepting "the standard/best-practice way" as final                      | Push for what it concretely means here                    |
| Defaulting to `comprehensive` unprompted                                 | Default is `normal`; escalate only on explicit request    |
| Continuing past a stop signal, or stopping without declaring what's open | Respect the stop; always list what's left in "Still Open" |
| Naming the calling project, tool, or plugin in a question                | Keep everything scoped to the subject only                |

## Best Practices

- Attach a short recommended default to every question — faster to answer, easier to parse.
- Cap rounds around 5–7 questions; split by priority (Blocking, then Shaping) if the frontier is bigger.
- Write the tree structure out explicitly, even briefly — more reliable than holding it implicitly.
- Re-state the hypothesis every session, before diving into rounds.
- Check available context/tools before asking — only ask what a lookup can't answer.

## Common Rationalizations to Reject

| Rationalization                                                               | Why it's wrong                                                                                                                           |
| ----------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------- |
| "The request seems clear enough, I can just proceed."                         | "Seems clear" is a feeling, not a check. More than one reasonable interpretation = not clear.                                            |
| "Asking would slow the user down."                                            | One well-batched round beats the rework from a wrong assumption.                                                                         |
| "I'll just pick sensible defaults for everything, mention it later."          | Fine for Cosmetic in `normal` mode, if declared. Not fine for Blocking/Shaping — "later" often means after the artifact's already wrong. |
| "The user already answered something similar earlier."                        | Confirm it still applies — don't assume a past answer transfers.                                                                         |
| "This is a minor detail, not worth its own question."                         | Priority decides this, not word count.                                                                                                   |
| "The user's answer was vague, but pushing again feels pedantic."              | A vague answer isn't resolved — it's unresolved in disguise. One follow-up isn't pedantic.                                               |
| "This sounds like it follows common best practice, so I don't need to check." | Naming a practice isn't picking a concrete implementation of it.                                                                         |

## Verification

Before presenting the Resolution Summary, confirm:

- [ ] Every Blocking/Shaping node has an explicit, concrete answer — not a buzzword, hedge, or deferred-to-convention non-answer.
- [ ] No question was asked whose prerequisite was still unresolved.
- [ ] In `normal` mode, every Cosmetic item is either unasked-but-logged under "Auto-Defaulted," or absent entirely — none silently dropped.
- [ ] Early stop → every remaining item is listed under "Still Open."
- [ ] The summary follows the exact template, names no specific project/tool/plugin.
- [ ] Mode is `normal` unless `comprehensive` was explicitly requested.
