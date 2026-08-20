# Example outcome

What the person did with an expense an earlier message was recorded as.

## Invariants

- `ACCEPTED | DISCARDED`
- an expense the person has not decided on is no example

## Made of / held by

- [Example expense](example-expense.md) — carries one.
- [Recorded status](recorded-status.md) — what the store keeps; this is the two of its three that a person has
  decided.
- [AI provider](../contracts/out/ai-provider.md#what-the-two-messages-carry) — where the model reads it, on the
  expense's own line.
