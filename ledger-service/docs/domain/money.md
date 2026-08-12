# Money

An amount of one currency, counted in that currency's smallest unit.

## Invariants

| Field                                | Bound                                |
|--------------------------------------|--------------------------------------|
| minor units                          | `>= 0`, a whole count, never a fraction |
| [`currency`](currency-code.md)       | mandatory                            |

The currency sets the scale: 1500 minor units is 15.00 EUR, and 1500 JPY.

## Built from the main unit

An amount can be given in the currency's main unit — 7200 for 7200 HUF, 12.50 for 12.50 EUR — and is scaled to
minor units by that currency's own number of decimal places
([ADR 0011](../adr/0011-the-amount-is-scaled-to-minor-units-in-the-domain.md)).

Refused, never adjusted:

- No amount given.
- A currency with no minor unit at all.
- An amount finer than its currency's decimal places.
- An amount too large to record.

## Made of / held by

A count of minor units and a [currency code](currency-code.md).
