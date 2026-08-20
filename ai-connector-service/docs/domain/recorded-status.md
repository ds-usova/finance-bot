# Recorded status

Where the ledger has left an expense, as this service remembers it.

## Invariants

- `PROPOSED | ACCEPTED | DISCARDED`

## Made of / held by

- [Spending row](spending-row.md) — the entry a status is carried beside.
- [Example outcome](example-outcome.md) — the two of the three a person has decided.
- [The ledger's facts](../contracts/out/change-stream.md) — which published fact means which of the three.
- [Learn what the ledger did with a message](../usecases/learn-message-outcome.md) — reads one off the fact and
  writes it to the row.
- [Database](../contracts/out/database.md) — where one is kept, per expense row.
