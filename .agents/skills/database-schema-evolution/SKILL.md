---
name: database-schema-evolution
description: Applies the expand-migrate-contract pattern so that every migration is backward compatible with the application code currently running, and flags any operation that would lock or rewrite a large table. Use when adding, modifying, or removing a database table, column, index, or constraint — any change expressed as a migration against a relational schema. Do not use for changes to seed/reference data that involve no DDL.
---

# Database Schema Evolution

## Overview

A database migration is one of the few changes in a codebase that is often genuinely irreversible once applied to real data, and that runs against a live system serving real traffic. Unlike application code, which can usually be rolled back by deploying a previous version, a schema change frequently cannot be cleanly undone — and a naive migration deployed at the same time as the code that depends on it creates a window where old and new application instances disagree about what the schema looks like.

This skill governs how to evolve a schema safely: classify the risk of the change, sequence it correctly relative to the application code that depends on it, and use the **expand → migrate → contract** pattern for anything that isn't purely additive.

## When to Use

- Adding a new table, column, index, or constraint.
- Modifying an existing column's type, nullability, or default.
- Renaming or removing a table, column, index, or constraint.
- Backfilling or transforming existing data as part of a structural change.

## When NOT to Use

- Changes to seed or reference data that involve no DDL (no `CREATE`, `ALTER`, or `DROP`) — that's an application-level data concern, not a schema evolution.

## Core Principles

1. **Every migration must be deployable independently of application code, in either order, without breaking either version.** The migration should succeed while the _previous_ version of the application is still running, and the previous version of the application should keep working correctly while the new schema is in place. This decoupling is what makes it safe to deploy the schema change and the application change as two separate steps, in the correct order, rather than as one risky simultaneous change.
2. **Additive changes are safe by default; everything else needs a multi-step plan.** Adding a new table, a new nullable column, or a new index is low-risk on its own. Modifying or removing something the running application already depends on is not — it requires the **expand → migrate → contract** pattern below, not a single one-shot `ALTER`.
3. **Deployment order depends on the direction of the change.** When _adding_ something, the schema change deploys first, then the application code that uses it. When _removing_ something, the application code that stops using it deploys first, then the schema change that removes it. Getting this backwards — deploying code that already expects a column before the column exists, or dropping a column before every instance has stopped reading it — is one of the most common causes of a production incident at deploy time.
4. **Never rename or retype a column directly in one step.** A direct rename or type change breaks every query and every running instance still using the old name/type the instant it's applied. Always expand (add the new shape alongside the old), transition (dual-write and/or backfill, with application code that tolerates both), and only then contract (remove the old shape, once nothing depends on it anymore).
5. **Treat large tables as a distinct risk category.** An operation that completes in milliseconds on a small development database can hold a lock or rewrite an entire table for minutes or hours on a large production table — long enough to cause request timeouts and cascading failures. Test migrations against realistic data volumes, not just a near-empty development database, before judging them safe.
6. **Every migration needs an explicit rollback story.** "We'll figure it out if something goes wrong" is not a rollback plan. Know, before applying a migration, exactly what reverting it looks like — and for any change that is not trivially reversible (a destructive `DROP`, a data transformation), know the answer is "roll forward with a fix," not "undo," and plan accordingly.
7. **Naming and structural conventions are applied consistently, not per-migration improvisation.** Constraint names, audit columns, timestamp types, and other project-wide schema conventions exist so that any reader can predict a migration's shape without re-deriving it. A migration that ignores existing conventions "because this one case is different" erodes exactly the consistency conventions exist to provide.

## The Expand → Migrate → Contract Pattern

This is the central technique for any non-purely-additive schema change.

```
EXPAND                     MIGRATE                    CONTRACT
Add the new structure  ──► Transition application ──► Remove the old
alongside the old.         code and data to the       structure, once
Nothing is removed yet.    new structure. Both old    nothing depends
Both old and new app       and new shapes coexist     on it anymore.
versions still work.       during this phase.
```

### EXPAND

Add the new column, table, index, or constraint without touching anything that already exists. Use `NOT VALID` / deferred-validation forms where the database supports them, so the structural addition itself doesn't require an expensive validation pass against existing data up front. At the end of this phase, both the old and new application code can run correctly against the schema — nothing has been removed or changed in a way that breaks the previous version.

### MIGRATE

Transition the application and the data to the new structure:

- Deploy application code that writes to _both_ the old and new structures (dual-write), if data needs to move from one shape to another.
- Backfill historical data from the old structure into the new one, in small batches, in the background — not as one long-running statement that locks the table for its entire duration.
- Once the new structure is fully populated and verified, deploy application code that _reads_ from the new structure (switch reads), while still writing to both for a safety margin.

At every point during this phase, every running version of the application — old or new — must remain compatible with the current state of the schema.

### CONTRACT

Once the new structure has been the only one in use for long enough to be confident nothing still depends on the old one (no running instance still queries it, no external integration still reads it), remove the old structure in its own, separate migration: drop the old column, table, index, or constraint. This is the only phase that is actually destructive — and it happens last, deliberately, once everything that could be affected by it has already moved on.

**A subtle but important detail:** If the project uses an ORM or similar mapping layer, confirm the application has genuinely stopped _loading_ the old structure (not just stopped using it in business logic) before dropping it — some ORMs eagerly load every mapped column by default, which can cause a failure at the contract step even after the application logic itself no longer references the old column.

## Risk Classification

Use this to decide whether a change is safe as a single step, or needs the full expand-migrate-contract treatment.

| Risk Level | Example Changes                                                                                                                                                | Required Approach                                                                                                                                                                |
| :--------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Low**    | Add a new table; add a new nullable column or one with a default; add a non-unique index using a non-blocking/concurrent build mechanism                       | Single migration is generally sufficient. Deploy schema before the application code that uses it.                                                                                |
| **Medium** | Add a `NOT NULL` constraint to an existing column; add a unique constraint; add a foreign key to an existing column                                            | Validate in a non-blocking way first (e.g. add the constraint unvalidated, then validate separately) where the database supports it; otherwise treat as expand-migrate-contract. |
| **High**   | Rename a column or table; change a column's data type; remove a column, table, index, or constraint still in use; change cardinality between existing entities | Full expand → migrate → contract, across multiple separate deploys. Never attempted as a single migration.                                                                       |

## Workflow

### Step 1 — Classify the Change

Identify exactly what's being added, modified, or removed, and classify it per the **Risk Classification** section above. If it's High risk, plan the full expand-migrate-contract sequence before writing the first migration — don't start writing DDL and discover the risk level partway through.

### Step 2 — Confirm Deployment Order

State explicitly which deploys first: the schema change or the application code change. (See **Core Principle #3** — additive changes: schema first; removals: code first.) If this project's deployment pipeline can't guarantee that ordering reliably, that's a finding to raise, not a reason to skip planning the correct order anyway.

### Step 3 — Write the Migration

Follow the project's existing conventions (naming, audit columns, timestamp types, ID generation strategy — locate and read the project's own database/schema documentation before drafting, don't invent a parallel convention). Use the database's non-blocking/concurrent forms for operations on existing tables wherever the database supports them (e.g. building an index without taking a blocking lock, validating a new constraint as a separate, non-blocking step after adding it).

### Step 4 — Identify Lock and Rewrite Risk

For any operation touching an existing table, determine whether it requires a blocking lock or a full table rewrite at the database level (common culprits: adding an index without a non-blocking option, adding `NOT NULL` without a pre-validated default, changing a column's type). If so, and the table is large enough in production for this to matter, restructure the change to avoid it — typically by splitting into a non-blocking structural step plus a separate, batched backfill.

### Step 5 — Define the Rollback Story

For each migration, state explicitly what reverting it means. For a purely additive migration, this is usually straightforward (drop what was added). For a migration that transforms or removes data, state plainly that the rollback strategy is "roll forward with a corrective migration," not "undo" — and make sure anyone deploying this understands that distinction before it's needed under pressure.

### Step 6 — Test Against Realistic Data Volume

Run the migration against a copy of production-scale data, not just a near-empty development database, before considering it verified. A migration that completes instantly in development can take minutes or hours — and hold a lock the whole time — against a production-sized table.

### Step 7 — Self-Review Against the Quality Checklist

Run through [`resources/QUALITY_CHECKLIST.md`](resources/QUALITY_CHECKLIST.md) before presenting the migration as ready.

## Quality Checklist

Condensed version — full checklist lives in [`resources/QUALITY_CHECKLIST.md`](resources/QUALITY_CHECKLIST.md):

- [ ] The change was classified by risk level before writing any DDL.
- [ ] High-risk changes use the full expand → migrate → contract sequence across separate migrations — never a single one-shot operation.
- [ ] Deployment order (schema-first for additions, code-first for removals) was stated explicitly.
- [ ] No column or table is renamed or retyped directly — an expand/contract sequence was used instead.
- [ ] Lock-acquiring or table-rewriting operations on existing tables were identified and replaced with non-blocking equivalents where the database supports them.
- [ ] A rollback story exists for every migration, including an explicit "roll forward" statement for anything not cleanly reversible.
- [ ] The migration follows this project's existing naming and structural conventions, not an invented parallel convention.
- [ ] The migration was tested against realistic data volume, not just a near-empty development database.
- [ ] If an ORM or mapping layer is in use, it was confirmed to have stopped loading any column being dropped in a contract step.

## Anti-Patterns

| Anti-Pattern                                                                                         | Why It Fails                                                                                                                                                                                                              |
| :--------------------------------------------------------------------------------------------------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Renaming a column directly in one migration                                                          | Breaks every query and every running application instance still using the old name the instant it's applied — there is no point in the deploy where both old and new code can coexist correctly                           |
| Adding a `NOT NULL` constraint to an existing column with no default, in one step                    | Requires a full table scan to validate against every existing row, and fails outright if any existing row has a null value — both effects can take a production system down at deploy time                                |
| Bundling a destructive `DROP` in the same migration as the additive change it's supposedly replacing | Removes the safety margin the expand-migrate-contract pattern exists to provide — if anything is still depending on the old structure, there is no way to discover that before it's already gone                          |
| Deploying application code that expects a new column before the migration that adds it has run       | The exact wrong-order mistake Core Principle #3 exists to prevent — the new code fails immediately against the old schema, for every request, until the migration catches up                                              |
| Running a single large `UPDATE` to backfill an entire table in one statement                         | Holds a lock and generates a huge amount of write-ahead/transaction-log volume for the statement's entire duration, on a table that's supposedly still serving live traffic; batch the backfill into small chunks instead |
| Testing a migration only against a near-empty development database before approving it               | Lock duration and rewrite cost scale with table size — a migration that's instant in development can take the production table offline for the same operation at scale                                                    |
| Treating "we have a backup" as a rollback plan for a destructive schema change                       | A backup restores you to a point in time, losing everything written since — it is a disaster-recovery mechanism, not a deployment rollback strategy; plan a genuine roll-forward path instead                             |

## Best Practices

- Locate and read the project's own database/schema documentation before drafting a migration — naming conventions, ID strategy, audit-column pattern, and timestamp conventions should already be established; a new migration should match them, not introduce a one-off variant.
- Prefer the database's non-blocking/concurrent index-build and constraint-validation mechanisms by default for any operation on an existing table, even when the table is currently small — tables grow, and the migration will outlive the moment it was written for.
- Split a backfill into small, bounded batches with a brief pause between them, rather than one unbounded statement — this keeps lock duration and replication lag bounded and recoverable if something needs to be paused partway through.
- When a column is being phased out during a contract step, consider a brief observation period (logging or metrics on whether anything still reads/writes it) before the final `DROP`, rather than trusting code review alone to have found every caller.
- Keep each migration in its own file/step, named and dated according to the project's convention, and never edit a migration that has already been applied to any shared/production-like environment — add a new migration to correct it instead, the same way you would never rewrite a commit that's already been pushed and built upon.

## Common Rationalizations

| Rationalization                                                                         | Reality                                                                                                                                                                                                                                                       |
| :-------------------------------------------------------------------------------------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| "It's just a rename, the underlying data doesn't change."                               | The data may not change, but every query, every ORM mapping, and every running instance referencing the old name breaks simultaneously — "just a rename" is exactly the kind of change that most needs the expand-migrate-contract treatment, not least.      |
| "This table is small right now, a blocking operation is fine."                          | The migration will very likely outlive the moment the table was small. Writing it with the non-blocking form costs nothing extra today and removes a future incident entirely.                                                                                |
| "We'll just deploy the schema change and the code at the same time, it should be fine." | "At the same time" is never actually simultaneous — there is always a window, however brief, where some instances are on the old code and some on the new, or where the migration is mid-flight. That window is exactly where this class of incident happens. |
| "If something goes wrong we can always roll back the migration."                        | Many migrations — anything that drops data or transforms it irreversibly — cannot be cleanly rolled back once applied. Plan the rollback (or explicit roll-forward) story before applying it, not after something has already gone wrong.                     |
| "I tested it locally and it ran instantly, so it's safe."                               | Lock duration and rewrite cost are a function of table size, not query complexity — a migration that's instant against a few hundred local rows can be the single statement that takes a production table offline.                                            |

## Output

This skill does not produce a standalone document artifact in the way a spec or contract does — its output is the migration file(s) themselves, following this project's own migration-tool conventions and file location.

**Worked example:** [`examples/example-migration-sequence.md`](examples/example-migration-sequence.md) walks through a High-risk change — renaming a column on a table assumed to hold a large amount of data — using the full expand → migrate → contract sequence, continuing the domain-neutral "resource reservation" example used across the other skill examples. Illustrative SQL is shown using common, widely-portable syntax; adapt exact syntax to your specific database engine and migration tool.
