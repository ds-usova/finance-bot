# Currency code

The currency an amount is denominated in, as a code ISO 4217 knows.

## Invariants

| Field  | Bound                                           |
|--------|-------------------------------------------------|
| `code` | mandatory, non-blank, and a code ISO 4217 knows |

Any casing is accepted, and the code is upper case from then on.

## Made of / held by

The code itself.

- [Spending row](spending-row.md) — the currency its amount is written in.
- [Example expense](example-expense.md) — the currency a recalled amount is counted in.
- [Intent extraction](../contracts/in/intent-extraction.md) — where a caller names the currency to assume.
- [Record the spending a user's message names](../usecases/extract-intents.md) — hands the assumed one to the
  model.
- [The ledger's facts](../contracts/out/change-stream.md) — the fact payload one is read out of.
- [Database](../contracts/out/database.md) — where one is kept, per expense row.
