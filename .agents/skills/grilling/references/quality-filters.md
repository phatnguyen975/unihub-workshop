# Quality Filters — Detecting and Handling Vague Answers

This is the detail behind Workflow step 4 in `SKILL.md`: processing answers, not just collecting them. A round isn't done just because every question got a reply — it's done when every reply is actually a decision, not a placeholder wearing a decision's clothes.

## Why this matters

The failure mode this guards against isn't silence — it's a confident-sounding answer that doesn't actually resolve anything. "Make it scalable" and "the standard approach" both _feel_ like answers, and it's tempting to mark the node resolved and move on. But neither one is specific enough to act on without still making an unchecked assumption later — which defeats the purpose of asking in the first place.

## Signals that an answer is not actually resolved

Watch for these patterns in a reply:

- **Buzzwords standing in for a spec.** "Scalable", "robust", "clean", "modern", "flexible", "enterprise-grade" — these describe a quality, not a concrete choice. They're fine as color commentary alongside a real answer, but not as the answer itself.
- **Deferred to unspecified convention.** "The way most apps do it", "the standard pattern", "whatever's normal here", "best practice". These name a category of answer without picking a member of it. There are usually several "standard" ways to do most things.
- **Hedging language.** "I think", "probably", "I guess", "maybe just", "something like". This often signals the user hasn't actually thought it through yet, not just that they're being casual — worth a gentle check rather than taking the literal words at face value.
- **Answering a different, easier question.** Sometimes a reply resolves a nearby but distinct question instead of the one asked. Check that the reply actually maps onto the node before marking it resolved.
- **Non-answer confirmations.** "Yeah, sounds good" to an open-ended question (not one with a suggested default to confirm) doesn't tell you which of several options they meant — only accept a bare confirmation when there was a concrete default on offer to confirm.

## What is NOT a vague answer

Don't over-apply this — these are legitimately resolved:

- A firm, specific choice, even if brief: "email" (in answer to a channel question) is complete.
- Confirmation of a stated suggested default: "1 OK" against a round that included a concrete `→ Suggested:` line is a real, specific answer — the suggestion supplies the specificity.
- "I don't know, you decide" — this is itself a decision (delegate the choice), not a vague answer. Treat it as: pick the suggested default (or the most defensible option if none was offered), mark it resolved, and note in the summary that it was delegated rather than directly chosen.
- A boundary or range, stated as such on purpose: "somewhere between 3 and 5 retries, I don't care which exactly" is a resolved decision with an intentional degree of freedom — don't force false precision onto an answer that's deliberately loose.

## How to follow up

When a reply looks vague:

1. **Push once, specifically** — don't re-ask the original question verbatim. Narrow it using their own words: if they said "The usual auth flow", ask "Do you mean session cookies, or a token-based flow like JWT?" rather than repeating "what auth flow do you want?"
2. **Offer 2–3 concrete options if the space is genuinely open**, rather than an open-ended re-ask — this respects that they may not have a specific technical vocabulary for what they want, and it's faster for them to point at one than to invent a spec from scratch.
3. **Accept the second answer even if it's still not fully precise**, as long as it's now concrete enough to act on without further silent interpretation. Don't turn this into an infinite loop chasing perfect precision — one follow-up is usually enough; use judgment past that point rather than mechanically repeating the pattern.
4. **If, after a follow-up, the user clearly wants to delegate the decision** ("you pick", "whatever you think is right"), treat that as resolved via delegation (see above) rather than pushing a third time.

## Example

```
Round 1, Q3: [Shaping] How should conflicting edits be handled — last-write-wins, or merge?
User's reply: "just handle it the standard way"

Follow-up (not a full new round, inline): "There isn't a single standard here — the common options are last-write-wins (simplest, can silently lose data) or a merge/conflict-flag approach (safer, more complex). Which fits better for this feature?"
User's reply: "oh, last-write-wins is fine, it's low-stakes data"

→ Resolved: last-write-wins. (Reason captured for the summary: low-stakes data, user confirmed acceptable data-loss risk.)
```

This keeps the interview efficient — one extra exchange, not a repeated loop — while making sure what lands in the Resolution Summary is something the caller can actually build on without guessing what "the standard way" was supposed to mean.
