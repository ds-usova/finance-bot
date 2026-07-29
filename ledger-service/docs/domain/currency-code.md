# Currency code

The currency an amount is denominated in, as its ISO 4217 code.

## Invariants

- A code is present and not blank.
- A code is upper case: it is put in upper case before it is checked, so every instance holds `EUR` however it
  was written.
- A code ISO 4217 does not know is refused.

## Made of / held by

A single ISO 4217 code.

- [Money](money.md) — every amount carries one.
- [AI Connector Service — intent extraction](../contracts/out/ai-connector.md) — an extraction may name the
  currency to assume.
