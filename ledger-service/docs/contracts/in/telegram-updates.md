# Telegram — incoming messages (Bot API, long polling)

Messages a user sends to the bot cross this boundary into the service. The service reaches the boundary itself:
it asks Telegram for whatever has arrived and waits, rather than being called when something happens.

- **Counterpart:** Telegram, the messaging platform hosting the bot
- **Transport:** Telegram Bot API over HTTP, long polling
- **Schema:** none — Telegram owns the Bot API and its payloads

## Operations

| Operation                | Purpose                                                                                              | Used by                                                              |
|--------------------------|------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------|
| Collect waiting messages | delivers the messages users have sent since the last collection, and acknowledges the previous batch | [Act on a user's message](../../usecases/handle-incoming-message.md) |

## Semantics

Only messages are requested. Every other kind of activity Telegram could report — edits, reactions, button
presses, membership changes — is never delivered.

A collection returns the messages queued for the bot, oldest first, at most a configured number of them, waiting
a configured time for the first one to arrive. Nothing waiting means an empty answer, not a failure.

A message carrying no text — a voice note, a photo, a sticker — is accepted at the boundary and discarded. It is
not an error and produces no reply. A message naming no sender is discarded the same way; a channel post is the
usual one.

Every batch is acknowledged in full, including messages the service discarded and messages it failed to record.
A single message can therefore never stall delivery of the ones behind it. Acknowledgement is final: Telegram
does not deliver an acknowledged message again.

Three of the values Telegram puts on a message are read, each carried onward as opaque text and never
interpreted:

- **who sent it** — the person the service stores the ledger against, so the same person is one user wherever
  they write from;
- **which conversation it is in** — where the answer to it goes, and nothing else;
- **which message it is** — what that answer is threaded onto ([outgoing replies](../out/telegram-replies.md)).

In a private chat Telegram numbers the sender and the conversation alike; in a group it does not, and only the
sender says whose spending it is.

## Failures

| Condition                                                     | Signal                                                                                             |
|---------------------------------------------------------------|----------------------------------------------------------------------------------------------------|
| Telegram rejects a collection or is unreachable               | the failure is logged, the collection is retried, and nothing is acknowledged — no message is lost |
| A message cannot be handled                                   | the failure is logged and its batch is acknowledged with the rest                                  |
| A message carries no text, or names no sender                 | none — the message is discarded silently                                                           |
| The bot credential is missing while collection is switched on | the service refuses to start                                                                       |

## Compatibility

Telegram owns both sides of the payload, so a change here arrives unannounced. The service reads only who sent a
message, the conversation it belongs to, which message it is, and its text; anything else Telegram adds,
removes, or reshapes passes unnoticed.

Users stored before the sender became the identity keep working only where Telegram numbers a private chat and
its one participant alike. A user first seen through a group message is stored under that group, and their
spending is reachable under nothing else.

Handling more than text — voice notes, photos — means asking Telegram for nothing new, only stopping the
discard. Handling anything that is not a message means widening what is requested.
