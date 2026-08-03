# Message reference

What one handled message is known by inside the service, so the spending that message produced can be found
again and reported back to whoever sent it.

## Invariants

- A reference is present.
- A reference is a UUID, and a value that is not one is refused.
- A reference is minted fresh for each handled message, and identifies that message alone.
- A reference is read back only from a credential this service signed, never from a value a caller supplied
  ([ADR 0010](../adr/0010-a-message-reference-rides-the-caller-token-not-the-extraction-request.md)).

## Made of / held by

A single unique value.

- [Expense proposal](expense-proposal.md) — every stored proposal records the message it came from.
- [Act on a user's message](../usecases/handle-incoming-message.md) — mints one per message, and reads back
  what was recorded under it.
- [AI Connector Service — intent extraction](../contracts/out/ai-connector.md) — carries it on the credential
  minted for the turn.
- [MCP — the create expense proposal tool](../contracts/in/mcp.md) — where it is read back out of that
  credential.
