# Expense status

Whether a piece of spending is already in the ledger, or still waiting for its user to decide.

## Invariants

- `PENDING | RECORDED`
- `PENDING -> RECORDED` only

## Made of / held by

- [Expense](expense.md) — carries one, for its whole life.
- [Expense filter](expense-filter.md) — narrows a listing to one of the two.
- [Browse a person's expenses](../usecases/browse-expenses.md) — narrows or answers both.
- [Change an entry's category](../usecases/change-an-expense-category.md) — names one on the way in.
- [Resolve a reported proposal](../usecases/resolve-a-reported-proposal.md) — what turns the pending one into the
  recorded one.
