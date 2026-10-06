# Quality Filters — Detecting and Handling Vague Answers

Detail behind Workflow step 4 in `SKILL.md`. A round isn't done when every question got a reply — it's done when every reply is actually a decision, not a placeholder wearing a decision's clothes.

## Why it matters

The failure mode isn't silence — it's a confident-sounding non-answer. "Make it scalable" and "the standard approach" both _feel_ like answers but aren't specific enough to act on; marking them resolved just defers the unchecked assumption, which defeats the point of asking.

## Signals an answer isn't actually resolved

- **Buzzwords standing in for a spec** — "scalable", "robust", "clean", "flexible", "enterprise-grade". Fine as color commentary alongside a real answer, not as the answer itself.
- **Deferred to unspecified convention** — "the way most apps do it", "the standard pattern", "best practice". Names a category without picking a member of it.
- **Hedging language** — "I think", "probably", "maybe just". Often signals the user hasn't actually decided yet.
- **Answering a different, easier question.** Check the reply actually maps onto the node asked before marking it resolved.
- **Non-answer confirmations** — "sounds good" to an open-ended question tells you nothing about which option they meant. Only accept a bare confirmation when a concrete `→ Suggested:` default was on offer to confirm.

## What is NOT vague

- A firm, specific choice, even brief: "email" is complete.
- Confirmation of a stated suggested default — the suggestion supplies the specificity.
- "I don't know, you decide" — a decision to delegate, not a vague answer. Pick the suggested default (or the most defensible option), mark resolved, note it was delegated.
- A deliberately stated range — "3 to 5 retries, don't care which" — is resolved with intentional looseness; don't force false precision onto it.

## Following up

1. **Push once, specifically** — narrow using their own words rather than re-asking verbatim. "The usual auth flow" → "Session cookies, or token-based like JWT?"
2. **Offer 2–3 concrete options** if the space is genuinely open, rather than another open-ended re-ask.
3. **Accept the second answer** once it's concrete enough to act on, even if not fully precise — one follow-up is usually enough; use judgment rather than chasing perfect precision.
4. **Clear delegation after a follow-up** ("you pick") → resolved via delegation, don't push a third time.

## Example

```
Round 1, Q3: [Shaping] How should conflicting edits be handled — last-write-wins, or merge?
User: "just handle it the standard way"

Follow-up: "There isn't a single standard here — last-write-wins (simplest, can silently lose data) or a merge/conflict-flag approach (safer, more complex). Which fits better for this feature?"
User: "last-write-wins is fine, it's low-stakes data"

→ Resolved: last-write-wins (low-stakes data, acceptable data-loss risk).
```

One extra exchange, not a repeated loop — and the summary ends up with something the caller can build on, not a guess at what "the standard way" meant.
