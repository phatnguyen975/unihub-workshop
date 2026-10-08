# Context Budget Management

Detail behind the "Context Budget Management" section in `SKILL.md`. Applies most when priming is triggered by drift or a context reset — there's already a body of context in play, not a blank session.

## Why manage before full

Waiting until the context window is nearly full to start trimming causes an abrupt quality drop — the model is already attention-starved by the time trimming starts. Managing proactively at **~75% capacity** keeps quality flat instead of falling off a cliff.

## What to cut first

- Dead-end attempts already concluded — the exploration that didn't pan out, once the lesson from it is captured in one line.
- Verbose tool output already extracted from — a long log, diff, or search result once the one relevant fact has been pulled out.
- Settled back-and-forth — a multi-turn clarification once it resolved into a single decision.
- Drafts that were superseded — an earlier version of a file/plan once a later version replaced it.

## What to protect until the end

- The original task and any hard constraints — these anchor everything else and should never be the thing trimmed.
- The active error or test output currently being debugged.
- The file(s) currently being edited.
- Any decision explicitly marked important by the human.

## Compress before dropping

Don't delete detail outright — reduce it to its conclusion. A three-paragraph investigation into why an approach failed compresses to one line: _"Tried X — failed because Y; going with Z instead."_ That line preserves the useful signal (don't retry X) without the token cost of the full narrative.

## Recency ordering

Content near the end of context gets the most attention; content in the middle gets the least (the "lost in the middle" effect). Order accordingly:

1. Stable background first — rules, architecture context, things unlikely to be needed verbatim again.
2. Task-specific detail in the middle.
3. The active task, current error, and immediate next step **last** — closest to where generation actually happens.

## Example

**Before (uncompressed, ~400 tokens):** A full back-and-forth exploring three possible database schema designs, including the reasoning for rejecting two of them, followed by the tool output of a migration that failed, followed by the fix.

**After (compressed, ~40 tokens):** "Schema: chose append-only event log over normalized tables or JSON blob — needed audit trail, see decision below. Migration `003_events.sql` failed on missing index, fixed by adding `idx_events_created_at`; migration now passes."

The conclusion and the reason survive; the exploratory narrative doesn't need to.
