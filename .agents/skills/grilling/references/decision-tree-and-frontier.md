# Decision Tree & Frontier

Detail behind Workflow steps 2–3 in `SKILL.md`. A starting heuristic, not a script to execute literally — use judgment throughout. The order below is a useful default, not a checklist that must be followed in lockstep every time.

## Finding the nodes

Pull two kinds of items out of the subject:

- **Vague or unquantified claims** — "make it fast", "the usual auth flow", "a reasonable number of retries".
- **Genuine forks** — more than one reasonable implementation exists, and picking wrong means redoing work.

Skip anything already answered in context, anything with only one reasonable interpretation, and anything that's a lookup-able fact rather than a judgment call (see "Look it up before you ask" below).

## Dependencies

Would answering B require knowing A's answer first, or would B's phrasing change depending on A? If yes, A is a prerequisite of B. Don't force a dependency that isn't real — over-linking just delays questions that could've been asked sooner.

## Priority

| Priority | Rough test                                 |
| -------- | ------------------------------------------ |
| Blocking | Wrong answer here means a full redo        |
| Shaping  | Wrong answer means real but fixable rework |
| Cosmetic | Wrong answer, nobody would really mind     |

When torn between two tiers, lean toward the higher one — under-asking costs more than a slightly longer round.

## Look it up before you ask

If a node is actually a fact sitting in the codebase, docs, or a tool — resolve it yourself, it doesn't need to be a node. Not fully sure the lookup is right? Keep it as a node, but make your finding the recommended default and say where it came from.

## Frontier & rounds

Frontier = nodes whose dependencies are resolved (or have none), filtered by mode — `normal` excludes Cosmetic, `comprehensive` includes it (usually in a later round). Recompute after every round: resolved answers can unblock children, invalidate siblings, or reveal a node that wasn't identified at the start — the tree isn't fixed once built.

Cap a round around 5–7 questions; split by priority if the frontier is bigger.

Round format:

```markdown
## Round <N> — <count> question(s)

1. [Blocking] <question, phrased as a concrete choice>
   → Suggested: <short default, with a reason if not obvious>

Reply quickly, e.g. "1, 2 OK, 3 → <override>."
```

- A choice between named options beats a fully open-ended question — faster to answer, easier to parse.
- Always include a suggested default — a fast path to accept or override, not you deciding for them.

## Worked example

**Subject:** "Add a notification feature to the app."

**Nodes & priority:**

- Which events trigger it — Blocking
- Delivery channel(s) — Blocking
- Real-time vs. batched — Shaping, depends on channel
- User configuration of types — Shaping, depends on event list
- Retry behavior — Shaping, depends on channel + timing
- Read/unread history — Shaping, depends on channel
- Visual styling — Cosmetic, depends on an in-app surface existing

**Round 1** (`normal` mode) — frontier is the two root Blocking nodes:

```markdown
## Round 1 — 2 questions

1. [Blocking] Which events trigger a notification — fixed initial set, or extensible from the start?
   → Suggested: fixed set now (e.g. 2–3 events), extensible later.

2. [Blocking] Which delivery channel(s) — in-app only, or also email/push?
   → Suggested: in-app only for v1.
```

User answers: fixed set (new comment, mention); in-app only.

**Round 2** — the Shaping nodes that depended on channel are now in the frontier. Visual styling stays excluded (Cosmetic, `normal` mode) — it lands in the Resolution Summary's "Auto-Defaulted" table instead, with a stated default.

Continue until the tree is exhausted.
