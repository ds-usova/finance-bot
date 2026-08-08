# Expense status

Whether a piece of spending is already in the ledger, or still waiting for its user to decide.

## Invariants

- `PENDING | RECORDED`, and no others.
- `PENDING -> RECORDED` only. Never the reverse.
- Which one a row carries is which table holds it, never a stored column
  ([ADR 0012](../adr/0012-a-set-of-rows-moves-between-tables-in-one-statement.md)).

## Made of / held by

A closed set of two values.

- [Expense](expense.md) — the recorded one.
- [Expense proposal](expense-proposal.md) — the pending one.
- [Expense filter](expense-filter.md) — narrows a listing to one of the two.
- [Browse a person's expenses](../usecases/browse-expenses.md) — lists both kinds together, and carries the status
  on every entry.
- [Resolve a reported proposal](../usecases/resolve-a-reported-proposal.md) — what turns the pending one into the
  recorded one.
