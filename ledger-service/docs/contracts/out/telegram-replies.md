# Telegram — outgoing replies (Bot API)

What the bot says back crosses this boundary. Every message the service handles is answered here, with a report
of what that message produced — the totals it asked for, and the spending it named; so is every tap on that
report's buttons.

- **Counterpart:** Telegram, the messaging platform hosting the bot
- **Transport:** Telegram Bot API over HTTP
- **Schema:** none — Telegram owns the Bot API and its payloads

## Operations

| Operation                | Purpose                                                            | Used by                                                                                                                                              |
|--------------------------|--------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------|
| Send a report            | puts the answer to one handled message in front of a user          | [Act on a user's message](../../usecases/handle-incoming-message.md)                                                                                 |
| Answer a tap             | tells the tapper what their tap did, and stops the button spinning | [Resolve a reported proposal](../../usecases/resolve-a-reported-proposal.md)                                                                         |
| Clear a report's buttons | takes both buttons off a report, so it cannot be resolved again    | [Resolve a reported proposal](../../usecases/resolve-a-reported-proposal.md) · [Clear the emptied reports](../../usecases/clear-emptied-reports.md) |

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

**Answered on a send that carried buttons:** the conversation Telegram put the report in, and the id Telegram
gave it. That pair is [recorded](../../domain/proposal-report.md), and it is the only way back to a report once
its proposals are gone. A send carrying no buttons answers nothing, because there is nothing to reach later.

**Sent when a tap is resolved:** the tap to answer · what happened, in one line · then the conversation and the
report message whose buttons come off.

**Sent when an accepted message is emptied:** the conversation and the report message alone. Nothing is
answered, no text is sent with it, and the report's own text is left exactly as it was. It goes only for a
message with nothing pending left under it.

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

- A period's two days are written as a day, an English month and a year. The locale the service runs under does
  not change that.
- No formatting markup is claimed for the text. A description or a merchant name is shown exactly as stored, and
  nothing in it is treated as an instruction to the renderer.
- Nothing is retried and nothing is sent twice. A report that fails is a report the user never sees.

### What a user reads

A message that only asked a question:

```
Between 27 Jul 2026 and 2 Aug 2026 you spent:
• 120.50 EUR (4 expenses)
• 7200.00 HUF (1 expense)
```

A period the ledger holds nothing in, and two periods in one message:

```
Between 27 Jul 2026 and 2 Aug 2026 you spent:
• 42.30 EUR (1 expense)

Nothing is recorded between 3 Aug 2026 and 9 Aug 2026.
```

A message that named spending. The two buttons ride this one:

```
Noted 2 expenses, pending your confirmation:
• Groceries (Food) — weekly shop, Rewe: 42.30 EUR
• Auto (Fuel) — tank refill: 60.00 EUR
```

A message that did both — the totals come first:

```
Between 27 Jul 2026 and 2 Aug 2026 you spent:
• 42.30 EUR (1 expense)

Noted 2 expenses, pending your confirmation:
• Groceries (Food) — weekly shop, Rewe: 42.30 EUR
• Auto (Fuel) — tank refill: 60.00 EUR
```

The rest, one line each:

| Outcome                                    | Text                                                                                   |
|--------------------------------------------|----------------------------------------------------------------------------------------|
| The message named and asked nothing        | `No expense was identified in that message.`                                           |
| The turn failed, having recorded nothing   | `Something went wrong and nothing was noted — please try again.`                     |
| The turn failed, having recorded something | `Something went wrong, so this may be incomplete. What I could read:` then the bullets |

A trimmed report closes with the count it left out — `… and 3 more.` for proposals, `… and 3 more periods.`
when the totals alone fill the limit.

### What a tap answers with

| Outcome                         | Text                                |
|---------------------------------|-------------------------------------|
| Confirmed                       | `Confirmed 2 expenses.`             |
| Deleted                         | `Deleted 2 expenses.`               |
| Tapped again, already confirmed | `Already confirmed: 2 expenses.`    |
| Nothing left to resolve         | `There is nothing left to resolve.` |

A count of one drops the plural: `Confirmed 1 expense.`

One report is always one message. Its length limit is spent in this order:

| Filled first | Then                                                       | When it still does not fit                              |
|--------------|------------------------------------------------------------|---------------------------------------------------------|
| The totals   | the proposal list, trimmed, with what was left out counted | whole periods drop from the oldest, and are counted too |

## Failures

| Condition                                            | Signal                                                                         |
|------------------------------------------------------|--------------------------------------------------------------------------------|
| Telegram refuses the call                            | delivery fails, carrying the error code and description Telegram answered with |
| Telegram is unreachable, or the call errors          | delivery fails, carrying what went wrong                                       |
| The report, or what to say about a tap, is absent    | rejected as invalid; nothing is sent                                           |
| The tap is older than the window Telegram answers in | answering fails; the buttons are still cleared                                 |
| The buttons the tap asks to clear are already gone   | clearing fails after the tapper has been answered                              |
| The buttons an emptied report asks to clear are gone | clearing fails, and the failure stays on the clearing's own thread             |

A delivery failure rolls nothing back: the spending the report was going to name stays recorded, a resolution
whose answer was lost stays resolved, and spending accepted from the page stays accepted whether or not its
report loses its buttons.

## Compatibility

Telegram owns both sides of the payload, so a change here arrives unannounced. The service sends only a
conversation, a reply target, plain text, a pair of buttons and the two calls that resolve a tap; anything else
Telegram adds or reshapes passes unnoticed.

Claiming a formatting mode later makes every stored description and merchant name an escaping problem, since
both are written by the user and rendered verbatim today.

The bot the report is sent as, and the address the Bot API is reached at, are
[configuration](../../configuration.md) — the same ones the incoming side uses
([ADR 0001](../../adr/0001-telegram-updates-arrive-by-long-polling.md)).
