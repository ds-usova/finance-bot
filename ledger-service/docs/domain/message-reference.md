# Message reference

What one handled message is known by inside the service, so the spending that message produced can be found
again and reported back to whoever sent it.

## Invariants

- A reference is present.
- A reference is a UUID, and a value that is not one is refused.
- A reference is minted fresh for each handled message, and identifies that message alone.
- While a turn is being acted on, a reference is read back only from a credential this service signed, never from
  a value the acting agent supplied
  ([ADR 0010](../adr/0010-a-message-reference-rides-the-caller-token-not-the-extraction-request.md)).
- Once the turn is reported, a reference leaves the service in the buttons under that report and comes back on
  the tap. It is not a credential and grants nothing: the tapper is who Telegram says sent the tap, and every
  lookup by a reference is scoped to that person's own rows.

## Made of / held by

A single unique value.

- [Expense proposal](expense-proposal.md) — every stored proposal records the message it came from.
- [Act on a user's message](../usecases/handle-incoming-message.md) — mints one per message, and reads back
  what was recorded under it.
- [AI Connector Service — intent extraction](../contracts/out/ai-connector.md) — carries it on the credential
  minted for the turn.
- [MCP — the create expense proposal tool](../contracts/in/mcp.md) — where it is read back out of that
  credential.
- [Telegram — outgoing replies](../contracts/out/telegram-replies.md) — carried in each button under a report.
- [Telegram — incoming messages](../contracts/in/telegram-updates.md) — read back off the button a user taps.
- [Resolve a reported proposal](../usecases/resolve-a-reported-proposal.md) — finds what that message proposed,
  and what it had confirmed.
- [Database](../contracts/out/database.md) — kept on every expense confirmed from a report, so it stays tied to
  the message that produced it.
