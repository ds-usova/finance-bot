# Expense filter

How much of a person's spending one listing asks for — which rows are wanted, and which page of them.

## Invariants

| Part         | Bound                                        |
|--------------|----------------------------------------------|
| `limit`      | `1..100`, default `50`                       |
| `offset`     | `>= 0`, default `0`                          |
| `status`     | optional                                     |
| `categoryId` | optional                                     |
| `period`     | optional; both days or neither, last counted |

- Over `limit`'s maximum is refused, never trimmed to it.
- Matching nothing is valid.
- A refusal names the part and the bound.

## Made of / held by

A status, a category id, a period, a page size and an offset.

- [Expense status](expense-status.md) — the kind of row it narrows to.
- [Spending period](spending-period.md) — the stretch of days it narrows to.
- [Browse a person's expenses](../usecases/browse-expenses.md) — the listing it bounds.
- [Browsing the ledger from a browser](../contracts/in/web-browse-api.md) — how each part arrives off a query, and
  what a refused one answers.
- [Database](../contracts/out/database.md) — how its parts become the predicates and the page of a read.
