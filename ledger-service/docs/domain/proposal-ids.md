# Proposal ids

The pending entries one acceptance names, as the listing answered their ids.

## Invariants

| Part    | Bound    |
|---------|----------|
| count   | `1..100` |
| each id | `>= 1`   |
| order   | as given |

- A repeated id is refused, never collapsed.
- Over the maximum is refused, never trimmed to it.

## Made of / held by

A list of stored ids.

- [Accept the proposals a person chose](../usecases/accept-chosen-proposals.md) — the only thing that carries
  one.
- [Expense](expense.md) — what each id names, a pending one.
- [Browsing the ledger from a browser](../contracts/in/web-browse-api.md) — where the ids arrive, and where the
  same bounds are declared for a caller to read.
