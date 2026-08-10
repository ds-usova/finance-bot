# Proposal report

Where one report the bot posted can be found again — the conversation it was posted into and the message
Telegram gave it — so its buttons can be reached after the proposals it listed are gone.

## Invariants

- A report is stored or not yet stored, and carries the store's own id only once it is.
- Two stored reports with the same id are the same report, whatever else differs; a report not yet stored equals
  only itself.
- The owning user's id — positive.
- The [incoming message id](incoming-message-id.md) the report is about — present.
- The conversation the report was posted into — present, non-blank.
- The report's own message id, as Telegram gave it on send — present, non-blank.

Two of those name a message and they are not the same message: one is what a person sent, the other is what the
bot sent back about it.

There is no unique key. One incoming message can carry several reports, and each has a row.

## Lifecycle

| Event   | By                                                                | Notes                                          |
|---------|-------------------------------------------------------------------|------------------------------------------------|
| Created | [Act on a user's message](../usecases/handle-incoming-message.md) | one per report delivered carrying buttons      |
| Changed | never                                                             | every field is fixed at creation               |
| Removed | never                                                             | only with its user, by the store's own cascade |

One state, and it outlives the proposals it is about — which is what lets an emptied report still be reached by
[the clearing](../usecases/clear-emptied-reports.md).

A report exists in the chat without a row in two cases: one delivered before the row was ever kept, and one
whose location could not be stored. Both keep their buttons, and a tap on either still answers truthfully.

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
