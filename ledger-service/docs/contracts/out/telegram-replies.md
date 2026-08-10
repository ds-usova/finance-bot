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

## What Each Call Carries

| Call                     | Sent with it                                                                            | Answered with                                                            |
|--------------------------|-------------------------------------------------------------------------------------------|--------------------------------------------------------------------------|
| Send a report            | the conversation · the message it answers · the report's text · the two buttons, when it lists anything to resolve | the conversation and message id Telegram gave it, when it carried buttons |
| Answer a tap             | the tap · what happened, in one line                                                    | nothing                                                                  |
| Clear a report's buttons | the conversation and the report message                                                 | nothing                                                                  |

- A report is addressed to the conversation, not the sender, and threaded onto the message it answers.
- A report whose target has been deleted is sent unthreaded rather than refused.
- The pair Telegram answers with is [recorded](../../domain/proposal-report.md). It is the only way back to a
  report once its proposals are gone.
- Each button carries a payload this service writes and alone reads
  ([incoming messages](../in/telegram-updates.md)).
- A button stays until something clears it.
- A report's own text is never edited.
- A tap is answered before its buttons are cleared. The buttons are cleared for every outcome, and even when
  answering failed. When both fail, the failure to answer is the one reported.
- Clearing buttons that are already gone is refused rather than ignored.
- Nothing is retried and nothing is sent twice.
- A period's two days are written as a day, an English month and a year, under any locale.
- No formatting markup is claimed. A description or a merchant name is shown exactly as stored.

## What a user reads

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

## What a tap answers with

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
