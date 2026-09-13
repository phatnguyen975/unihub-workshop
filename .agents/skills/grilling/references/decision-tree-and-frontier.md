# Decision Tree & Frontier — Full Procedure

This is the detailed mechanics behind Workflow steps 2–3 in `SKILL.md`. Read this in full the first time you run a grilling session, and come back to it whenever the priority tagging or round-splitting logic isn't obvious.

The reason this is written as an explicit, mechanical procedure rather than a single abstract instruction ("map this as a decision tree") is that the abstract version is easy to apply inconsistently — steps get skipped or reordered without noticing. Following the steps below literally — including writing the tree out, even briefly — makes the result reliable and repeatable every time.

## Step 1: Extract candidate nodes

Read the subject — including anything already established within it (prior decisions, constraints, conventions already stated) — and pull out two kinds of items:

- **Vague or unquantified claims.** Anything stated without enough specificity to act on: "make it fast", "the usual auth flow", "a reasonable number of retries", "store it somewhere durable".
- **Genuine forks.** Any point where more than one implementation is equally reasonable given what's been said so far, and picking wrong would mean redoing work: which of two data models, whether a feature is synchronous or async, whether granularity is coarse or fine, which of several plausible scopes applies.

Do **not** extract:

- Things already stated as settled within the subject itself.
- Things with only one reasonable interpretation (no real fork exists).
- Things that are facts, not decisions — see Step 4 (Lookup-before-ask) before treating anything as a node.

Write this out as a flat list before doing anything else. Don't try to build the tree structure and the list at the same time — separate the two passes.

## Step 2: Build dependency edges

For every pair of nodes, ask: "would I need to know the answer to node A before I could ask node B in a way that isn't a guess?" If yes, A is a parent of B (B depends on A).

Practical tests for a dependency:

- B's phrasing would have to change depending on A's answer (e.g., "should the retry queue be persistent?" depends on "is this even using a queue, or synchronous calls?").
- B is a refinement that only makes sense once A picks a direction (e.g., "which fields does the user-facing table need?" depends on "is there a user-facing table at all, or is this internal-only?").

If two nodes don't have this relationship, they're siblings — independent of each other, even if they're related in subject matter. Don't force a dependency that isn't real; over-linking the tree just delays questions that could've been asked earlier.

The result is a tree (or forest, if there are multiple independent branches): root-level nodes have no unresolved dependency, deeper nodes depend on their ancestors being resolved first.

## Step 3: Tag priority

Tag every node with exactly one of:

| Priority     | Definition                                                                                                                                 | Test                                                                                                                           |
| ------------ | ------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------ |
| **Blocking** | Work literally cannot start, or would have to be thrown away entirely, without this answer.                                                | "If I picked wrong here, would everything downstream need to be redone from scratch?"                                          |
| **Shaping**  | The outcome changes meaningfully depending on the answer, but a reasonable starting point exists and course-correction later is realistic. | "If I picked wrong here, would fixing it later cost noticeably more than getting it right now, without being a total do-over?" |
| **Cosmetic** | Low-impact — worth knowing, but the default choice is very unlikely to cause real problems either way.                                     | "If I never asked this and just picked something sensible, would anyone actually mind?"                                        |

When genuinely torn between two tiers, prefer the higher one (Blocking over Shaping, Shaping over Cosmetic) — under-asking is more expensive than a slightly longer round.

## Step 4: Look up before you ask

Before finalizing any node, check: is this actually a judgment call, or is it a fact that exists somewhere already (a convention already established in the project, documentation, code you have access to, or a tool you can query)? If it's discoverable, resolve it yourself and don't add it as a node at all — or, if you're not fully sure your lookup is right, add it as a node but state your found answer as the recommended default, explicitly noting where it came from, so the user is confirming rather than answering from scratch.

Only nodes that require an actual human judgment call belong in the tree.

## Step 5: Compute the frontier

The frontier is every node whose parent(s) are already resolved (or which has no parent at all), filtered by the active mode:

- `normal` mode: frontier = unresolved nodes with resolved (or no) parents, **excluding** Cosmetic nodes.
- `comprehensive` mode: frontier = unresolved nodes with resolved (or no) parents, **including** Cosmetic nodes — but if Blocking or Shaping nodes are also in the frontier, ask those first and hold Cosmetic ones for a later round so they don't crowd out higher-priority questions.

Recompute the frontier after every round — resolved answers unblock children (add them to the next frontier) and may also invalidate or simplify sibling nodes (prune them; note in your own reasoning why, no need to surface this to the user unless it changes something they said).

## Step 6: Format and ask the round

Cap a round at roughly 5–7 questions. If the frontier is larger, split it: Blocking first, then Shaping, then (in `comprehensive` mode) Cosmetic — each as its own round rather than one giant block.

Format:

```markdown
## Round <N> — <count> question(s)

1. [Blocking] <question, phrased as a concrete, answerable choice — not open-ended>
   → Suggested: <short recommended default, with a one-clause reason if it's not obvious>

2. [Shaping] <question>
   → Suggested: <default>

Reply quickly with e.g. "1, 2 OK, 3 → <override>" or answer each in full — whichever's faster for you.
```

Notes on phrasing:

- Prefer questions phrased as a choice between named options over fully open-ended ones ("Should tasks be split by BE/FE by default, or kept together?" beats "How should tasks be structured?") — this is faster to answer and produces a reply that's easier to parse unambiguously.
- Always include a suggested default, even for Blocking questions. A default doesn't mean you're deciding for the user — it's a fast path for them to accept or override.
- Keep the "why it matters" implicit in the question itself where possible; only add an explicit reason clause when the impact isn't obvious from the question alone.

## Worked Example

**Subject:** "Add a notification feature to the app."

**Step 1 — candidate nodes (flat list):**

- Which events trigger a notification?
- Delivery channel(s) — in-app, email, push, more than one?
- Real-time delivery or can it be batched/delayed?
- Does the user get to configure which notifications they receive?
- Retry behavior if delivery fails
- Notification history / read-unread state stored anywhere?
- Visual styling of the notification UI

**Step 2 — dependencies:**

- "Delivery channel(s)" has no dependency (root).
- "Real-time or batched" depends on which channel(s) are chosen (push implies different latency expectations than email).
- "Retry behavior if delivery fails" depends on delivery channel (retry semantics differ by channel) and real-time-vs-batched.
- "User configuration of notification types" depends on "which events trigger a notification" (root, independent) — can't ask about configuring types until the type list itself is settled at least at a first pass.
- "Read/unread history" depends on delivery channel (only matters if there's an in-app surface at all).
- "Visual styling" depends on there being an in-app surface, and on read/unread existing.

**Step 3 — priority tags:**

- Which events trigger it → **Blocking** (defines the entire feature scope)
- Delivery channel(s) → **Blocking** (defines architecture: push infra vs. email service vs. in-app-only)
- Real-time vs. batched → **Shaping**
- User configuration of types → **Shaping**
- Retry behavior → **Shaping**
- Read/unread history → **Shaping**
- Visual styling → **Cosmetic**

**Step 4 — lookup check:** none of these are discoverable facts in this example; all require a human decision. (In a real session, check the codebase/docs first — e.g., an existing notification service or design system might already answer "visual styling.")

**Step 5 — frontier (Round 1, `normal` mode):** only root nodes with no unresolved parent: "which events trigger it" and "delivery channel(s)" — both Blocking.

**Round 1:**

```markdown
## Round 1 — 2 questions

1. [Blocking] Which events should trigger a notification — a fixed initial set, or should it be an extensible list from the start?
   → Suggested: fixed initial set (e.g., 2–3 concrete events) now, extensible later — avoids over-building before real usage patterns exist.

2. [Blocking] Which delivery channel(s) — in-app only, or also email/push?
   → Suggested: in-app only for v1; email/push are separate infra investments better justified once in-app usage is validated.
```

Suppose the user answers: "1 → fixed set: new comment, mention. 2 → in-app only for now."

**Recompute frontier (Round 2):** "real-time vs. batched", "user configuration of types", and "read/unread history" are now askable (their parent — delivery channel — is resolved as in-app). "Retry behavior" depends on both channel AND real-time-vs-batched, so it's still blocked until Round 2 resolves that. "Visual styling" is Cosmetic, excluded in `normal` mode — it goes to the Resolution Summary's Auto-Defaulted table instead, with a stated default (e.g., "match existing app notification/toast styling if any exists, otherwise a simple minimal style").

This continues until the tree is exhausted, at which point the session ends with the Resolution Summary as defined in `SKILL.md`.
