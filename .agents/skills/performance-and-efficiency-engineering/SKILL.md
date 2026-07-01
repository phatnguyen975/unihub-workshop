---
name: performance-and-efficiency-engineering
description: Applies a measure-first discipline and a catalog of well-known performance anti-patterns (N+1 queries, missing indexes, unbounded result sets, blocking I/O in hot paths, unnecessary recomputation) during both implementation and review. Use when a task or change involves a database query, a loop that processes a collection, a call to an external service, or any code on a path that will run repeatedly or at scale. Do not use for one-off scripts, admin tooling run by a single operator, or any code path whose execution frequency and data volume are both guaranteed to stay trivially small.
---

# Performance and Efficiency Engineering

## Overview

Most performance defects are not exotic algorithmic mistakes — they are a small set of well-known, easily-recognized patterns repeated in a new place: a query issued inside a loop, a result set fetched without a limit, a synchronous call to a slow dependency sitting in a path that must respond quickly. This skill catalogs those patterns, gives a measure-first discipline for deciding what's actually worth optimizing, and applies during both implementation and review, so the same set of checks protects code at the point it's written and again at the point it's reviewed.

This skill is deliberately framework-agnostic and stack-agnostic: the specific API names for "eager loading" or "batching" differ by ORM and language, but the underlying pattern — and the fix — is the same everywhere. Substitute your stack's actual mechanism for the illustrative examples below.

## When to Use

- A task or change touches a database query, a cache, or any data-access code.
- A change introduces a loop that iterates over a collection and does meaningful work (especially I/O) per item.
- A change adds or modifies a call to an external service, another internal service, or any dependency whose latency is not guaranteed to be negligible.
- The code being written or reviewed sits on a path that will run repeatedly (a request handler, a message consumer, a scheduled job) or at scale (processing collections whose size grows with real usage, not a fixed small constant).

## When NOT to Use

- A one-off script or admin tool run manually, by a single operator, at a frequency and data volume where inefficiency has no meaningful cost.
- Code on a path whose execution frequency and data volume are both guaranteed to stay trivially small (e.g. a startup-time configuration read that runs once per process lifetime).

## Core Principles

1. **Measure before optimizing; never optimize by feel.** Premature optimization — restructuring code for performance before confirming it's actually a bottleneck — wastes effort and frequently makes code harder to read for no measurable benefit. Apply this skill's pattern catalog to _prevent_ well-known anti-patterns at write time (cheap, no measurement needed — these patterns are bad regardless of scale), and reserve deeper optimization effort for paths a profiler or query log has actually identified as a real cost.
2. **The most common performance defect is one query becoming many.** The N+1 query pattern — one query to fetch a collection, then one additional query per item to fetch related data — is the single most frequent, most consistently recognizable performance defect across every stack and every ORM. Treat any loop that triggers a query (or any other I/O) per iteration as a default suspect, not an exception.
3. **A query's shape and its supporting index are one decision, not two.** A query that will run frequently or against a growing table needs an index that actually supports its access pattern — adding the query without checking the index, or adding the index without confirming the query actually uses it, both leave the real problem in place. See `database-schema-evolution` for the indexing change itself; this skill is what tells you a change needs one.
4. **Every query against a growing collection needs an explicit bound.** A query, an API response, or an in-memory collection operation with no limit will work fine in development and at small scale, and degrade — sometimes catastrophically — as the underlying data grows. Bound it deliberately (pagination, a hard cap, a streaming approach) at the point it's written, not after it's already caused an incident.
5. **Synchronous calls to slow or unreliable dependencies don't belong in a path that must stay fast.** A call to an external service, a downstream system, or anything whose latency isn't tightly bounded and guaranteed, placed directly in a hot, latency-sensitive path, makes that path only as fast and as reliable as its slowest dependency. Push it out of the synchronous path (async processing, a queue, a cache) wherever the operation's own correctness requirements allow it.
6. **Caching is a deliberate design decision with its own correctness obligations, not a free performance lever.** A cache without a clear invalidation strategy, or without a TTL chosen for a reason, trades a performance win for a staleness or correctness risk that's easy to underestimate. Treat introducing a cache with the same design seriousness as introducing a new data store — because, in the ways that matter, it is one.
7. **Test against realistic scale, not a near-empty development dataset.** A query, a loop, or an algorithm that performs fine against ten rows can degrade non-linearly against ten thousand. The patterns in this skill are specifically the ones that look harmless at small scale and become severe at real scale — assume that gap exists until verified otherwise.

## Performance Anti-Pattern Catalog

### N+1 Queries

**The pattern:** Code fetches a collection (1 query), then iterates over it and fetches related data for each item individually (N additional queries) — instead of fetching the related data for the whole collection in one additional query or one join.

```
// ANTI-PATTERN: 1 query for posts, then 1 additional query per post
posts = fetchAllPosts()
for post in posts:
    author = fetchAuthorById(post.authorId) // N additional queries
```

**The fix:** Load the related data for the whole collection up front — commonly called eager loading, a join, or a batched/bulk fetch depending on your stack's terminology — rather than triggering a separate fetch per item.

```
// FIXED: load posts and their authors together, or in one batch step
posts = fetchAllPostsWithAuthors() // ORM "include"/"eager load"/join
// or, if a join isn't natural here:
posts = fetchAllPosts()
authorIds = posts.map(p => p.authorId).distinct()
authorsById = fetchAuthorsByIds(authorIds).indexById() // 1 batched query
for post in posts:
    author = authorsById[post.authorId] // no additional query
```

**How to spot it during review:** Look for any query/fetch call sitting textually inside a loop body, where the loop iterates over a result set from an earlier query. This is the single highest-signal thing to search for in this entire catalog.

**A nested version to also watch for:** A loop that fetches a collection, then for each item fetches _another_ collection, then loops over _that_ — multiplying the query count further (sometimes called a "2N+1" or deeper pattern). The fix is the same principle applied at each level: batch-fetch each level's related data once for the whole collection above it, not once per item.

**Caution — don't over-correct into over-eager loading.** Eagerly loading every possible relation "just in case" on every query wastes memory and bandwidth for data the current operation doesn't need. Load what _this_ operation actually needs, not the maximal possible graph — and watch for eager-loading configurations that accidentally traverse a circular relationship (A loads B, B loads A again), which can produce an enormous or infinite fetch.

### Missing or Mismatched Indexes

**The pattern:** A query filters, sorts, or joins on a column with no supporting index, forcing a full scan that gets slower as the table grows — invisible in development against a small table, severe in production against a large one.

**The fix:** Confirm the column(s) a frequent query filters/sorts/joins on have a supporting index, added via this project's own `database-schema-evolution` discipline. Verify the query's execution plan actually uses the index you expect — adding an index that the query planner doesn't end up using for this specific query shape solves nothing.

### Unbounded Result Sets

**The pattern:** A query, an API response, or an in-memory operation has no limit on how much data it can return or process, and the _caller_ — not the data source — is what currently keeps the result small, by happening to only have a little data today.

**The fix:** Add an explicit, deliberate bound: pagination for user-facing list endpoints, a hard maximum for internal batch operations, or a streaming/chunked approach for genuinely large data that can't be paginated away. Decide the bound at design time, not as a reactive fix after a query already returned more rows than expected.

### Blocking I/O in a Hot or Latency-Sensitive Path

**The pattern:** A request-handling path, a tight loop, or any latency-sensitive code makes a synchronous call to a dependency whose latency isn't tightly bounded — an external API, a slow downstream service, a cold cache — directly in that path, so the path's own speed and reliability become hostage to that dependency's.

**The fix:** Where the operation's correctness allows it, move the slow work out of the synchronous path — defer it to a background job, a queue, or an async callback — and apply this project's own resilience patterns (timeouts, circuit breakers) to any synchronous call that must remain in the path. See `idempotency-and-exactly-once-design` if moving the work to an async boundary introduces a new retry-risk operation.

### Unnecessary Recomputation

**The pattern:** The same expensive computation, query, or transformation is performed repeatedly for the same input within a single operation or a short time window, where the result could have been computed once and reused.

**The fix:** Memoize within the scope of a single operation where the inputs are genuinely unchanged, or introduce a cache with an explicit TTL and invalidation strategy for results reused across operations (see **Core Principle #6** — this is a design decision, not a free win).

### Algorithmic Complexity Mismatches

**The pattern:** Logic that is correct but scales worse than the data it operates on requires — most commonly, a nested loop over the same or related collections producing quadratic (or worse) behavior where a single pass, a lookup structure (e.g. a hash map keyed by the field being searched), or a different algorithm would scale linearly.

**The fix:** When a loop's body itself contains a search through another collection, check whether that inner search can be replaced by a lookup structure built once, outside the loop, rather than re-scanned on every iteration.

## Workflow

This workflow applies the same way whether you are writing new code or examining already-written code. The checks themselves don't change between the two moments — only _when_ you're running them, and what you do with what you find, differs slightly (see the notes at the end of each step).

### Step 1 — Confirm This Skill Actually Applies

Check the change against **When to Use** section: does it touch a database query, introduce a loop with per-item I/O, add a call to an external/slow dependency, or sit on a path that runs repeatedly or at scale? If none of these apply, stop here — this skill has nothing to check for this change.

### Step 2 — Scan for the N+1 Shape

Look for any query, fetch, or other I/O call sitting textually inside a loop body that iterates over a previous query's result set — the single highest-signal pattern in the **Performance Anti-Pattern Catalog** section. Check nested relationships too (a "2N+1" or deeper version of the same shape). If found, fix it by batching the related fetch for the whole collection, per the catalog's fix pattern — never by reverting to per-item fetches "because it's simpler to read."

### Step 3 — Check Index Support for Every Query Touched

For every new or changed query against a table that runs frequently or is expected to grow, confirm a supporting index exists for the columns it filters, sorts, or joins on — and confirm the query's actual execution plan uses that index, not just that one exists somewhere on the table. Route any new index through this project's `database-schema-evolution` discipline.

### Step 4 — Confirm Every Variable-Size Result Is Bounded

For every query, endpoint, or in-memory operation that returns a variable-size result, confirm an explicit bound exists (pagination, a hard maximum, or a streaming/chunked approach), following this project's own pagination conventions if it has them.

### Step 5 — Check Hot-Path I/O and Caching

For every new synchronous call to an external or slow dependency sitting in a latency-sensitive path, confirm a timeout is in place, or that the work has been moved out of the synchronous path where correctness allows it. For every new cache, confirm a stated TTL and invalidation strategy exist — not an unexamined "we cache this."

### Step 6 — Check for Algorithmic Complexity Mismatches

For any nested loop, confirm it isn't performing a repeated linear search through another collection where a lookup structure built once outside the loop would scale better.

### Step 7 — Self-Review Against the Quality Checklist

Run through [`resources/QUALITY_CHECKLIST.md`](resources/QUALITY_CHECKLIST.md) before considering the change complete. If a performance issue is found in Steps 2–6, record or address it according to the workflow that invoked this skill.

## Quality Checklist

Condensed version — full checklist lives in [`resources/QUALITY_CHECKLIST.md`](resources/QUALITY_CHECKLIST.md):

- [ ] No query or I/O call sits inside a loop body over a previous query's result set (N+1 shape) — related data for a collection is fetched in one batched step, not once per item.
- [ ] Eager-loading/batch-fetching is scoped to what the current operation actually needs — not the maximal possible relation graph "just in case," and not traversing a circular relationship.
- [ ] Every frequent or growing-table query has a supporting index, and the query's actual execution plan was checked to confirm the index is used.
- [ ] Every collection-returning query, endpoint, or in-memory operation has an explicit bound (pagination, hard limit, or streaming).
- [ ] No synchronous call to a slow or unreliable dependency sits directly in a latency-sensitive path without a timeout, or was moved out of the synchronous path where correctness allows it.
- [ ] Any new cache has a stated TTL and invalidation strategy — not introduced as an unexamined performance lever.
- [ ] No nested loop performs a repeated linear search through another collection where a lookup structure built once would scale better.
- [ ] Claims about this change's performance impact are backed by an actual measurement (query log, profiler, execution plan) where the change is non-trivial — not asserted from intuition alone.

## Anti-Patterns (Process-Level)

| Anti-Pattern                                                                                                                                                           | Why It Fails                                                                                                                                                                                                               |
| :--------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Optimizing a code path based on intuition, with no query log, profiler output, or other measurement confirming it's actually a cost                                    | Wastes effort on a path that may not matter, often at the expense of readability, while the actual bottleneck (if one exists) goes unaddressed                                                                             |
| Treating "it's fast in development" as evidence it will be fast in production                                                                                          | The patterns in this catalog are specifically the ones that are invisible at small scale and severe at real scale — development-scale testing alone cannot rule them out                                                   |
| Eager-loading every relation on every query "to be safe"                                                                                                               | Wastes memory and bandwidth on data the current operation doesn't need, and can silently explode in cost the moment a circular relationship is involved                                                                    |
| Adding a cache without deciding its TTL or invalidation strategy first                                                                                                 | Converts a performance question into an unexamined correctness risk — stale data served confidently is often worse than a slower, always-correct response                                                                  |
| Fixing an N+1 query by manually pre-fetching everything into application memory and joining it there, instead of using the data layer's own batching/join mechanism    | Usually trades one performance problem (many small queries) for another (one enormous, unfiltered fetch) — use the data layer's batching mechanism, which can push filtering and joining back down to where it's efficient |
| Reviewing for performance only when a feature is explicitly "about" performance, and skipping this check for every other feature that happens to add a query or a loop | Most N+1 defects are introduced in features that were never framed as performance-sensitive — this skill's trigger conditions in "When to Use" are deliberately broader than "performance work"                            |

## Best Practices

- Enable query logging (or your stack's equivalent visibility mechanism) in development specifically so a repeated, near-identical query pattern with only an ID changing becomes visible before it ever reaches review.
- When a fix for an N+1 pattern is applied, write a test or check that would fail if the pattern were reintroduced later (e.g. asserting on query count for a given operation), not only a test that the data returned is correct — correctness and query-count efficiency are different properties, and a regression can reintroduce the inefficiency while leaving correctness intact.
- When in doubt about whether a query needs an index, default to checking the execution plan rather than guessing from the query's apparent simplicity — a query that looks trivial can still trigger a full scan if it has no supporting index.
- Treat "doubling the data roughly doubles the response time" as an acceptable sign; treat "doubling the data roughly squares the response time" as a sign of an algorithmic complexity mismatch worth investigating immediately, not a "future problem."
- Prefer the data layer's own batching/eager-loading mechanism over a hand-rolled batching loop in application code wherever your stack provides one — it's usually better-tested and can push optimization (like only selecting needed columns) further down than a hand-rolled version would.

## Common Rationalizations

| Rationalization                                                            | Reality                                                                                                                                                                                                                                                                                |
| :------------------------------------------------------------------------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| "This loop only runs over a handful of items in our test data, it's fine." | Test data size is not production data size. The N+1 shape doesn't change based on how much data exists today — it changes how _badly_ the problem manifests as data grows, and by then it's a production incident, not a code review comment.                                          |
| "I'll optimize this later if it turns out to be slow."                     | The N+1 and unbounded-query patterns in this catalog cost nothing extra to avoid at write time — the "optimize later" cost is real (a production incident, a slow page, a database under unexpected load) and asymmetric to the near-zero cost of writing it correctly the first time. |
| "Adding an index for every query we write is overkill."                    | This isn't a blanket call to index everything — it's a call to deliberately decide, for each frequent or growing-table query, whether it needs index support, and to verify that decision against the actual execution plan rather than skipping the check entirely.                   |
| "Caching this will obviously make it faster, let's just add a cache."      | A cache without a stated TTL and invalidation strategy is a correctness liability wearing a performance improvement's clothes — decide the staleness bound deliberately, the same way you'd design any other piece of state.                                                           |
| "This synchronous external call has always been fine in practice."         | "Has been fine" describes the dependency's historical behavior, not a guarantee. A latency-sensitive path with no timeout on a synchronous call to an external dependency is one slow day away from that dependency's problem becoming this path's outage.                             |

## Output

This skill provides performance guidance and validation criteria that help ensure changes avoid common performance anti-patterns and follow scalable design practices. Its output is the set of implementation decisions, performance considerations, and validation results produced when applying the principles and checks defined in this skill. Any performance issue or optimization opportunity identified should be handled according to the workflow that invokes this skill, while non-trivial performance improvements should be supported by relevant evidence such as measurements, profiling results, query analysis, or execution plans.

**Worked example:** [`examples/example-n-plus-one-fix.md`](examples/example-n-plus-one-fix.md) walks through identifying and fixing an N+1 query introduced while extending the "resource reservation" example used across the other skill examples to show a list of a user's reservations with their resource details. Illustrative only, with pseudocode rather than any specific language, framework, or ORM syntax.
