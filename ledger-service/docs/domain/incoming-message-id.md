# Incoming message id

What one handled message is known by inside the service, so the spending that message produced can be found
again and reported back to whoever sent it.

## Invariants

- Length — at most `56` bytes in UTF-8, present and non-blank. The bound is bytes rather than characters,
  because it exists to keep a button's payload inside the 64 Telegram allows.
- A value is derived from the message that started the turn. Nothing mints one.
- From a Telegram message — `<conversation>:<message>`.
- From a [written message](written-message.md) — `web:<id>`, `id` `>= 1`.
- A value stored before this was derived is opaque text and is read back as given, so a report already in a chat
  still names its own rows.

## Made of / held by

A single unique value.

- [Expense](expense.md) — every pending one records the message it came from, and keeps it once recorded.
- [Spending query](spending-query.md) — every period asked about records the message that asked.
- [Proposal report](proposal-report.md) — every recorded report names the message it is about.
- [Act on a user's message](../usecases/handle-incoming-message.md) — derives one per message, and reads back
  what was recorded under it.
- [Written message](written-message.md) — the text a `web:` id is derived from.
- [Act on a text filed from the web](../usecases/handle-written-message.md) — derives one per text, and reads
  back what was recorded under it.
- [Read what became of a filed text](../usecases/read-written-message.md) — reads the days of the entries under
  one.
- [Settle the texts left reading](../usecases/sweep-written-messages.md) — reads whether anything was recorded
  under one.
- [AI Connector Service — intent extraction](../contracts/out/ai-connector.md) — carries it on the credential
  minted for the turn.
- [MCP — the ledger's tools](../contracts/in/mcp.md) — where it is read back out of that credential, by the
  tools that record a proposal and a period.
- [Telegram — outgoing replies](../contracts/out/telegram-replies.md) — carried in each button under a report.
- [Telegram — incoming messages](../contracts/in/telegram-updates.md) — read back off the button a user taps.
- [Resolve a reported proposal](../usecases/resolve-a-reported-proposal.md) — finds what that message proposed,
  and what it had confirmed.
- [Clear the emptied reports](../usecases/clear-emptied-reports.md) — names the messages an acceptance or a
  discard may have emptied, and the reports to reach.
- [Database](../contracts/out/database.md) — kept on every expense confirmed from a report, so it stays tied to
  the message that produced it.
