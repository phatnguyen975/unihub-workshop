# Database Schema Evolution Quality Checklist

Run this checklist against every migration before considering it ready to deploy. Every item must pass.

## Classification

- [ ] The change was classified as Low, Medium, or High risk before any DDL was written (see SKILL.md's Risk Classification table).
- [ ] If the classification was uncertain, the change was treated as the _higher_ risk category by default, not the lower one.

## Deployment Ordering

- [ ] For an additive change, it was confirmed that the schema migration will deploy _before_ the application code that depends on it.
- [ ] For a removal, it was confirmed that the application code that stops using the structure deploys _before_ the migration that removes it.
- [ ] If the project's deployment pipeline cannot guarantee this ordering automatically, that gap was explicitly flagged, not silently assumed to be fine.

## Expand-Migrate-Contract Discipline (for Medium/High risk changes)

- [ ] No column or table is renamed directly in a single migration.
- [ ] No column's data type is changed directly in a single migration.
- [ ] The expand step adds the new structure without removing or altering anything the current application depends on.
- [ ] The migrate step's dual-write/backfill logic is described or implemented in small, bounded batches — not as one large, unbounded operation.
- [ ] The contract step (removal of the old structure) is its own, separate migration, applied only after confirming nothing still depends on the old structure.
- [ ] If an ORM or mapping layer is used, it was confirmed to have stopped _loading_ (not just using) any column being dropped before the contract step runs.

## Lock and Rewrite Risk

- [ ] Every operation on an existing table was checked for whether it acquires a blocking lock or triggers a full table rewrite at the database level.
- [ ] Index creation on an existing table uses the database's non-blocking/concurrent mechanism, if one is available.
- [ ] Adding a `NOT NULL` constraint to an existing column either provides a default that avoids a full-table validation scan, or is split into an unvalidated-then-validated sequence where the database supports it.
- [ ] No single migration performs an unbounded `UPDATE`, `DELETE`, or backfill across an entire large table in one statement.

## Rollback Story

- [ ] Every migration has an explicit, stated rollback approach.
- [ ] For any migration that is not cleanly reversible (data loss, irreversible transformation), the rollback story explicitly says "roll forward with a corrective migration" rather than implying an undo is possible.
- [ ] A backup/snapshot is not being relied upon as the sole rollback mechanism for a destructive change that has its own dedicated forward-fix plan.

## Conventions

- [ ] The migration's naming follows this project's existing migration file-naming convention.
- [ ] Constraint names, audit columns, ID generation strategy, and timestamp types match this project's existing conventions — verified against the project's own schema documentation, not assumed from general habit.
- [ ] Indexing strategy for any new column matches the access patterns already documented for this table, where such documentation exists.

## Testing

- [ ] The migration was tested against a dataset of realistic production scale, not only a near-empty development database.
- [ ] If the migration includes a data backfill or transformation, its output was spot-checked against expected results on the realistic-scale dataset.
- [ ] The migration was confirmed to run cleanly against a schema that already has every prior migration applied, in the project's actual migration order — not tested in isolation against a hand-crafted starting schema.

## Final Gate

- [ ] No already-applied migration (one that has run against any shared or production-like environment) was edited in place; any correction is a new migration.
- [ ] The migration's risk classification, deployment order, and rollback story were all stated explicitly to a human reviewer before this migration is applied to a shared environment.
