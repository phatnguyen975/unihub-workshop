---
name: handoff
description: Compact the live thread of the current session — what's in flight, why, and the exact next action — into a single HANDOFF.md snapshot at the project root, so a fresh session, a different agent, or a different tool can resume immediately without re-reading the whole conversation. Use on phrases like "hand off", "wrap up this session", "create a handoff", "prepare a handoff for the next session", or a direct invocation of this skill by name.
disable-model-invocation: true
---

# Handoff

A session-boundary primitive: it takes everything relevant about the work currently in flight and compacts it into one snapshot file that a fresh session can read and immediately act on. It doesn't know what project it's running in, what other skills or tools exist around it, or what happens to the file after it's written beyond being kept out of git.

## Overview

The failure mode this prevents isn't "forgetting things" — it's the opposite: a fresh session either gets nothing (and has to reconstruct context by re-reading a sprawling conversation, or by guessing) or gets everything (a full transcript dump that buries the one thing that actually matters under a pile of settled, already-documented history). Both waste the next session's time.

`handoff` produces exactly one artifact — `HANDOFF.md` at the project root — containing only the **live thread**: what's currently in flight, why, and the one concrete next step. Anything already captured somewhere durable (a spec, a design doc, a commit, a project's own running record of decisions, if it keeps one) is referenced by path, never restated. The file is a snapshot of a moment, not a growing log — every invocation overwrites it completely.

## When to Use

Trigger phrases: direct invocation, or wording like "hand off", "wrap this session up", "create a handoff", "prepare this for the next session", "I'm switching tools, hand off first". Typical situations:

- Switching to a different agent or tool partway through a task.
- Ending a session for now with work still in progress, to be picked up later.
- Handing the in-flight work to a colleague.
- Forking off a side task while the human continues the main thread elsewhere.
- The conversation is getting long and continuity should be preserved deliberately before it's lost.

## When NOT to Use

- **Nothing is actually in flight.** If the session ended cleanly with no open thread (a finished, merged piece of work, nothing dangling), there's nothing to compact — say so instead of producing an empty-feeling file.
- **The human wants a full history or audit trail, not a snapshot.** This skill deliberately drops anything already recorded elsewhere; it is not a substitute for a project's own running decisions or requirements record, if it keeps one. Point to that instead.
- **The human wants the whole conversation preserved verbatim.** That's a transcript export, not a handoff — a handoff is a compaction by design, not a copy.

## Input & Invocation

```
handoff
<optional: what the next session will be used for>
```

- **Next-session scope** (optional at invocation, but required before writing): what the upcoming session will focus on. If the human didn't state it when invoking, ask exactly one direct question before doing anything else — e.g. "What will the next session pick up?" This is not a multi-round interview; get the one answer and proceed.
- This scope is what makes the output small and relevant instead of a dump: it decides what stays in the file at meaningful detail versus what gets reduced to a one-line reference.

## Output

One file, `HANDOFF.md`, written to the project root. If it doesn't exist, create it. If it already exists, **overwrite it completely** — this is a snapshot of the current moment, not a running log, so the old content is stale by definition once a new one is written.

Use this exact structure. Each heading below includes what to put there and how to synthesize it — this isn't filler to replace with your own judgment call about format, only about content:

```markdown
# Handoff

> **Generated:** <ISO 8601 timestamp with UTC offset, e.g. 2026-09-11T14:32:00+07:00>
> **Next session scope:** <one-line restatement of what the human said the next session is for>

## Current State

## Done This Session

## Remaining

## Decisions Made This Session

## Exact Next Action

## How to Verify

## References

## Gotchas / Blockers

## Redaction Note
```

- **Generated** — always the actual current timestamp, with a UTC offset (not just a bare date). This is what lets a resuming session judge staleness — if the codebase has moved on since, the gap is visible immediately.
- **Current State** — one or two sentences of orientation: what stage or shape the work is currently in. Someone with zero prior context should understand the situation in about ten seconds from this alone. Not a history — a snapshot.
- **Done This Session** — a bullet list of work completed in _this session only_, not the project's entire history. Be concrete: name the files touched, the commands run, the tests that passed. If something is already fully described in a durable place (a commit message, a merged spec), don't restate it here — cover it under References instead.
- **Remaining** — a bullet list of what's left, scoped to the declared next-session focus. Include anything that would block that focus even if it falls slightly outside it. Don't dump the whole project backlog — only what's relevant to resuming this specific thread.
- **Decisions Made This Session** — only decisions settled _in this session_ that aren't written down anywhere else yet. If a decision already lives in a spec, ADR, commit message, or a project's own decisions record, skip it here entirely and make sure it's reachable via References instead.
- **Exact Next Action** — exactly one concrete, unambiguous step, not a list and not a direction. Phrase it as something a fresh agent could start executing immediately — a specific command, or a specific file plus the specific change to make. "Run `npm test` and fix the failing `checkout.spec.ts` case" is a next action; "continue working on checkout" is not.
- **How to Verify** — a concrete check (a command, a test, an output to inspect) that confirms the Exact Next Action succeeded, or that the described state is accurate. If there's no clean automated check, describe concretely what "correct" looks like rather than dropping this section.
- **References** — every path, URL, commit hash, or doc location that holds settled detail instead of it being restated in this file: specs, design docs, ADRs, PRs, issue trackers, a project's own decisions record if it keeps one. This is the mechanism that keeps the file small — prefer a link over a paragraph whenever the information already exists somewhere durable.
- **Gotchas / Blockers** — non-obvious traps discovered this session that would cost a fresh agent time to rediscover: a flaky test, a misleading error, an environment quirk, a version mismatch. Omit this heading entirely if there's genuinely nothing here — don't force a placeholder.
- **Redaction Note** — always present, never silently skipped. State plainly either what kind of sensitive content was redacted (key, token, credential, PII — never the value itself) or that nothing needed redaction. See `references/compaction-and-redaction.md` for what counts as sensitive and how to redact it in place.

For a fully filled-in example, see `references/example-handoff.md`.

## Core Principles

1. **Compaction over completeness.** If it's already written down somewhere durable, reference it — don't copy it. A handoff that duplicates a spec has failed at its one job.
2. **Snapshot, not log.** Every run overwrites fully. There is only ever one current handoff; nothing accumulates.
3. **Scope-filtered, not exhaustive.** The declared next-session focus decides what gets real detail versus a one-line reference.
4. **Redact before writing, every time.** Not conditional on whether the repo is shared, private, or temporary.
5. **This skill doesn't know where it's being used.** Never name a specific project, plugin, workflow, or tool inside the file's content — the file should read the same regardless of what surrounds it. Refer to any project-specific ongoing record (a decisions log, a requirements doc) generically, by what it is, not by an assumed filename.

## Workflow

Full detail on compaction judgment calls and redaction patterns: `references/compaction-and-redaction.md`. Condensed sequence:

1. **Get the next-session scope.** Use what was given at invocation, or ask the one direct question if it wasn't.
2. **Synthesize the live thread.** Walk the session and extract, per the Output template: current state, work done, work remaining, decisions made, the one exact next action, how to verify it, relevant gotchas.
3. **Compact.** For every candidate item, check whether it already exists somewhere durable (spec, doc, commit, an existing project decisions record). If so, drop the restatement and add the pointer under References instead.
4. **Scope-filter.** Trim or reduce-to-a-reference anything that falls outside the declared next-session focus, except blockers that would affect it regardless.
5. **Redact.** Scan for credentials, tokens, keys, and PII; replace with a note of what was redacted, never the value. Always fill in the Redaction Note, even when the answer is "nothing found."
6. **Write `HANDOFF.md` at the project root.** Full overwrite if it already exists.
7. **Ensure it's gitignored.** See Gitignore Handling below.
8. **Confirm back to the human.** File path, generated timestamp, and a one-line summary of what got captured — not a restatement of the whole file.

## Gitignore Handling

Two scripts are bundled for this, one per platform — `scripts/ensure-gitignore.sh` (bash) and `scripts/ensure-gitignore.ps1` (PowerShell). Both are idempotent: safe to run every time, they only add the `HANDOFF.md` entry if it isn't already present, and never duplicate it.

Before running either script:

1. **Confirm you're actually at the project root** — look for a `.git` directory, or ask the human if it's ambiguous. Running this in the wrong directory adds a `.gitignore` entry in the wrong place.
2. **Pick the script that matches the available environment** — `ensure-gitignore.sh` where a POSIX shell (bash) is available, `ensure-gitignore.ps1` where PowerShell is available. Check which is actually usable before running either; don't assume based on the OS name alone (e.g. PowerShell is also available on non-Windows systems, POSIX shells are available inside WSL on Windows).
3. **Run it against the project root directory.**
4. **Verify the result by reading `.gitignore` back afterward** — don't trust a successful exit code alone as proof the entry is there. Confirm the `HANDOFF.md` line is actually present.

If neither shell is usable for some reason, fall back to doing the same check-then-append logic directly as a file edit: read `.gitignore` (or note it doesn't exist), check whether an exact `HANDOFF.md` line is already present, and create or append accordingly — the scripts exist for convenience and consistency, not because the logic is impossible to do directly.

## Anti-Patterns

- **Dumping the raw conversation instead of compacting it.** The point is the live thread, not a transcript.
- **Restating content that already lives in a durable artifact.** If it's already written down somewhere, that's what References is for.
- **A vague Exact Next Action** ("keep working on the feature") instead of one concrete, startable step.
- **Appending to or merging with a previous `HANDOFF.md`** instead of a full overwrite — this is a snapshot mechanism, not a growing log.
- **Skipping the Redaction Note**, or writing it only when something was found — it must always be present, including a plain "nothing found."
- **Running a gitignore script without confirming project root first**, or trusting its exit code without reading the file back.
- **Naming a specific project, plugin, or tool inside the handoff content itself.**

## Best Practices

- Ask the single scoping question upfront rather than guessing what the next session is for — a wrong guess produces a file with the wrong things emphasized.
- Keep References dense and Done/Remaining light — the file should point outward more than it explains inward.
- Use an ISO 8601 timestamp with UTC offset, not a bare date, so staleness is judged correctly across timezones and tools.
- Phrase the Exact Next Action so specifically that acting on it requires no further interpretation.
- After running a gitignore script, read the file back rather than trusting the script's own success message.

## Common Rationalizations to Reject

| Rationalization                                                          | Why it's wrong                                                                                                                                                                            |
| ------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| "I'll include everything, just to be safe."                              | This defeats compaction. A bloated handoff costs the next session more reading time than a missing detail would — use References to keep everything reachable without inflating the file. |
| "It's just a local/private repo, redaction doesn't really matter here."  | Redact regardless of repo visibility — a local repo can become shared later, and the check costs nothing to run every time.                                                               |
| "I'll append to the existing HANDOFF.md so nothing gets lost."           | This is a snapshot mechanism, not a log — overwrite fully. Anything worth keeping long-term belongs in a durable artifact, not accumulating inside this file.                             |
| "The script ran without an error, that's good enough."                   | Verify by reading `.gitignore` back. A script that silently no-ops for the wrong reason still exits 0.                                                                                    |
| "I can guess what the next session is for from what we were just doing." | Ask (or use what was already stated) instead of guessing — a wrong guess about scope produces a file that over- or under-includes the wrong things.                                       |

## Verification

Before confirming back to the human, confirm:

- [ ] Every item under Done This Session and Decisions Made This Session was checked against durable artifacts (specs, docs, commits) — nothing here duplicates what's already written down elsewhere.
- [ ] Exact Next Action is a single concrete, immediately-actionable step — not a list, not a vague direction.
- [ ] How to Verify names a concrete, checkable action, not "make sure it looks right."
- [ ] The Redaction Note is present and explicit — content was actually scanned for credentials/tokens/PII, not assumed clean.
- [ ] `.gitignore` was read back after running the script (or doing the equivalent edit) to confirm the `HANDOFF.md` line is actually present — not just trusted from a success message.
- [ ] `HANDOFF.md` was fully overwritten, not appended to, if it already existed.
- [ ] No specific project, tool, or plugin name appears inside the handoff content itself.

## Reference Files

- `references/compaction-and-redaction.md` — detailed guidance on judging what counts as "already durable" versus what belongs in the live thread, plus concrete redaction patterns for credentials, tokens, keys, and PII.
- `references/example-handoff.md` — a complete, fully filled-in sample `HANDOFF.md` for a realistic in-progress task.
- `scripts/ensure-gitignore.sh` / `scripts/ensure-gitignore.ps1` — idempotent scripts that add a `HANDOFF.md` entry to the project's `.gitignore` if it isn't already there. See Gitignore Handling above for how to use them safely.
