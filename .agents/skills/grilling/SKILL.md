---
name: grilling
description: Interview the user through structured, round-based questioning to resolve ambiguity in a plan, decision, or idea before any artifact, spec, or code gets created. Use proactively when a request is underspecified or has a fork with more than one reasonable implementation, or when the user asks to be "grilled", "interviewed", or to "stress-test" a plan. Not for a single one-off clarifying question — this is structured, multi-round elicitation.
---

# Grilling

A structured interview primitive for resolving ambiguity before it turns into rework. It asks good questions in an efficient order and refuses to accept vague answers — it does not know or care what plan it's interviewing about, what tool invoked it, or where its output should end up.

## Overview

Most rework in software and planning tasks doesn't come from bad execution — it comes from an assumption that was never checked out loud. This skill exists to surface those assumptions before an artifact is built on top of them, using a technique borrowed from how good engineers actually run design reviews: map every open decision as a tree, only ask about the part of the tree you can currently answer without guessing, and batch related questions together instead of trickling them out one at a time.

The skill produces exactly one deliverable: a **Resolution Summary** — a clean, structured record of every question asked and every decision made. What happens to that summary afterward (saved to a file, appended to a log, discarded) is entirely up to whatever invoked this skill. This skill never decides that on its own and never writes files itself.

## When to Use

- **Self-invoked, before creating an artifact.** After reading a request (a spec, a ticket, a design task, a feature request), if you notice unresolved forks, unquantified requirements, or a point where you'd otherwise have to silently pick one of several reasonable interpretations — invoke this skill before producing the artifact, not after.
- **User-invoked, explicit request.** The user says things like "grill me on this", "interview me before we start", "stress-test this plan", "make sure we're not missing anything", or directly invokes `grilling`.
- **Any time confidence in "what the user actually wants" is meaningfully below certain** — not just confidence in what they literally typed, but in the underlying goal behind it.

## When NOT to Use

- **The request has no genuine fork.** If there is exactly one reasonable way to interpret and execute the request, asking anyway just adds friction. Reserve this skill for real forks, not for busywork clarifications a single follow-up sentence would settle.
- **The answer is discoverable, not a judgment call.** If the answer already exists in the codebase, the attached documents, or a tool you have access to, look it up instead of asking a human to repeat it. Only grill about things that genuinely require a human decision. See `references/decision-tree-and-frontier.md` for how to tell the two apart.
- **The user has explicitly asked for speed over rigor.** If the user has signaled urgency or has explicitly said to skip clarification and just proceed, respect that — but still surface, in your normal response, what you assumed and why, so nothing is silently buried.
- **A grilling session on this exact subject already produced an answer that still applies.** Don't re-interview a decision that was already resolved and hasn't changed; confirm it still applies in one line instead of re-running the whole tree.
- **The task is trivial or fully mechanical.** A one-line fix, a direct factual question, or a task where any competent execution converges on the same output doesn't need this.

## Input & Invocation

There's no formal parameter schema — invocation is one flag plus free-form text:

```
grilling --mode=<normal|comprehensive>
<subject — whatever should be interviewed about, in plain language>
```

_(Or the equivalent natural-language request — e.g. "grill me thoroughly" → `comprehensive`, "grill me" with no qualifier → `normal`)_

**`--mode`** (optional, default `normal`):

- **`normal` (default):** Ask Blocking and Shaping items. Cosmetic items are auto-defaulted, not asked, but always declared under "Auto-Defaulted" in the summary.
- **`comprehensive`:** Ask everything, including Cosmetic items — typically in the final rounds, after the higher-priority items are settled, so they don't dilute focus early on.
- **When self-invoked (no explicit user request for grilling), always default to `normal`.** Self-invocation means you're interrupting some other task to clarify it — keep that interruption as light as reasonably possible. Only escalate to `comprehensive` when the user explicitly asks for it, mid-session or up front.

**Subject** (required): everything else is the subject — the plan, request, decision, or idea to interview about, written in plain language. There's nothing else to fill in; any already-known constraints, prior decisions, or a narrower focus are simply part of that text, not a separate field.

- **When a human invokes this skill**, the subject is whatever they typed along with the invocation (e.g., "grill me on this: we're adding a notification feature...").
- **When self-invoked**, there's no external subject being handed to you — invoking this skill is itself the conclusion of analysis you already did on the task in front of you (that analysis is exactly what triggers a "When to Use" match in the first place). The subject is that task, described using what you already know from the request and conversation so far. Don't fabricate a structured hand-off between "the analysis" and "the skill" — they're the same continuous reasoning, just move straight into Workflow step 1 with what you've already established.

## Output

Two output shapes exist, at two different points in the session:

**1. A Round** — the in-session, transient unit of interaction. Every round follows the same numbered format so it can be produced and answered reliably every time, without inventing a new structure per session. Full format and examples: `references/decision-tree-and-frontier.md`.

**2. The Resolution Summary** — the final deliverable, produced once when the session ends. Always use this exact structure:

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

Omit the "Auto-Defaulted" table entirely in `comprehensive` mode, or if nothing was defaulted. Omit "Still Open" if the tree fully resolved. Never omit "Resolved Decisions" if at least one round ran.

This is the only artifact this skill produces. It does not decide whether this gets written to a file, appended to a log, or just left in the conversation — that's the caller's call.

## Core Principles

1. **Never ask a question whose prerequisite isn't settled yet.** If answering question B requires knowing the answer to question A, don't ask B until A is resolved — you'd just be asking the user to guess on your behalf. This is the single most important rule; see `references/decision-tree-and-frontier.md`.
2. **Batch over trickle.** Ask every question you _can_ currently ask in one round, not one at a time. This respects the user's time and is dramatically more efficient than sequential single questions for anything beyond a trivial number of decisions.
3. **No silent assumptions.** Every decision that matters (Blocking or Shaping — see below) must get an explicit human answer. Anything auto-defaulted must still be declared in the Resolution Summary, never buried.
4. **Concrete over convention.** An answer like "the standard way" or "make it scalable" is not a resolved decision — it's a placeholder wearing a decision's clothes. Push once for specifics before accepting it as resolved. See `references/quality-filters.md`.
5. **Look it up before you ask.** If a question is really a research task (an existing convention, a fact in the codebase or docs) rather than a genuine judgment call, resolve it yourself first. Only spend the user's attention on things only they can decide.
6. **This skill doesn't know where it's being used.** Never reference a specific project name, tool, plugin, workflow, filename, or "who invoked me" inside a question or the summary. Keep everything scoped to the subject at hand.

## Workflow

Full mechanics, worked examples, and edge cases live in `references/decision-tree-and-frontier.md` and `references/quality-filters.md`. This is the condensed sequence:

1. **Frame the hypothesis.** Before asking anything, state in one short paragraph what you currently believe the user wants and why. This is cheap insurance — if the whole premise is off, the user corrects it in one shot instead of you burning several rounds refining details of the wrong thing. Wait for confirmation (or a correction) before continuing.
2. **Build the decision tree.** Walk the subject and mechanically extract every point of ambiguity or fork. For each one, determine what it depends on (so you know the asking order) and tag it with a priority: **Blocking** (can't proceed at all without it), **Shaping** (changes the outcome significantly), or **Cosmetic** (nice to know, low impact). Full step-by-step procedure: `references/decision-tree-and-frontier.md`.
3. **Compute the frontier and ask a round.** The frontier is every node whose dependencies are already resolved — the questions you can ask right now without guessing. In `normal` mode, exclude Cosmetic nodes from the frontier entirely; in `comprehensive` mode, include them (typically pushed to later rounds so they don't crowd out higher-priority questions). Ask the whole frontier as one numbered round, each question with a short recommended default attached, so the user can answer quickly by confirming or overriding individual numbers.
4. **Process the answers.** For each answer: check whether it's actually concrete or whether it's a buzzword / deferred-to-convention / hedged non-answer (`references/quality-filters.md`); if vague, push once more for specifics before marking it resolved. Once resolved, update the tree — this may unblock new nodes (now askable) or make other planned nodes irrelevant (prune them).
5. **Repeat.** Recompute the frontier and ask the next round. Continue until the frontier is empty for the active mode, or the user signals they're done.
6. **Handle early stop gracefully.** If the user says to stop before the tree is fully resolved, don't argue — but do list exactly what's left unresolved in the summary under "Still Open" so nothing disappears silently.
7. **Emit the Resolution Summary.** Use the exact template above. This ends the session.

## Anti-Patterns

- **Asking one question at a time when a full round is answerable.** This is slower and more tiring for the user than it needs to be — always batch the frontier.
- **Asking about a node whose prerequisite is still open.** This forces the user to guess at something you haven't told them yet. Wait for the frontier to include it.
- **Quietly picking an answer for a Blocking or Shaping item because it "seemed obvious."** If it's genuinely obvious, it's a fast question to ask, not a good reason to skip asking it.
- **Accepting "the standard/best-practice way" as a final answer.** That's a direction, not a decision. Ask what it concretely means in this context before moving on.
- **Defaulting to `comprehensive` mode when self-invoking.** This turns a lightweight clarification into a long interrogation the user didn't ask for.
- **Continuing past a user's stop signal, or stopping without declaring what's left open.** Either extreme breaks trust — respect the stop, but never let unresolved items vanish without a trace.
- **Naming the calling project, tool, or plugin inside a question.** This skill's questions and summary should read the same whether it's invoked from a coding agent, a writing tool, or a standalone chat.

## Best Practices

- Attach a short recommended default to every question — it turns "answer this open-ended question" into "confirm or override this specific pick", which is faster for the user and produces replies that are easier to parse unambiguously.
- Cap round size (roughly 5–7 questions) and prioritize Blocking before Shaping if the frontier is larger than that, splitting into sub-rounds rather than overwhelming the user in one block.
- Prefer writing out the decision tree structure explicitly (even briefly, in your own reasoning) rather than trying to hold it implicitly — this makes the mechanical steps in `references/decision-tree-and-frontier.md` consistently reliable rather than dependent on getting an abstract instruction right in your head.
- Re-state the hypothesis before diving into rounds, every time — it's cheap and prevents the most expensive failure mode (grilling the wrong thing in exhaustive detail).
- When in doubt about whether something is a genuine judgment call or a lookup-able fact, check available context or tools first; only ask if that comes up empty.

## Common Rationalizations to Reject

These are excuses that sound reasonable in the moment but quietly defeat the point of this skill. Watch for yourself reaching for one of these:

| Rationalization                                                                    | Why it's wrong                                                                                                                                                                                             |
| ---------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| "The request seems clear enough, I can just proceed."                              | "Seems clear" is a feeling, not a check. If you can name more than one reasonable interpretation, it isn't clear — that's exactly what this skill is for.                                                  |
| "Asking would slow the user down."                                                 | One well-batched round is almost always faster than the rework caused by a wrong assumption. Batching (Core Principle 2) is what keeps this cheap.                                                         |
| "I'll just pick sensible defaults for everything and mention it later."            | Fine for Cosmetic items in `normal` mode — and only if declared in the summary. Not fine for Blocking or Shaping items; "later" often means "after the artifact is already built on the wrong assumption." |
| "The user already answered something similar earlier in this conversation."        | Confirm it still applies to this subject — don't assume a past answer to a different question transfers. If it clearly does apply, say so and skip re-asking, but check first.                             |
| "This is a minor detail, not worth its own question."                              | Size of the sentence needed to ask isn't the same as size of the impact. If it's tagged Shaping or Blocking, its priority — not its word count — decides whether it gets asked.                            |
| "The user gave a vague answer, but pushing again feels pedantic."                  | A vague answer isn't a resolved decision, it's an unresolved one wearing different clothes. One follow-up for specifics is not pedantic — silently guessing what they meant is the actual risk.            |
| "This plan sounds like it follows common best practice, so I don't need to check." | "Best practice" is a direction with many valid concrete implementations. Naming the practice isn't the same as picking one; that's still an open decision.                                                 |

## Verification

Before presenting the Resolution Summary, confirm:

- [ ] Every Blocking and Shaping node has an explicit, concrete answer — not a buzzword, deferred-to-convention, or hedged non-answer.
- [ ] No question was asked whose prerequisite was still unresolved at the time.
- [ ] In `normal` mode, every Cosmetic item is either absent from the questioning or logged under "Auto-Defaulted" with the default applied — none silently dropped without a trace.
- [ ] If the session ended early, every remaining unresolved item is listed under "Still Open", not omitted.
- [ ] The summary follows the exact template structure and names no specific project, tool, or plugin.
- [ ] The mode used matches how the session was invoked (self-invoked sessions default to `normal` unless the user explicitly asked otherwise).

## Reference Files

- `references/decision-tree-and-frontier.md` — the full step-by-step procedure for extracting ambiguity, tagging priority, building dependency edges, computing the frontier, and formatting a round. Includes a worked example. Read this before running the Workflow section above for the first time, or whenever unsure how to prioritize or sequence questions.
- `references/quality-filters.md` — how to detect vague, buzzword, or deferred-to-convention answers, and how to follow up without being annoying about it.
