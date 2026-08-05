# Telegram — outgoing replies (Bot API)

What the bot says back crosses this boundary. Every message the service handles is answered here, with a report
of what that message produced — the totals it asked for, and the spending it named; so is every tap on that
report's buttons.

- **Counterpart:** Telegram, the messaging platform hosting the bot
- **Transport:** Telegram Bot API over HTTP
- **Schema:** none — Telegram owns the Bot API and its payloads

## Operations

| Operation                | Purpose                                                            | Used by                                                                      |
|--------------------------|--------------------------------------------------------------------|------------------------------------------------------------------------------|
| Send a report            | puts the answer to one handled message in front of a user          | [Act on a user's message](../../usecases/handle-incoming-message.md)         |
| Answer a tap             | tells the tapper what their tap did, and stops the button spinning | [Resolve a reported proposal](../../usecases/resolve-a-reported-proposal.md) |
| Clear a report's buttons | takes both buttons off a report, so it cannot be resolved again    | [Resolve a reported proposal](../../usecases/resolve-a-reported-proposal.md) |

## Semantics

**Sent with a report:** the conversation to say it in · the message it answers · the text of the report — its
totals, then whatever it proposes · the two buttons, when it lists anything to resolve.

A report is addressed to the conversation the message came from, not to the person who sent it — in a group, the
answer is read by everyone in it. It is threaded onto the message it answers, so a group reader can tell which
message it belongs to.

Each button carries a payload naming which button it is and which message the report was about. The service
writes that payload and is the only reader of it
([incoming messages](../in/telegram-updates.md)). A button stays on the message until something clears it, so a
report can be tapped as long as it is in the chat.

**Sent when a tap is resolved:** the tap to answer · what happened, in one line · then the conversation and the
report message whose buttons come off.

The two calls go in that order: the tapper's button spins until the tap is answered, and by then the resolution
has already happened, so the answer is true whether or not the buttons come off. The buttons are cleared for
every outcome, and the clearing is attempted even when answering the tap failed — a resolution nobody could be
told about still loses its buttons. When both fail, the failure to answer the tap is the one reported.

Clearing buttons that are already gone is refused rather than ignored, so a repeat tap on a report that was fully
resolved reports a delivery failure after the tapper has been answered.

The report's own text is never edited. A resolved report keeps reading exactly as it was sent.

A message that no longer exists is not a lost report: a report whose target has been deleted is sent unthreaded
rather than refused.

The service turns [the report](../../usecases/handle-incoming-message.md#the-report) and
[what a tap did](../../usecases/resolve-a-reported-proposal.md#outcomes) into chat text here, at the boundary
whose limits shape it. What the core hands over is an outcome, the periods it totalled and the proposals it
recorded; never a rendered string, and never an amount already written out. An answer to a tap is one short line, well inside the far tighter length limit Telegram allows
it.

A report's text is written in two parts, in this order:

- **The totals**, one block per period the message asked about — a header naming the two days, then one line per
  currency with the amount and how many expenses are behind it. A period the ledger holds nothing in gets a
  single line saying so.
- **What the turn proposed**, under whichever of the report's own texts the outcome earned.

A period's two days are written as a day, an English month and a year, whatever locale the service runs under.

No formatting markup is claimed for the text, so a description or a merchant name is shown exactly as it was
stored and nothing in it is treated as an instruction to the renderer.

One report is always one message, and its length limit is spent on the totals first. What is cut is the proposal
list, and what was left out is counted in a closing line. When the totals alone fill the limit, whole periods are
dropped from the oldest and counted in a closing line of their own.

Nothing is retried, and nothing is sent twice: a report that fails is a report the user never sees.

## Failures

| Condition                                             | Signal                                                                         |
|-------------------------------------------------------|--------------------------------------------------------------------------------|
| Telegram refuses the call                             | delivery fails, carrying the error code and description Telegram answered with |
| Telegram is unreachable, or the call errors           | delivery fails, carrying what went wrong                                       |
| The report, or what to say about a tap, is absent     | rejected as invalid; nothing is sent                                           |
| The tap is older than the window Telegram answers in  | answering fails; the buttons are still cleared                                 |
| The buttons the tap asks to clear are already gone    | clearing fails after the tapper has been answered                              |

A delivery failure rolls nothing back: the spending the report was going to name stays recorded, and a resolution
whose answer was lost stays resolved.

## Compatibility

Telegram owns both sides of the payload, so a change here arrives unannounced. The service sends only a
conversation, a reply target, plain text, a pair of buttons and the two calls that resolve a tap; anything else
Telegram adds or reshapes passes unnoticed.

Claiming a formatting mode later makes every stored description and merchant name an escaping problem, since
both are written by the user and rendered verbatim today.

The bot the report is sent as, and the address the Bot API is reached at, are
[configuration](../../configuration.md) — the same ones the incoming side uses
([ADR 0001](../../adr/0001-telegram-updates-arrive-by-long-polling.md)).
