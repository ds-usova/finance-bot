# ADR 0018: A proposal is a status on the expense table

- **Status:** Accepted
- **Supersedes:** 0006, 0012
- **Date:** 2026-08-18
- **Source:** [One Expense Table, With a Status](../../../docs/implemented/34-one-expense-table-with-a-status/plan.md)

## Context

[ADR 0006](0006-an-expense-proposal-is-a-table-and-an-entity-of-its-own.md) gave a proposal its own table,
entity, port and adapter, mirroring `expense` column for column, so that a read of `expense` never needed a
status predicate. Nothing ever diverged: no column grew on one table without the other, and no lifecycle rule
told them apart beyond which table a row was in. The split cost two of everything behind one API, a `UNION ALL`
on every browse read, and — per [ADR 0012](0012-a-set-of-rows-moves-between-tables-in-one-statement.md) —
acceptance as a table move instead of a column update, because the row's table membership was its status.

ADR 0012 made that move safe under a redelivered confirmation: a single `DELETE … RETURNING` feeding an
`INSERT`, whose row count told a resolution from a no-op. The same guarantee is available from a single
`UPDATE` once status lives in a column rather than in which table a row is in, without a second table to keep
in step and without the id a proposal held changing the moment it is accepted.

## Decision

A proposal is a status on the expense table, and moving between statuses is one `UPDATE` whose row count
distinguishes a resolution from a no-op.

`expense_proposal` is gone. `expense` carries a `status` column (`PENDING` | `RECORDED`), with a check
constraint bounding it to those two values and `ck_expense_pending_has_message` enforcing that a `PENDING` row
always names the message it came from. Accepting is
`UPDATE expense SET status = 'RECORDED', updated_at = :now WHERE … AND status = 'PENDING'`; under
`READ COMMITTED`, a second, concurrent acceptance waits on the first's row locks, re-evaluates the predicate
against the now-`RECORDED` rows, matches nothing, and returns `0` — ADR 0012's guarantee, without a second
table. `ExpenseRepositoryAdapterConcurrencyTest` races two callers on the same message and shows one answer
three and the other zero.

An entry keeps its id and its `created_at` across the status change, which the table move ADR 0012 decided
could not offer: a moved row took a new id there, and the id an `ExpenseProposal` and the `Expense` it became
carried were never the same. `openapi/ledger-api.yaml`'s `Expense.id` now reads unique across every entry of
the caller's, not within a status, and names that entry for its whole life.

Reads that mean "recorded spending" — `totalsByCurrency`, `countByMessageReference` — carry a
`status = 'RECORDED'` predicate on the one table instead of reading a table that held only recorded rows.
`findPage` and `countMatching` lost their `UNION ALL`.

The migration is `V009__merge_expense_proposal_into_expense.sql`: it adds the column and both constraints,
copies every `expense_proposal` row across as `PENDING`, and drops the table. The publication migration that
had held `V009` and had run nowhere is renumbered to `V010__publish_ledger_changes.sql`, capturing `expense`
and `category` and no longer naming `expense_proposal`.

## Consequences

- One table, one entity, one port, one adapter: a column added for one status is available to the other, and no
  second schema needs to be kept in step by hand.
- Every read of a person's spending or of what waits for them now states its status as a predicate; no read gets
  it for free from which table it reads.
- Acceptance is idempotent under a redelivered confirmation without a lock, a uniqueness constraint, or a second
  table — the same property ADR 0012 gave the table move, carried by the `UPDATE`'s row count instead of the
  `DELETE … RETURNING`'s.
- An id is stable for an entry's whole life, including across acceptance, which the table move could not
  provide.
- Nothing forbids `RECORDED -> PENDING` in the schema; only the writes do, since the sole insert that writes
  `PENDING` is the one that creates a proposal, and both accept statements guard `status = 'PENDING'`.
- 2026-08-18 — the publication migration named above is now `V010__publish_facts_through_an_outbox.sql`, and it
  captures an `outbox` table rather than `expense` and `category`
  ([ADR 0019](../../../docs/adr/0019-the-ledger-publishes-facts-through-a-transactional-outbox-rather-than-its-own-row-changes.md)).
  The renumbering this ADR describes still holds; what that migration publishes no longer does.
