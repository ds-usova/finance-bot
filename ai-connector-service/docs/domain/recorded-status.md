# Recorded status

Where the ledger has left an expense, as this service remembers it.

## Invariants

- **Values:** `PROPOSED | ACCEPTED | DISCARDED`.

## Made of / held by

- **Made of:** the status itself.
- **Held by:** every expense [kept in the store](../contracts/out/database.md), beside the
  [spending row](spending-row.md) the fact carried.
