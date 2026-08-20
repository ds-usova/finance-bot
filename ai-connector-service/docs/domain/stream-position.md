# Stream position

Where a published fact stands on the ledger's stream — the pair the newest fact for a row is decided by.

## Invariants

| Field | Bound  |
|-------|--------|
| `ms`  | `> 0`  |
| `seq` | `>= 0` |

Two positions order by `ms` first, and by `seq` within the same millisecond.

## Made of / held by

The milliseconds of the stream entry, and the sequence within them.

- [The ledger's facts](../contracts/out/change-stream.md) — the entry id one is read out of.
- [Learn what the ledger did with a message](../usecases/learn-message-outcome.md) — compares the fact's against
  the row's before writing.
- [Database](../contracts/out/database.md) — where each expense row keeps the position it was last written from.
