# Example outcome

What the person did with an expense an earlier message was recorded as.

## Invariants

- **Values:** `ACCEPTED | DISCARDED`.
- **Decided only:** an expense the person has not yet decided on is no example.

## Made of / held by

- **Made of:** the outcome itself.
- **Held by:** an [example expense](example-expense.md).
- **Narrower than:** the [recorded status](recorded-status.md) the store keeps, which also holds the undecided
  ones.
