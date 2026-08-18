# Stream position

Where a published fact stands on the ledger's stream — the pair the newest fact for a row is decided by.

## Invariants

- **Milliseconds:** positive.
- **Sequence:** zero or above.
- **Order:** by milliseconds, then by sequence.

## Made of / held by

- **Made of:** the milliseconds and the sequence within them.
- **Held by:** every expense [kept in the store](../contracts/out/database.md), as the position its row was last
  written from.
