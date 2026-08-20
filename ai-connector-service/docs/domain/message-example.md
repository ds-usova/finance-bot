# Message example

One earlier message of the same person, and how their spending was filed from it.

## Invariants

| Field      | Bound                                                               |
|------------|---------------------------------------------------------------------|
| `text`     | mandatory, non-blank — the earlier message, character for character |
| `expenses` | mandatory, at least one, none absent                                |

A message whose spending the person has not decided on yet is no example, which is why there is always at least
one expense.

## Made of / held by

The earlier message's text, and the decided expenses it was recorded as.

- [Example expense](example-expense.md) — one per decided expense.
- [Recall the person's own worked examples](../usecases/recall-examples.md) — answers them, closest first.
- [AI provider](../contracts/out/ai-provider.md#what-the-two-messages-carry) — reads them beside the message
  being handled.
- [Database](../contracts/out/database.md) — where the message and its expenses are kept.
