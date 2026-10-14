# Example expense

One expense an earlier message was recorded as, and what the person did with it.

## Invariants

| Field                           | Bound                         |
|---------------------------------|-------------------------------|
| `description`                   | mandatory, non-blank          |
| `merchant`                      | optional, never left unstated |
| `amount`                        | mandatory, non-blank          |
| [`currency`](currency-code.md)  | mandatory                     |
| `categoryName`                  | optional, never left unstated |
| `groupingName`                  | optional, never left unstated |
| [`outcome`](example-outcome.md) | mandatory                     |

The amount reads as a [spending row](spending-row.md) carries it, in the currency's main unit.

## Made of / held by

An expense and what the person did with it. Built fresh for each recall, and never stored itself.

- [Message example](message-example.md) — holds one per decided expense of an earlier message.
- [Example outcome](example-outcome.md) — what the person did with it.
- [Currency code](currency-code.md) — what the amount is counted in.
- [Category classifier](../contracts/out/classifier.md#what-of-a-persons-history-is-sent) — told part of one beside
  the message being classified.
- [Learn what the ledger did with a message](../usecases/learn-message-outcome.md) — writes and refiles the row
  one is read from.
- [Delete the messages kept past their age](../usecases/purge-messages.md) — removes that row with its message.
- [Database](../contracts/out/database.md) — where that row is kept.
