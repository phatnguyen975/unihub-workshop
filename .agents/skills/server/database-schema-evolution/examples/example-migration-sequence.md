# Worked Example: Renaming a Column on a Large Table

> [!NOTE]
> This is an illustrative, domain-neutral example showing how to apply this skill. It continues the "resource reservation with capacity limit" example used across the other skill examples. The `resources` table (introduced in the `task-planning-and-breakdown` example's migration task) was originally built with a column named `capacity_left` — and the team has decided this should be renamed to `remaining_capacity` for clarity, matching the domain language used everywhere else in the spec and code (`Capacity`, `decrement`, `CapacityExhaustedError`). The `resources` table is assumed to be large enough in production that a blocking, single-step rename is not acceptable. SQL shown uses common, widely-portable syntax — adapt exact syntax to your specific database engine and migration tool.

## Step 1 — Classify the Change

A column rename on an existing, actively-queried table is **High risk** per the Risk Classification table — it requires the full expand → migrate → contract sequence, never a single-step rename.

## Step 2 — Confirm Deployment Order

This is a multi-deploy sequence, not a single ordering decision:

1. Deploy **Migration 1 (Expand)**.
2. Deploy application code that dual-writes to both columns.
3. Run the backfill.
4. Deploy application code that reads from the new column only (still dual-writing for safety).
5. Confirm nothing still depends on the old column.
6. Deploy application code that stops writing to the old column entirely.
7. Deploy **Migration 2 (Contract)**.

Each numbered step above is its own deploy. None of them are combined.

## Migration 1 — Expand

```sql
-- Add the new column alongside the old one. Nullable for now — no
-- application code writes to it yet, so it cannot have a value until
-- the next deploy.
ALTER TABLE resources
    ADD COLUMN remaining_capacity INTEGER;
```

This is a metadata-only operation on most database engines — adding a nullable column with no default does not require rewriting existing rows, so it is fast and non-blocking even on a large table.

**Rollback:** `ALTER TABLE resources DROP COLUMN remaining_capacity;` — trivial, since nothing depends on this column yet.

## Deploy: Dual-Write Application Code

The application is updated to write to _both_ columns on every update, while still reading from the old column (`capacity_left`) as the source of truth:

```
function decrementCapacity(resource, byAmount):
    newValue = resource.capacity_left - byAmount
    update resources set
        capacity_left = newValue,
        remaining_capacity = newValue
    where id = resource.id
```

At this point, both old and new application instances (if a rolling deploy is in progress) still work correctly: old instances only know about `capacity_left`, which is still being kept accurate; new instances write both.

## Backfill — Populate Historical Rows

Existing rows created before the dual-write deploy don't yet have a value in `remaining_capacity`. Backfill it in small, bounded batches — not as one unbounded `UPDATE`:

```sql
-- Run repeatedly, in batches, until no rows remain with a NULL value.
-- Each batch is small enough to avoid holding a long-running lock.
UPDATE resources
SET remaining_capacity = capacity_left
WHERE remaining_capacity IS NULL
    AND id IN (
        SELECT id FROM resources
        WHERE remaining_capacity IS NULL
        LIMIT 1000
    );
```

This statement is re-run (by a script, a scheduled job, or a one-off maintenance task — whichever this project's tooling supports) until it affects zero rows, confirming every existing row now has both columns populated and in agreement.

## Deploy: Switch Reads to the New Column

Once the backfill is confirmed complete, deploy application code that reads from `remaining_capacity` as the source of truth, while _still_ dual-writing to `capacity_left` for a safety margin:

```
function decrementCapacity(resource, byAmount):
    newValue = resource.remaining_capacity - byAmount // ← now reads new column
    update resources set
        capacity_left = newValue, // still written, for rollback safety
        remaining_capacity = newValue
    where id = resource.id
```

If anything were to go wrong with the new column at this point, rolling back the application deploy alone is sufficient — `capacity_left` is still accurate, since it's still being written.

## Observation Period

Before removing the old column, confirm nothing else still depends on it: check for any other service, report, or ad hoc query that reads `capacity_left` directly. A brief logging or metrics check on whether the old column is still being _read_ anywhere (not just by this application) is worth doing here, rather than relying solely on a code search to have found every caller.

## Deploy: Stop Writing to the Old Column

Once confident nothing reads `capacity_left` anymore, deploy application code that stops writing to it:

```
function decrementCapacity(resource, byAmount):
    newValue = resource.remaining_capacity - byAmount
    update resources set
        remaining_capacity = newValue // capacity_left no longer written
    where id = resource.id
```

## Migration 2 — Contract

Only now, once application code has been confirmed to neither read nor write `capacity_left`, is it safe to remove:

```sql
ALTER TABLE resources
    DROP COLUMN capacity_left;
```

**Rollback:** Explicitly **not** a simple undo — if this needed to be reverted, the rollback story is "roll forward": re-add the column via a new migration and re-populate it from `remaining_capacity` via the same backfill technique used above, then re-deploy the dual-write code. This is stated plainly rather than implying `DROP COLUMN` can simply be reversed.

## What This Example Demonstrates

- The rename never happened directly — it was decomposed into add, dual-write, backfill, switch-read, stop-write, and finally drop, each its own deploy.
- At every single point in the sequence, both whatever version of the application happens to be running is compatible with the schema as it exists at that moment.
- The backfill was batched, not a single unbounded statement against a large table.
- The contract step's rollback story is honestly stated as "roll forward," not a false promise of a clean undo.
- The "subtle but important detail" called out in `SKILL.md` — checking whether anything still _reads_ the old column, beyond just this application's own business logic — was treated as its own explicit step before the final `DROP`, not skipped.
