# Expense filter

How much of a person's spending one listing asks for — which rows are wanted, and which page of them.

## Invariants

- A page size of at least 1 and at most 100.
- A page size above the maximum is refused, never trimmed down to it.
- 50 is the page size a caller who names none is given.
- An offset of zero or more.
- Every narrowing dimension is optional, and each is optional on its own: a status, a category, a period.
- A [spending period](spending-period.md) is both days or neither, and the last day counts whole.
- A filter narrowing to rows nobody has is a valid filter.
- A refusal names the part at fault and the bound it broke.

## Made of / held by

A status, a category id, a period, a page size and an offset.

- [Expense status](expense-status.md) — the kind of row it narrows to.
- [Spending period](spending-period.md) — the stretch of days it narrows to.
- [Browse a person's expenses](../usecases/browse-expenses.md) — the listing it bounds.
- [Browsing the ledger from a browser](../contracts/in/web-browse-api.md) — how each part arrives off a query, and
  what a refused one answers.
- [Database](../contracts/out/database.md) — how its parts become the predicates and the page of a read.
