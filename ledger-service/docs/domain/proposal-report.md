# Proposal report

Where one report the bot posted can be found again — the conversation it was posted into and the message
Telegram gave it — so its buttons can be reached after the proposals it listed are gone.

## Invariants

| Field                                            | Bound                          |
|--------------------------------------------------|--------------------------------|
| `id`                                             | only once stored               |
| `userId`                                         | `> 0`                          |
| [`incomingMessageId`](incoming-message-id.md)    | mandatory                      |
| `conversationId`                                 | mandatory, non-blank           |
| `sentMessageId`                                  | mandatory, non-blank           |

- Two stored reports with the same `id` are the same report. An unstored one equals only itself.
- `incomingMessageId` is what the person sent, `sentMessageId` is what the bot sent back.

## Lifecycle

| Event   | By                                                                | Notes                                          |
|---------|-------------------------------------------------------------------|------------------------------------------------|
| Created | [Act on a user's message](../usecases/handle-incoming-message.md) | one per report delivered carrying buttons      |
| Changed | never                                                             | every field is fixed at creation               |
| Removed | never                                                             | only with its user, by the store's own cascade |

## Made of / held by

The owning user's id, the incoming message the report is about, the conversation it was posted into, the
report's own message id, and the instants it was recorded and last updated.

- [User](user.md) — who the report was sent to.
- [Incoming message id](incoming-message-id.md) — which message the report is about.
- [Act on a user's message](../usecases/handle-incoming-message.md) — records one once a report is delivered.
- [Clear the emptied reports](../usecases/clear-emptied-reports.md) — reads them back to reach the buttons.
- [Database](../contracts/out/database.md) — where one is kept.
- [Telegram — outgoing replies](../contracts/out/telegram-replies.md) — what the delivery answers with, and what
  the clearing addresses.
