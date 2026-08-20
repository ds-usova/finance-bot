# Message identity

The person a message was written by and the message itself — the pair a kept message is filed under.

## Invariants

| Field                                                                             | Bound                |
|-----------------------------------------------------------------------------------|----------------------|
| `userId`                                                                          | an `int64`           |
| [`incomingMessageId`](../../../ledger-service/docs/domain/incoming-message-id.md) | mandatory, non-blank |

Built from a token's claims, the subject must be present, non-blank, and parse as a whole number.

An identity is whole or absent: nothing carries a person without a message, or a message without a person.

## Made of / held by

The person's id in the ledger, and the id of the message they sent.

- [Incoming message id](../../../ledger-service/docs/domain/incoming-message-id.md) — what the message half
  names.
- [Spending row](spending-row.md) — yields one where the row names a message, as the key the expense is hung off.
- [Intent extraction](../contracts/in/intent-extraction.md#the-token) — the token claims one is read from, never
  the request.
- [Record the spending a user's message names](../usecases/extract-intents.md) — files the message under it.
- [Recall the person's own worked examples](../usecases/recall-examples.md) — reaches a person's earlier messages
  by it.
- [Database](../contracts/out/database.md) — the key a kept message is filed under.
