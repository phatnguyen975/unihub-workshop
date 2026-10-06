---
name: handoff
description: Compact the live thread of the current session — what's in flight, why, and the exact next action — into a single HANDOFF.md snapshot, so a fresh session, a different agent, or a different tool can resume immediately without re-reading the whole conversation. Use on phrases like "hand off", "wrap up this session", "create a handoff", "prepare a handoff for the next session", or a direct invocation of this skill by name.
disable-model-invocation: true
---

# Handoff

## Overview

A session-boundary primitive — compacts work currently in flight into one snapshot a fresh session can read and act on immediately.

- A fresh session either gets nothing (reconstructs context by re-reading or guessing) or everything (a transcript dump burying what matters) — both waste time.
- Produces one artifact — `HANDOFF.md` at the project root — containing only the **live thread**: what's in flight, why, the next step.
- Anything already durable (spec, doc, commit, a project's own decisions record) is referenced by path, never restated.
- A snapshot of a moment, not a log — every run overwrites it completely.

## When to Use

Trigger phrases: direct invocation, "hand off", "wrap this session up", "create a handoff", "prepare this for the next session". Typical situations:

- Switching to a different agent or tool mid-task.
- Ending a session with work still in progress.
- Handing in-flight work to a colleague.
- Forking a side task while the main thread continues elsewhere.
- A long conversation where continuity is worth preserving deliberately.

## When NOT to Use

- **Nothing actually in flight.** Clean end, nothing dangling — say so instead of producing an empty-feeling file.
- **A full history or audit trail is wanted, not a snapshot.** Point to a project's own decisions record instead, if it keeps one.
- **The whole conversation, verbatim, is wanted.** That's a transcript export, not a handoff.

## Input & Invocation

```
handoff
<optional: what the next session will be used for>
```

- **Next-session scope** (required before writing). Not given at invocation? Ask one direct question — "What will the next session pick up?" — not a multi-round interview.
- This scope decides what gets real detail versus a one-line reference.

## Output

One file, `HANDOFF.md`, project root. Doesn't exist → create it. Exists → **overwrite completely**, no append.

```markdown
# Handoff

> **Generated:** <ISO 8601 timestamp with UTC offset>
> **Next session scope:** <one-line, from the human>

## Current State

<1–2 sentences of orientation — stage/shape, not history>

## Done This Session

<bullets — files touched, commands run, tests passed; this session only>

## Remaining

<bullets — scoped to next-session focus; include blockers that affect it even if outside scope>

## Decisions Made This Session

<bullets — only decisions not yet written down anywhere else>

## Exact Next Action

<one concrete, immediately-startable step — a command, or a file plus the specific change; not a direction>

## How to Verify

<one concrete check that confirms it worked>

## References

<paths/URLs/commits holding durable detail — instead of restating it above>

## Gotchas / Blockers

<non-obvious traps found this session — omit the heading if none>

## Redaction Note

<what was redacted, or "nothing found" — never omit>
```

- Already durable somewhere → reference it, don't restate it in the body.
- More than one thread in flight → give each its own Exact Next Action + How to Verify, or ask the human which to prioritize.
- Full example: `references/example-handoff.md`.

## Core Principles

1. **Compaction over completeness.** Already durable → reference it, don't copy it.
2. **Snapshot, not log.** Full overwrite every run — only one current handoff ever exists.
3. **Scope-filtered, not exhaustive.** Next-session focus decides real detail versus a one-line reference.
4. **Redact before writing, every time.** — regardless of repo visibility.
5. **Doesn't know where it's used.** No project/tool/plugin names inside the file; refer to any project-specific record generically, by what it is.

## Workflow

A default flow, not a rigid script. Full detail: `references/compaction-and-redaction.md`.

1. **Get the next-session scope** — from invocation, or ask the one question.
2. **Synthesize the live thread** — current state, done, remaining, decisions, the exact next action(s), how to verify, gotchas.
3. **Compact** — already durable? Reference it, drop the restatement.
4. **Scope-filter** — trim to the declared focus; keep blockers regardless of scope.
5. **Redact** — credentials, tokens, keys, PII. Always fill in the Redaction Note.
6. **Write `HANDOFF.md` at the project root** — full overwrite if it exists.
7. **Ensure it's gitignored** — see Gitignore Handling.
8. **Confirm back to the human** — path, timestamp, one-line summary.

## Gitignore Handling

Two scripts, one per platform — `scripts/ensure-gitignore.sh` (bash), `scripts/ensure-gitignore.ps1` (PowerShell). Both idempotent: safe to run every time, only add the entry if missing.

Before running either script:

1. Confirm you're at the project root — look for `.git`, or ask the human if ambiguous.
2. Pick the script matching the available environment — don't assume based on OS name alone (PowerShell exists on non-Windows; POSIX shells exist inside WSL).
3. Run it against the project root.
4. Verify by reading `.gitignore` back afterward — don't trust a successful exit code alone.

Neither shell usable? Do the same check-then-append logic as a direct file edit — the scripts exist for convenience, not because the logic requires them.

## Anti-Patterns

| Anti-Pattern                                                                                    | Fix                                    |
| ----------------------------------------------------------------------------------------------- | -------------------------------------- |
| Dumping the raw conversation instead of compacting it                                           | Live thread only, not a transcript     |
| Restating content that's already durable                                                        | Reference it instead                   |
| Vague Exact Next Action ("keep working on the feature")                                         | One concrete, startable step           |
| Appending to or merging with a previous `HANDOFF.md`                                            | Full overwrite — snapshot, not a log   |
| Skipping the Redaction Note, or only writing it when something's found                          | Always present, even "nothing found"   |
| Running the gitignore script without confirming project root, or trusting its exit code blindly | Confirm root first; read the file back |
| Naming a specific project, plugin, or tool inside the content                                   | Keep it generic                        |

## Best Practices

- Ask the scoping question upfront — a wrong guess misweights the whole file.
- Keep References dense, Done/Remaining light — point outward more than explain inward.
- ISO 8601 timestamp with UTC offset, so staleness judges correctly across timezones.
- Phrase Exact Next Action so specifically it needs no further interpretation.
- Read `.gitignore` back after running the script — don't trust its success message alone.

## Common Rationalizations to Reject

| Rationalization                                                          | Why it's wrong                                                                                                |
| ------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------- |
| "I'll include everything, just to be safe."                              | Defeats compaction — a bloated handoff costs more reading time than a missing detail. Use References instead. |
| "It's just a local/private repo, redaction doesn't matter here."         | Redact regardless of visibility — a local repo can become shared later, and the check costs nothing.          |
| "I'll append to the existing HANDOFF.md so nothing gets lost."           | Snapshot, not a log — overwrite fully. Anything worth keeping long-term belongs in a durable artifact.        |
| "The script ran without an error, that's good enough."                   | Verify by reading `.gitignore` back — a silent no-op still exits 0.                                           |
| "I can guess what the next session is for from what we were just doing." | Ask, or use what was already stated — a wrong guess over- or under-includes.                                  |

## Verification

Before confirming back to the human, confirm:

- [ ] Every item under Done This Session and Decisions Made This Session was checked against durable artifacts — nothing here duplicates what's already written down elsewhere.
- [ ] Exact Next Action is a single concrete, immediately-actionable step — not a list, not a vague direction.
- [ ] How to Verify names a concrete, checkable action.
- [ ] The Redaction Note is present and explicit — content was actually scanned, not assumed clean.
- [ ] `.gitignore` was read back after running the script (or the equivalent edit) to confirm the entry is actually there.
- [ ] `HANDOFF.md` was fully overwritten, not appended to, if it already existed.
- [ ] If multiple threads were in flight, each has its own Exact Next Action, or the human was asked to prioritize.
- [ ] No specific project, tool, or plugin name appears inside the handoff content.

## Reference Files

- `references/compaction-and-redaction.md` — judging what's "already durable" vs. what belongs in the live thread, plus redaction patterns for credentials, tokens, keys, PII.
- `references/example-handoff.md` — a complete, fully filled-in sample `HANDOFF.md`.
- `scripts/ensure-gitignore.sh` / `scripts/ensure-gitignore.ps1` — idempotent scripts adding the `HANDOFF.md` entry to `.gitignore`. See Gitignore Handling above.
