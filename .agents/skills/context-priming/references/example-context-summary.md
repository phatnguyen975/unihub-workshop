# Example — A Filled-In Context Summary

## Example 1 — Fresh Start

A human invoked this skill: "prime context — I'm picking this project back up after a few days off, going to add pagination to the search results endpoint."

```markdown
## Context Summary — Pagination for search results endpoint

> **Loaded:** 2026-09-12T09:10:00+07:00
> **Focus:** Add pagination to the `/search` endpoint.

### Rules

Pagination must be cursor-based, not offset-based — this is a house standard, not a per-endpoint choice. Query params stay snake_case.

- `CONVENTIONS.md` — the pagination and naming rules above; also confirms REST-only, no GraphQL.

### Spec / Architecture

The current response is an unpaginated flat array; the doc already flags pagination as planned, no design decision left to make.

- `docs/api/search.md`, "Response shape" section — only section relevant here, rest of the doc covers unrelated endpoints.

### Source Files

`search.ts` needs the same cursor-pagination shape already used elsewhere in the project.

- `src/routes/search.ts` — file to edit; currently returns a flat array with no pagination.
- `src/routes/orders.ts` — existing cursor-pagination pattern to mirror, per CONVENTIONS.md's consistency rule.

### Error / Test Output

Not applicable — no active failure.

### Prior Session / History

A session snapshot from two days ago exists, noting this task was queued up next; no work had started on it yet.

- Session snapshot artifact (as of two days ago) — confirms this is the next task, no prior attempt to account for.

### Understanding

Search currently returns a flat array with no size limit. The project already has a cursor-based pagination pattern in `orders.ts` — same shape should apply here for consistency. No architectural decision needed; this is an implementation task following an established pattern.

### Gaps

None — pattern to follow is clear, no open questions.

### Ready to proceed?

Yes — ready to implement cursor pagination on `/search` following the `orders.ts` pattern.
```

### What to notice

- **Each level explains itself before listing files** — the prose line under each heading is what makes the summary useful on its own; the file list underneath is for traceability, not the main content.
- **Every referenced file has a reason attached**, not just a bare path — e.g. `orders.ts` is explicitly marked as the pattern to mirror, not just listed alongside the file being edited.
- **Only the relevant section of the spec was loaded** (Response shape), and that's stated explicitly, not implied.
- **No "Context Adjustments" section appears** — this wasn't a drift/reset entry, so it's correctly omitted rather than padded with "not applicable."
- **Gaps is honestly empty** — nothing was added just to fill the section.

## Example 2 — Drift/Reset Trigger

A human invoked this skill: "prime context — I think we lost the thread after that last context reset, let's make sure we're aligned before continuing the checkout refactor."

```markdown
## Context Summary — Checkout refactor (post-reset realignment)

> **Loaded:** 2026-09-12T11:05:00+07:00
> **Focus:** Continue the checkout refactor after a context reset.

### Rules

No change from earlier in this session — REST-only, cursor pagination, snake_case params, Jest required.

- `CONVENTIONS.md` — unchanged, re-confirmed still applies.

### Spec / Architecture

Checkout refactor spec, "Payment step" section — the part currently in progress.

- `docs/checkout-refactor.md`, "Payment step" — only section still relevant; earlier sections (cart, shipping) are already merged and settled.

### Source Files

`checkout.ts` is mid-edit; the flaky test mentioned below is the current blocker.

- `src/routes/checkout.ts` — file currently being edited, partially refactored.
- `src/routes/checkout.test.ts` — the test currently failing intermittently.

### Error / Test Output

`checkout.test.ts` "handles payment retry" case fails intermittently (~1 in 5 runs) — suspected race condition in retry-count tracking, not yet fixed.

### Prior Session / History

Reconstructed from what remained after the reset: cart and shipping steps of the refactor are done and merged; payment step is in progress; the flaky test was already identified before the reset, not a new discovery.

### Context Adjustments

The pre-reset exploration of three different retry-tracking approaches was compressed to one line: "settled on a counter stored per-session, not per-request — see reasoning in commit abc123." The full back-and-forth wasn't recoverable after the reset and isn't needed going forward.

### Understanding

The refactor is on its last step (payment), blocked by one flaky test tied to a known race condition. No new decision needed — continue debugging the retry-count race condition using the per-session counter approach already settled on.

### Gaps

Whether the race condition was already narrowed down to a specific line before the reset is unclear — worth confirming before diving back in.

### Ready to proceed?

Ready to resume debugging, pending confirmation on the Gap above.
```

### What to notice

- **Context Adjustments appears here** because this was explicitly a post-reset entry — it wasn't included in Example 1 because that entry had nothing to adjust.
- **Prior Session / History reconstructs from what remained**, rather than pretending full recall — and says so plainly.
- **Gaps is honest about a real uncertainty** this time, instead of defaulting to "none" — the two examples together show the section is used both ways depending on what's actually true.
- **Rules and Spec inherit "no change" rather than being silently dropped** — still stated, just briefly, since nothing new needed loading there.
