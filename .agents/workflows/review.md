---
description: Run a multi-axis quality and security review after all tasks for a feature are complete. Produces findings for the human to confirm before /fix.
---

# /review — Review the Completed Feature

## Usage

```
/review <feature-name>
```

Run after every task in `specs/<feature-name>/tasks.md` is checked off and its own verification has passed.

## Scope

By this point, `SPEC.md`, `API_CONTRACT.md`, `tasks.md`, and the project's documentation are assumed accurate — any legitimate deviation already went through the improvement-proposal flow during `/build`. This command checks the implementation **against** these artifacts; it does not re-open or re-question the artifacts themselves.

## What This Command Does

1. **Invoke `code-review-and-quality`** (always).
   - Confirm the change's size is actually reviewable; if it's too large for a single pass, say so as the first finding.
   - **Walk all five axes deliberately:** Correctness (against the approved spec/contract), Design (layer and module-boundary compliance — cross-reference `modular-boundary-enforcement`), Security, Test Coverage (right layer, right mocking, per this project's testing policy), Style.
   - **Classify every finding by severity:** Critical / Should-fix / Nit / FYI. Every finding states its concrete impact, not just an instruction.
2. **Invoke `security-hardening`** (re-run in full-checklist mode if the feature touches authentication, payments, webhooks, or RBAC).
   - Confirm trust boundaries were mapped and STRIDE was applied during design; confirm the relevant checklist sections (auth, payment, webhook, file upload, general input) were actually satisfied in the implementation, not just assumed.
3. **Invoke `performance-and-efficiency-engineering`** (if the feature touched a database query, a collection loop, or an external/slow dependency call during `/build`).
   - Re-scan specifically for the N+1 query shape, missing index support, unbounded result sets, and unguarded hot-path I/O.
   - Confirm any cache introduced has a stated TTL and invalidation strategy.
4. **Produce `specs/<feature-name>/review-notes.md`**, listing every finding from all skills above, with severity labels and a summary count by severity at the top.
5. **Present the summary and stop. Do not fix anything yet.** Applying a fix is a separate, explicit step (`/fix`), not something this command does on its own.

## If a Genuine Specification Gap Is Found

If, during review, the agent believes it has found a genuine specification gap (not an implementation defect) — say so explicitly in `review-notes.md` and flag that the human may need to restart an improvement proposal retroactively. This command itself never edits `specs/` or the project's documentation; it only flags the need to.

## Human Verification Gate

The human reads `review-notes.md`, confirms or disputes each AI-found finding, and adds any additional findings the AI missed. The combined, human-confirmed list is the only valid input to `/fix` — do not proceed to `/fix` using only the agent's own unconfirmed findings.

## Notes for the Agent

- Lead with Critical findings; do not bury the most important finding under a long list of Nits.
- An axis with nothing to report still gets an explicit "No issues found" entry — never a silently omitted section.
- If reviewing code written by an agent in an earlier session, pay particular attention to whether tests were genuinely written test-first (often invisible from the diff alone) and whether the implementation silently expanded beyond its task's declared scope.
