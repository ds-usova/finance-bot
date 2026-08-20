# Message example

One earlier message of the same person, and how their spending was filed from it.

## Invariants

- **Text:** present, non-blank — the earlier message, character for character.
- **Expenses:** present, at least one, none of them absent.
- **Copied:** the expenses are taken on construction, so the list handed in cannot change them afterwards.

## Made of / held by

- **Made of:** the earlier message's text, and its [example expenses](example-expense.md).
- **Held by:** [Recall the person's own worked examples](../usecases/recall-examples.md), which answers them.
- **Read from:** the message and its expenses as they are [kept in the store](../contracts/out/database.md).
