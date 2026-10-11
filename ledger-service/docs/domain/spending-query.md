# Spending query

One period a caller asked about, recorded against the message that asked.

## Invariants

| Field                                            | Bound            |
|--------------------------------------------------|------------------|
| `id`                                             | only once stored |
| `userId`                                         | `> 0`            |
| [`period`](spending-period.md)                   | mandatory        |
| [`incomingMessageId`](incoming-message-id.md)    | mandatory        |
| `createdAt`                                      | mandatory        |

Two stored queries with the same `id` are the same query. An unstored one equals only itself.

## Lifecycle

| Event   | By                                                                        | Notes                                                       |
|---------|---------------------------------------------------------------------------|-------------------------------------------------------------|
| Created | [Summarize spending over a period](../usecases/summarize-spending.md)     | one per period a caller asked about                         |
| Changed | never                                                                     |                                                             |
| Removed | [Act on a user's message](../usecases/handle-incoming-message.md)         | only once the report reached the user                       |
| Removed | [Act on a text filed from the web](../usecases/handle-written-message.md) | once the turn has read them back, whatever the text came to |

```plantuml
@startuml
[*] --> Recorded : Summarize spending over a period
Recorded --> [*] : the report reached the user
Recorded --> [*] : a filed text's turn discarded it
Recorded --> Stranded : the report never arrived, or the discard failed
state Stranded #line.dashed : kept until the store is cleaned by hand
@enduml
```

## Made of / held by

The owning user's id, a [spending period](spending-period.md), the id of the message that asked, and the instant
it was recorded.

- [User](user.md) — whose spending the question is about, and the only spending it can reach.
- [Incoming message id](incoming-message-id.md) — which message asked, and what the report answering that message
  reads back.
- [Database](../contracts/out/database.md) — where one is kept, and for how long.
