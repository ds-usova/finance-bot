# Category reference

A category or a grouping, by the ledger's id for it and the name it carried when the fact was published.

## Invariants

| Field  | Bound                |
|--------|----------------------|
| `id`   | `> 0`                |
| `name` | mandatory, non-blank |

## Made of / held by

The ledger's id, and the name published beside it.

- [Spending row](spending-row.md) — carries two, the category the expense is filed under and the grouping that
  category sits in.
- [The ledger's facts](../contracts/out/change-stream.md) — the fact payload both are read out of.
- [Learn what the ledger did with a message](../usecases/learn-message-outcome.md) — refiles a row under the pair
  a later fact carries.
- [Database](../contracts/out/database.md) — where each id and name are kept, per expense row.
