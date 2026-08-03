# Telegram — outgoing replies (Bot API)

What the bot says back crosses this boundary. Every message the service handles is answered here, with a report
of the spending that message produced.

- **Counterpart:** Telegram, the messaging platform hosting the bot
- **Transport:** Telegram Bot API over HTTP, one call per report
- **Schema:** none — Telegram owns the Bot API and its payloads

## Operations

| Operation     | Purpose                                                   | Used by                                                              |
|---------------|-----------------------------------------------------------|----------------------------------------------------------------------|
| Send a report | puts the answer to one handled message in front of a user | [Act on a user's message](../../usecases/handle-incoming-message.md) |

## Semantics

**Sent:** the conversation to say it in · the message it answers · the text of the report.

A report is addressed to the conversation the message came from, not to the person who sent it — in a group, the
answer is read by everyone in it. It is threaded onto the message it answers, so a group reader can tell which
message it belongs to.

A message that no longer exists is not a lost report: a report whose target has been deleted is sent unthreaded
rather than refused.

The service turns [the report](../../usecases/handle-incoming-message.md#the-report) into chat text here, at the
boundary whose limits shape it. What the core hands over is an outcome and a list; never a rendered string.

No formatting markup is claimed for the text, so a description or a merchant name is shown exactly as it was
stored and nothing in it is treated as an instruction to the renderer.

One report is always one message. A list too long for the Bot API's own length limit is cut, and what was left
out is counted in a closing line.

Nothing is retried, and nothing is sent twice: a report that fails is a report the user never sees.

## Failures

| Condition                                   | Signal                                                                         |
|---------------------------------------------|--------------------------------------------------------------------------------|
| Telegram refuses the call                   | delivery fails, carrying the error code and description Telegram answered with |
| Telegram is unreachable, or the call errors | delivery fails, carrying what went wrong                                       |
| The report is absent                        | rejected as invalid; nothing is sent                                           |

A delivery failure rolls nothing back: the spending the report was going to name stays recorded.

## Compatibility

Telegram owns both sides of the payload, so a change here arrives unannounced. The service sends only a
conversation, a reply target and plain text; anything else Telegram adds or reshapes passes unnoticed.

Claiming a formatting mode later makes every stored description and merchant name an escaping problem, since
both are written by the user and rendered verbatim today.

The bot the report is sent as, and the address the Bot API is reached at, are
[configuration](../../configuration.md) — the same ones the incoming side uses
([ADR 0001](../../adr/0001-telegram-updates-arrive-by-long-polling.md)).
