# Money

An amount of one currency, counted in that currency's smallest unit.

## Invariants

- The count of minor units is zero or higher.
- A currency is present.
- The currency sets the scale: 1500 minor units is 15.00 EUR, and 1500 JPY.
- The amount is exact — a whole count of minor units, never a binary fraction.

## Made of / held by

A count of minor units and a [currency code](currency-code.md).

- [Expense intent](expense-intent.md) — the amount a create carries.
