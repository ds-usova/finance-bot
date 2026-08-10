# Incoming message id

What one handled message is known by inside the service, so the spending that message produced can be found
again and reported back to whoever sent it.

## Invariants

- Length — at most `56` bytes in UTF-8, present and non-blank. The bound is bytes rather than characters,
  because it exists to keep a button's payload inside the 64 Telegram allows.
- A value is derived from the conversation a message arrived in and the message that started it, joined by a
  colon. Nothing mints one.
- A value stored before this was derived is opaque text and is read back as given, so a report already in a chat
  still names its own rows.
- While a turn is being acted on, an id is read back only from a credential this service signed, never from a
  value the acting agent supplied
  ([ADR 0015](../adr/0015-a-turn-is-named-by-the-message-that-started-it-not-by-a-value-minted-beside-it.md)).
- Once the turn is reported, an id leaves the service in the buttons under that report and comes back on the
  tap. It is not a credential and grants nothing: the tapper is who Telegram says sent the tap, and every lookup
  by an id is scoped to that person's own rows.

## Made of / held by

A single unique value.

- [Expense proposal](expense-proposal.md) — every stored proposal records the message it came from.
- [Spending query](spending-query.md) — every period asked about records the message that asked.
- [Proposal report](proposal-report.md) — every recorded report names the message it is about.
- [Act on a user's message](../usecases/handle-incoming-message.md) — derives one per message, and reads back
  what was recorded under it.
- [AI Connector Service — intent extraction](../contracts/out/ai-connector.md) — carries it on the credential
  minted for the turn.
- [MCP — the ledger's tools](../contracts/in/mcp.md) — where it is read back out of that credential, by the
  tools that record a proposal and a period.
- [Telegram — outgoing replies](../contracts/out/telegram-replies.md) — carried in each button under a report.
- [Telegram — incoming messages](../contracts/in/telegram-updates.md) — read back off the button a user taps.
- [Resolve a reported proposal](../usecases/resolve-a-reported-proposal.md) — finds what that message proposed,
  and what it had confirmed.
- [Clear the emptied reports](../usecases/clear-emptied-reports.md) — names the messages an acceptance may have
  emptied, and the reports to reach.
- [Database](../contracts/out/database.md) — kept on every expense confirmed from a report, so it stays tied to
  the message that produced it.
