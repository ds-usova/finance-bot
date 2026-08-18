# Currency code

The currency an amount is denominated in, as a code ISO 4217 knows.

## Invariants

- **Present:** neither absent nor blank.
- **Casing:** any casing is accepted, and the code is upper case from then on.
- **Known:** a code ISO 4217 does not know is refused.

## Made of / held by

- **Made of:** the code itself.
- **Held by:** the [caller's assumed currency](../contracts/in/intent-extraction.md).
- **Also held by:** a [spending row](spending-row.md), as the currency its amount is written in.
