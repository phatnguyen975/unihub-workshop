---
name: codegraph-intelligence
description: Use CodeGraph (colbymchenry/codegraph) instead of grep/find/glob or blind file reads whenever a task needs code structure — tracing call chains and bug flows, computing blast radius before renaming or changing a signature, finding tests affected by a change, auditing architecture/pattern/route consistency, or orienting in an unfamiliar repo. Trigger proactively for debugging ("how does X reach Y"), refactoring ("what breaks if I change this"), code review ("what does this diff impact"), and codebase onboarding — even if the user doesn't name CodeGraph. Also covers guiding setup when CodeGraph isn't configured yet. Not for editing code, free-text search, or reading a single already-known file.
---

# CodeGraph Intelligence

## Overview

CodeGraph is a local, pre-indexed knowledge graph of a codebase: symbols, their relationships (calls, imports, inheritance, routes, cross-language bridges), and the verbatim source behind them. Querying it answers structural questions in one or two targeted calls instead of the multi-step `grep`/`glob`/`Read` scanning an agent would otherwise need.

This skill is a routing and usage guide: given a task, it points to the right domain, the right CodeGraph command, and how to read and fold the result back into the actual work. It assumes only `colbymchenry/codegraph` — no forks or reimplementations under similar names.

## When to Use

Reach for CodeGraph whenever the task is about how code is _structured or connected_, not just about reading one already-known file:

- Tracing how an error or a value flows from one place to another when debugging
- Finding every caller/callee of a symbol, including through callbacks or other dynamic-dispatch paths that `grep` can't follow
- Assessing blast radius before renaming a symbol, changing a signature, or restructuring a module
- Finding which tests are affected by a set of changed files
- Reviewing a diff/PR and needing its structural impact, not just its diff
- Auditing pattern consistency (e.g. "do all handlers implement the same interface?") or mapping the API/route surface of a service
- Getting oriented quickly in an unfamiliar repository or module
- Any case where the alternative is several `grep`/`glob`/`Read` calls to reconstruct a call path or a symbol's usages

## When NOT to Use

- **Writing or editing code** — CodeGraph is read/query-only; use the agent's normal file-edit tools
- **Finding an arbitrary string, log message, or comment text** — CodeGraph's search is name/signature based, not general full-text search; use `grep`/`ripgrep`
- **Explaining why code behaves a certain way** — CodeGraph returns structure and verbatim source, never a semantic summary; read the source it returns and reason over it directly
- **True runtime behavior or real taint/security analysis** — CodeGraph is 100% static analysis; dynamic-dispatch edges are heuristic inferences, not observed runtime traces, and there is no sanitization-awareness
- **A single, already-identified file with no structural question attached** — a plain file read is simpler and cheaper

## Input

| Needed                                               | Type                         | Required                                              |
| ---------------------------------------------------- | ---------------------------- | ----------------------------------------------------- |
| Task intent (which domain it maps to)                | inferred from the request    | Required                                              |
| Symbol name, file path, or natural-language question | depends on domain            | Usually required                                      |
| A diff or list of changed files                      | file paths / git diff output | Required only for Code Review and Test Impact domains |

See `references/domains.md` for the exact input each domain expects.

## Output

CodeGraph's raw result (verbatim source + structural facts) is an **input to the task**, not the deliverable — fold it into the debugging/refactoring/review work directly. Only when a finding needs to be surfaced to the human on its own (not merged into prose), use the structure below instead of pasting raw command output. Each labeled part is its own section — never collapse several facts onto one line after a label; split into a short list under the section instead.

```markdown
## CodeGraph Findings — <short title, e.g. "Renaming `UserService.save`">

> **Domain:** <domain name>
> **Command(s):** `<command>` on `<target>`

### Scope

<what was asked and what was actually searched; 1–2 sentences>

### Findings

<the structural fact(s) CodeGraph returned — one self-contained point per line
when there's more than one; don't merge distinct facts into a single sentence>

### Sources

<every file/symbol backing the Findings, each with a short note on what's there; never a bare path list>

- `<file:line-range>` — <what's there and why it matters>
- `<file:line-range>` — <what's there and why it matters>

### Next

<what the human/agent should do with this — e.g. "3 call sites to update before renaming" or "run these 2 affected tests">

### Caveats

<only when the domain or result warrants one; omit the section entirely otherwise —
e.g. a staleness warning on a specific file, a derived/best-effort domain, or static
analysis-only scope — see the domain's Limitations in `references/domains.md`>
```

## Core Principles

- Prefer CodeGraph over `grep`/`find`/blind `Read` whenever the question is about structure or connections, not a single known file.
- Trust the returned source and structure — don't re-verify with `grep` "just in case". Re-read directly only when the result carries a staleness warning.
- The CLI is always the fallback. A capability missing from the current MCP tool list is never a reason to stop — it's a reason to use the equivalent CLI command instead.
- MCP stays minimal by design (only `explore` is exposed by default). Never reconfigure it mid-task to add more tools.
- Missing setup is guided, not refused. A missing CLI or MCP wiring leads to setup instructions for the human, never an outright refusal to help.
- Results are structural facts from static analysis, not semantic judgments — the agent still does the reasoning about what they mean.
- One well-aimed query beats several narrow ones — prefer a precise `explore`/`impact` call over chaining many `callers`/`callees` calls when one broader query already covers the path.

## Setup & Interface Resolution

Resolve this once per session, before the first real query:

1. **Probe CLI** — a harmless CLI command (e.g. `codegraph status` or `codegraph version`) either succeeds or doesn't. This is directly verifiable.
2. **Probe MCP** — check whether the agent's own current tool list already contains a tool matching `codegraph_explore`. This is the only reliable signal (see `references/setup.md` for why).
3. Apply the decision matrix (both ready / CLI only / neither) — full flow, exact official install commands, and restart handling are in `references/setup.md`. Read it before guiding a human through setup.
4. Once the interface is resolved, confirm the current project is indexed (`.codegraph/` exists); if not, guide/run initialization per `references/setup.md`.

Setup is never a hard blocker — guide the human, fall back to whatever's available, and keep working.

## Workflow

1. Resolve the interface and project index (Setup section above).
2. Map the task to a **domain** using the routing table below.
3. Inside the domain, pick the specific use case and command(s) — see `references/domains.md` for the full guidance per domain.
4. Run the command. Read both the structural facts and the verbatim source in the result — don't re-`Read` a file CodeGraph already returned.
5. Fold the result into the actual task. Use the Output format template only when surfacing a finding on its own.
6. If a result carries a staleness warning, read that one spot directly instead of trusting the graph for it.
7. Continue the task, looping back to step 3 for any follow-up query.

### Domain Routing

| Domain                                 | Use when                                                                                            | Commands                                |
| -------------------------------------- | --------------------------------------------------------------------------------------------------- | --------------------------------------- |
| Onboarding / Context Priming           | Getting oriented in an unfamiliar repo or module                                                    | `explore`, `files`, `status`            |
| Debugging & Root Cause Tracing         | Tracing how an error/flow reaches a symbol, incl. callbacks and cross-language (mobile) bridges     | `explore`, `callers`, `callees`, `node` |
| Safe Refactoring                       | Blast radius before renaming/changing a signature; keeping routes intact while restructuring        | `query`, `impact`, `explore`            |
| Code Review / Change Impact Assessment | Summarizing what a diff touches and which tests it affects                                          | `query`, `impact`, `affected`, `node`   |
| Architecture & Pattern Audit           | Pattern consistency, API/route surface, module boundaries                                           | `query`, `explore`, `node`              |
| Code Hygiene _(derived, best-effort)_  | Likely-orphaned symbols, duplicate/colliding names                                                  | `query`, `callers`                      |
| Data-flow Tracing _(structural only)_  | Following a route/entry-point forward to where it touches sensitive operations, as a first-pass map | `explore`                               |
| CI / Test Impact Optimization          | Running only the tests affected by changed files                                                    | `affected`                              |

Full detail per domain — trigger signals, required input, how to read the output, how it feeds back into the task, and known limitations — is in `references/domains.md`.

## Commands Reference

Every non-setup command is used somewhere in the domains above: `explore`, `node`, `query`, `callers`, `callees`, `impact`, `affected`, `files`, `status`. Setup commands (`init`, `uninit`, `install`, `uninstall`) are confined to the Setup section and `references/setup.md`. A few maintenance/troubleshooting commands (`index`, `sync`, `unlock`) exist outside both lists — see the Troubleshooting note in `references/commands.md`.

Purpose, invocation shape, and `--json`/flag notes for each command are in `references/commands.md`. Flags can change between CodeGraph versions — confirm exact syntax with `codegraph help <command>` when in doubt rather than assuming.

Only `explore` is exposed over MCP by default, as `codegraph_explore` (same output as the `explore` CLI command). Every other command in this list — including `node` — runs via the CLI regardless of whether MCP is ready, since it isn't exposed over MCP by default.

## Anti-Patterns

- Running `grep`/`find` first "just to be safe" when CodeGraph already answered the structural question.
- Treating a missing CLI or MCP wiring as a hard blocker and refusing the task instead of guiding setup or falling back.
- Reconfiguring MCP tool exposure mid-session instead of using the CLI for the one missing capability.
- Chaining many narrow `callers`/`callees` calls instead of one `explore`/`impact` query that already covers the path.
- Presenting raw, unfiltered command output to the human instead of the relevant findings.
- Inferring code behavior or intent from structure alone, without reading the verbatim source CodeGraph returned.

## Common Rationalizations to Reject

- _"CLI isn't set up, so I can't help with this."_ — Reject. Guide setup, then fall back to whatever becomes available, or proceed with normal tools; never a hard refusal.
- _"MCP doesn't expose `impact`, so I'll skip blast-radius analysis."_ — Reject. The CLI exposes it identically; use that.
- _"I already grepped and found the callers, no need to also check CodeGraph."_ — Reject when the task needs transitive impact or affected tests, which `grep` structurally cannot compute.
- _"The result looks right, I'll summarize from memory instead of quoting the actual lines."_ — Reject. Ground every claim in the source CodeGraph returned, not in paraphrase.
- _"I'll re-verify with grep to be extra safe."_ — Reject unless a staleness warning is present; this doubles cost for no benefit.

## Verification

Before presenting results, confirm:

- [ ] Interface was resolved via tool-list/CLI probing, not assumed
- [ ] The domain and command chosen match the task's actual intent
- [ ] Claims about code structure are grounded in the verbatim source/edges CodeGraph returned, not inferred
- [ ] Any staleness warning was respected (direct read for that spot)
- [ ] No redundant `grep`/`Read` was performed for something CodeGraph already answered
- [ ] If setup was needed, it was guided — not refused — and didn't block the rest of the work
- [ ] Findings surfaced to the human use the Output Format template, not a raw command dump
