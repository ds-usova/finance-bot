# Currency code

The currency an amount is denominated in, as its ISO 4217 code.

## Invariants

| Field  | Bound                                                            |
|--------|------------------------------------------------------------------|
| `code` | mandatory, non-blank; upper-cased before checking; ISO 4217 only |

A code also names how many decimal places an amount in it is written to, and answers whether an amount can be
recorded in it at all. A code with no decimal places at all — `XAU`, `XDR`, `XXX` — records no amount. Such a
code is still a valid code here, so one already stored on an expense is never refused after the fact.

## Made of / held by

A single ISO 4217 code.

- [Money](money.md) — every amount carries one, and is scaled by the decimal places it names.
- [AI Connector Service — intent extraction](../contracts/out/ai-connector.md) — an extraction may name the
  currency to assume.
- [Replace a person's preferences](../usecases/replace-the-preferences.md) — the chosen default is one.
- [Read a person's preferences](../usecases/read-the-preferences.md) — answers the one a person chose, or
  nothing.
- [Database](../contracts/out/database.md) — a person's chosen default is stored as one.
