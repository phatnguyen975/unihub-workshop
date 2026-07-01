# Performance and Efficiency Engineering Quality Checklist

Run this checklist against any change that meets this skill's "When to Use" trigger conditions. Every applicable item must pass.

## N+1 Query Detection

- [ ] No query, fetch, or other I/O call sits textually inside a loop body that iterates over a previous query's result set.
- [ ] Where related data for a collection is needed, it is loaded in one batched step (a join, an eager-load mechanism, or a single batched fetch keyed by the collection's IDs) — not once per item.
- [ ] Nested relationships (a collection whose items each have their own related collection) were checked for a "2N+1" or deeper version of the same pattern, not just the first level.
- [ ] Eager-loading is scoped to what the current operation actually needs — not the maximal possible relation graph "just in case."
- [ ] No eager-loading configuration traverses a circular relationship in a way that could produce an unbounded or runaway fetch.

## Indexing

- [ ] Every new or changed query that will run frequently, or against a table expected to grow, has a supporting index for the columns it filters, sorts, or joins on.
- [ ] The query's actual execution plan (not just "an index exists on this table somewhere") was checked to confirm the relevant index is actually used for this specific query shape.
- [ ] Any new index follows this project's `database-schema-evolution` discipline (e.g. non-blocking creation on an existing table) rather than being added as an afterthought outside that process.

## Bounded Results

- [ ] Every new or changed query, endpoint, or in-memory collection operation that returns a variable-size result has an explicit bound — pagination, a hard maximum, or a streaming/chunked approach.
- [ ] The bound's default and maximum values follow this project's own pagination conventions, if it has them, rather than an ad hoc choice for this one case.

## Hot-Path I/O

- [ ] No new synchronous call to an external service, a downstream system, or any dependency with non-trivial latency sits directly in a path this project treats as latency-sensitive without a timeout.
- [ ] Where the operation's correctness allows it, slow work was moved out of the synchronous path (async processing, a queue, a background job) rather than left to block the caller.
- [ ] If moving work to an async boundary introduces a new retry-risk operation, `idempotency-and-exactly-once-design` was applied to it.

## Caching

- [ ] Every new cache has an explicitly stated TTL.
- [ ] Every new cache has an explicitly stated invalidation strategy — not left as "we'll figure out staleness if it becomes a problem."
- [ ] The cache's staleness window was evaluated against the actual correctness requirements of the data being cached, not assumed acceptable by default.

## Algorithmic Complexity

- [ ] No nested loop performs a repeated linear search through another collection where a lookup structure (e.g. a hash map keyed by the searched field) built once outside the loop would scale better.
- [ ] For any new logic operating on a collection whose size grows with real usage, the growth behavior (linear, quadratic, or worse) was consciously considered, not left implicit in however the code happened to be written.

## Measurement Discipline

- [ ] Any claim that this change improves or doesn't harm performance is backed by an actual measurement (query log, profiler output, execution plan, or a benchmark) for any genuinely non-trivial change — not asserted from intuition alone.
- [ ] No optimization was applied to a path with no evidence it was actually a bottleneck, at the cost of readability or added complexity, "just in case."
- [ ] Testing or verification for this change used a realistic data volume where the patterns in this skill's catalog are known to be invisible at small scale (N+1, missing indexes, unbounded results) — not only a near-empty development dataset.

## Final Validation

- [ ] Performance issues discovered during validation should be handled by the process that invoked this checklist.
- [ ] Any optimization claim should include supporting evidence when the change has meaningful complexity or impact.
