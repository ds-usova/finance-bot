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

## Semantics

Two kinds of activity are requested: messages users send, and taps on the buttons the bot puts under a report.
Both arrive in one collection. Every other kind Telegram could report — edits, reactions, membership changes —
is never delivered.

A collection returns what is queued for the bot, oldest first, at most a configured number of items, waiting a
configured time for the first one to arrive. Nothing waiting means an empty answer, not a failure.

A message carrying no text — a voice note, a photo, a sticker — is accepted at the boundary and discarded. It is
not an error and produces no reply. A message naming no sender is discarded the same way; a channel post is the
usual one.

Every batch is acknowledged in full, including items the service discarded and items it failed to act on. A
single one can therefore never stall delivery of the ones behind it. Acknowledgement is final: Telegram does not
deliver an acknowledged item again.

Three of the values Telegram puts on a message are read, each carried onward as opaque text and never
interpreted:

- **who sent it** — the person the service stores the ledger against, so the same person is one user wherever
  they write from;
- **which conversation it is in** — where the answer to it goes, and nothing else;
- **which message it is** — what that answer is threaded onto ([outgoing replies](../out/telegram-replies.md)).

In a private chat Telegram numbers the sender and the conversation alike; in a group it does not, and only the
sender says whose spending it is.

### A button tap

Four of the values Telegram puts on a tap are read:

- **who tapped** — the person whose spending is resolved, and the only thing that says so;
- **which conversation, and which message** — where the report is, so its buttons can be cleared
  ([outgoing replies](../out/telegram-replies.md));
- **which tap it is** — what gets answered, so the button stops spinning on the tapper's client;
- **what the tapped button carries** — the payload the bot itself wrote into it
  ([outgoing replies](../out/telegram-replies.md)), naming which of the two buttons it is and which
  [message](../../domain/incoming-message-id.md) the report was about.

The payload never says who tapped. A tapper can send any payload they like and resolve nothing by it, because
everything the tap reaches is scoped to the tapper's own rows.

A tap naming no sender, carrying no message to clear the buttons on, or carrying a payload this bot never wrote,
is discarded silently — and, unlike a discarded message, is left spinning on the tapper's client until Telegram
times it out.

Telegram accepts an answer to a tap only for a limited window after it arrives. A tap answered later than that is
refused, which is a failure to report a resolution rather than a failure to make one.

**The bot does not work in a group chat.** Telegram withholds an ordinary group message from a bot unless the bot
is addressed, so a message written in a group is never collected and never answered — and no report is ever
posted into a group for a second person to tap.

- **Reaches the service:** a private chat with the bot.
- **Does not:** a group message that neither names the bot nor replies to one of its own.
- **Consequence:** the sender-and-conversation distinction above shapes what is *stored*, not what arrives today;
  and every tap that arrives is the tap of the person the report belongs to.
- **What would change it:** turning the bot's privacy mode off with its owner, which is a BotFather setting
  rather than anything in this service.

## Failures

| Condition                                                         | Signal                                                                                          |
|-------------------------------------------------------------------|-------------------------------------------------------------------------------------------------|
| Telegram rejects a collection or is unreachable                   | the failure is logged, the collection is retried, and nothing is acknowledged — nothing is lost |
| A message or a tap cannot be handled                              | the failure is logged and its batch is acknowledged with the rest                               |
| A message carries no text, or names no sender                     | none — the message is discarded silently                                                        |
| A tap names no sender or message, or carries an unknown payload   | none — the tap is discarded silently and never answered                                         |
| The bot credential is missing while collection is switched on     | the service refuses to start                                                                    |

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
