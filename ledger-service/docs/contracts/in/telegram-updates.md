# Telegram — incoming messages (Bot API, long polling)

Messages a user sends to the bot, and taps on the buttons the bot puts under a report, cross this boundary into
the service. The service reaches the boundary itself: it asks Telegram for whatever has arrived and waits, rather
than being called when something happens.

- **Counterpart:** Telegram, the messaging platform hosting the bot
- **Transport:** Telegram Bot API over HTTP, long polling
- **Schema:** none — Telegram owns the Bot API and its payloads

## Operations

| Operation                   | Purpose                                                                                              | Used by                                                                      |
|-----------------------------|------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------|
| Collect waiting messages    | delivers the messages users have sent since the last collection, and acknowledges the previous batch | [Act on a user's message](../../usecases/handle-incoming-message.md)         |
| Collect waiting button taps | delivers the taps on a report's buttons since the last collection                                    | [Resolve a reported proposal](../../usecases/resolve-a-reported-proposal.md) |

## What Is Read

| From      | Value                             | Used for                                                                                                                |
|-----------|-----------------------------------|-------------------------------------------------------------------------------------------------------------------------|
| A message | who sent it                       | the user the ledger is stored against                                                                                   |
| A message | which conversation it is in       | where the answer goes                                                                                                   |
| A message | which message it is               | what the answer is threaded onto ([outgoing replies](../out/telegram-replies.md))                                       |
| A message | its text                          | what the turn acts on                                                                                                   |
| A tap     | who tapped                        | the person whose spending is resolved                                                                                   |
| A tap     | which conversation, which message | where the report is, so its buttons can be cleared                                                                      |
| A tap     | which tap it is                   | what gets answered, so the button stops spinning                                                                        |
| A tap     | what the button carries           | the payload this bot wrote: which button, and which [message](../../domain/incoming-message-id.md) the report was about |

Each value is carried onward as opaque text.

## Failures

| Condition                                                       | Signal                                                                                            |
|-----------------------------------------------------------------|---------------------------------------------------------------------------------------------------|
| Telegram rejects a collection or is unreachable                 | the failure is logged, the collection is retried, and nothing is acknowledged — nothing is lost |
| The service stops before a batch is acknowledged                | the whole batch is delivered again on the next collection                                         |
| A message or a tap cannot be handled                            | the failure is logged and its batch is acknowledged with the rest                                 |
| A message carries no text, or names no sender                   | none — the message is discarded silently                                                        |
| A tap names no sender or message, or carries an unknown payload | none — the tap is discarded silently and never answered                                         |
| The bot credential is missing while collection is switched on   | the service refuses to start                                                                      |

## Compatibility

Telegram owns both sides of the payload, so a change here arrives unannounced. The service reads only who sent a
message, the conversation it belongs to, which message it is, and its text — and, on a tap, who tapped, where the
report is, which tap it is and what the button carries. Anything else Telegram adds, removes, or reshapes passes
unnoticed.

Users stored before the sender became the identity keep working only where Telegram numbers a private chat and
its one participant alike. A user first seen through a group message is stored under that group, and their
spending is reachable under nothing else — unreachable today, since no group message is collected at all.

Handling more than text — voice notes, photos — means asking Telegram for nothing new, only stopping the
discard. Handling a further kind of activity means widening what is requested: a kind that is not asked for is
dropped before the service sees it.

The payload the buttons carry is written and read by this service alone, so its shape can change with both sides
at once. A report already in the chat keeps the buttons it was sent with, and a payload written in an older shape
stops being recognised the moment the shape changes.
