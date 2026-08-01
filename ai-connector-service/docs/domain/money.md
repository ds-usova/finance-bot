# Money

An amount of money a user stated: a whole number of minor units, and the currency those are units of. Exact at
every step, and never a binary floating-point number.

## Invariants

- **Currency:** present, and one [Currency code](currency-code.md) accepts.
- **Sign:** never negative.
- **Written form:** plain decimal digits, with a fractional part or without one. Scientific notation is refused.
- **Precision:** at most as many fractional digits as the currency has. A finer amount is refused, never
  rounded.

## Made of / held by

- **Made of:** a count of minor units · a [Currency code](currency-code.md), which fixes how many minor units
  make one unit.
- **Held by:** [Expense intent](expense-intent.md), as the amount of an expense.
- **Crossing out:** an amount reaches the ledger as whole minor units and a code —
  [the expense proposal tool](../contracts/out/ledger-mcp.md).
