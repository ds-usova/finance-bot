# Recorded change

One change the ledger made, as this service reads it.

## Invariants

- **Kind:** a [spending row change](spending-row-change.md) or a [category row change](category-row-change.md),
  and nothing else.
- **Carries an operation and the id of the row it is about.**

## Made of / held by

- **Made of:** whichever of the two kinds the change is.
- **Read from:** [the ledger's changes](../contracts/out/change-stream.md), one entry at a time.
- **Held by:** [Learn what the ledger did with a message](../usecases/learn-message-outcome.md), as what it is
  asked to apply.
