# Example expense

One expense an earlier message was recorded as, and what the person did with it.

## Invariants

- **Description:** present, non-blank.
- **Amount:** present, non-blank, as a [spending row](spending-row.md) carries it.
- **Currency:** present, a [currency code](currency-code.md).
- **Category name:** stated as present or as absent, never left unstated.
- **Grouping name:** the same.
- **Outcome:** present, an [example outcome](example-outcome.md).

## Made of / held by

- **Made of:** the description, the amount, the currency, the category it is filed under, the grouping that
  category sits in, and the outcome.
- **Held by:** a [message example](message-example.md).
- **Read from:** the expense as it is [kept in the store](../contracts/out/database.md), built fresh for each
  recall and never stored itself.
- **Whose row is:** written and refiled by [Learn what the ledger did with a
  message](../usecases/learn-message-outcome.md), and removed by [Delete the messages kept past their
  age](../usecases/purge-messages.md).
