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
- Nothing here says whose ids these are. Who may accept them is settled against the caller's own rows.

## Made of / held by

A list of stored ids.

- [Accept the proposals a person chose](../usecases/accept-chosen-proposals.md) — the only thing that carries
  one.
- [Expense proposal](expense-proposal.md) — what each id names.
- [Browsing the ledger from a browser](../contracts/in/web-browse-api.md) — where the ids arrive, and where the
  same bounds are declared for a caller to read.
