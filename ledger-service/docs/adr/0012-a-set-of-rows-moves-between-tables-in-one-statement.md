# ADR 0012: A set of rows moves between tables in one statement

- **Status:** Accepted
- **Date:** 2026-08-05
- **Source:** [Accept or Discard a Reported Proposal from the Chat](../../../docs/implemented/15-accept-or-discard-a-reported-proposal/plan.md)

## Context

Confirming a report moves every `expense_proposal` row under one `MessageReference` into `expense` and removes it. The row's existence is the status: an unresolved message's proposals are in `expense_proposal`, a
resolved message's are not.

Nothing stops the same report being confirmed twice. Telegram leaves an inline keyboard on the message until an
edit clears it, a client can be stale, and a callback query can be redelivered — so two confirmations of the same
report can arrive at once. Written the obvious way, as read the rows, insert them, then delete them, both
transactions read the same rows under `READ COMMITTED` and the expenses are stored twice.

## Decision

Accept is a single data-modifying CTE: a `DELETE … RETURNING` whose result feeds an `INSERT`.

```sql
WITH accepted AS (
    DELETE FROM expense_proposal
    WHERE user_id = :userId AND message_reference = :messageReference
    RETURNING …
)
INSERT INTO expense (…) SELECT … FROM accepted
```

The delete is what decides which rows move, and the insert reads only what its own delete returned. The statement
returns the row count, which is how the caller tells a resolution from a no-op.

## Consequences

- A second confirmation is harmless without a lock, a status column, or a uniqueness constraint. Concurrently, the
  losing transaction's `DELETE` blocks on the winner's row locks and then matches nothing, so it returns `0` and
  inserts nothing; sequentially, the rows are already gone.
- A count of `0` is the signal that something else already resolved the report, which is what lets the use case
  distinguish an already-accepted report from an unknown one by counting `expense` rows afterwards.
- The statement is Postgres-only. A data-modifying CTE and `DELETE … RETURNING` are not portable, so this module's
  persistence tests must run against a real Postgres rather than an in-memory database — which they already do.
- Nothing observes the intermediate state, because there is none: no window exists in which a row is in both
  tables or in neither.
- The rows are moved by SQL rather than by mapping them through the domain, so no `Expense` instance is built on
  this path and any invariant an `Expense` factory would enforce is not applied. It holds only because the two
  tables carry the same columns with the same types and constraints; a column that diverges breaks it silently.
- Discard is the same statement without the insert, so both resolutions are atomic in themselves and neither needs
  a transaction spanning two calls.
