# Spending row

One proposed or recorded expense, whole, as the ledger published it.

## Invariants

| Field                              | Bound                                                                    |
|------------------------------------|--------------------------------------------------------------------------|
| `expenseId`                        | `> 0`                                                                    |
| `userId`                           | `> 0`                                                                    |
| `incomingMessageId`                | optional, never left unstated                                            |
| `description`                      | mandatory, non-blank                                                     |
| `merchant`                         | optional, never left unstated                                            |
| `amount`                           | mandatory, non-blank, a decimal string in the currency's main unit       |
| [`currencyCode`](currency-code.md) | mandatory                                                                |
| [`category`](category-ref.md)      | mandatory                                                                |
| [`grouping`](category-ref.md)      | mandatory — an expense is always filed under a category that sits in one |

The amount is never rescaled here. Where the row names a message, it yields the
[message identity](message-identity.md) the expense is hung off.

## Made of / held by

The ledger's own id for the expense, the person it belongs to, the message it came from, the description, the
merchant, the amount and its currency, the category it is filed under, and the grouping that category sits in.

- [Message identity](message-identity.md) — what a row naming a message yields.
- [Currency code](currency-code.md) — what the amount is counted in.
- [Category reference](category-ref.md) — both the category and the grouping.
- [Recorded status](recorded-status.md) — where the fact leaves the expense, carried beside the row.
- [Stream position](stream-position.md) — where that fact stands on the stream.
- [Example expense](example-expense.md) — what a decided row is recalled as.
- [The ledger's facts](../contracts/out/change-stream.md) — the fact payload one is read out of.
- [Learn what the ledger did with a message](../usecases/learn-message-outcome.md) — applies one to the expense's
  row.
- [Database](../contracts/out/database.md) — where one is kept.
