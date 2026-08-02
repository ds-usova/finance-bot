# ADR 0008: The connector hands expense recording to the model

- **Status:** Accepted
- **Date:** 2026-08-02
- **Source:** [The Ledger's Tools on the Chat Client](../implemented/10-plan-ledger-tools-on-the-chat-client.md)

## Context

The connector used to extract a structured answer from the model, assemble a proposal from it — category,
currency, amount — and call the ledger's `create_expense_proposal` tool itself, through a hand-rolled MCP
client. The ledger writes every refusal as guidance to retry against, naming what to send instead; nothing
read it, because the caller of the tool was the connector's own code, not the model.

## Decision

The ledger's tools hang off the chat client, and the model calls them directly: once per expense, reading a
refusal and retrying against it inside the same turn. The connector's structured-output extraction, its intent
vocabulary, and its hand-rolled MCP client are deleted.

## Consequences

- A refusal reaches the party that can act on it. The tool loop retries a corrected call inside the turn
  instead of failing it.
- The model now performs the minor-units conversion and splits a category label into the tool's two category
  arguments — work the connector's core used to do and could unit test.
- Nothing caps the tool loop. It runs for as long as the model keeps issuing tool calls.
