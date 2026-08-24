# Money

An amount of one currency, counted in that currency's smallest unit.

## Invariants

| Field                          | Bound                                                                       |
|--------------------------------|-----------------------------------------------------------------------------|
| minor units                    | `>= 0`, a whole count, never a fraction; refused above what can be recorded |
| [`currency`](currency-code.md) | mandatory, and one [an amount can be recorded in](currency-code.md)         |

The currency sets the scale: 1500 minor units is 15.00 EUR, and 1500 JPY.

An amount can be given in the currency's main unit — 7200 for 7200 HUF, 12.50 for 12.50 EUR — and is scaled to
minor units by [the decimal places its currency names](currency-code.md)
([ADR 0011](../adr/0011-the-amount-is-scaled-to-minor-units-in-the-domain.md)). One finer than those places is
refused, never rounded.

## Made of / held by

A count of minor units and a [currency code](currency-code.md).
