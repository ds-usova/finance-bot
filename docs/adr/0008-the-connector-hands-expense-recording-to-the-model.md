# ADR 0008: The connector hands expense recording to the model

- **Status:** Accepted
- **Date:** 2026-08-02
- **Source:** [The Ledger's Tools on the Chat Client](../implemented/10-ledger-tools-on-the-chat-client/plan.md)

## Context

A model's reading of a message reaches the ledger one of two ways. Structured output makes the connector own
the shape: it publishes a schema of its own, validates the answer against it, and is itself the caller of the
ledger's tool — so it keeps a mirror of a vocabulary the ledger already declares, drifting whenever either side
moves, and it receives the ledger's refusals, which are written as guidance to retry against and are useless to
a program that has already decided what to send. Tool calling inverts both: the ledger publishes the schema the
model fills, and the model is the caller, inside a turn still running when the refusal arrives.

## Decision

The ledger's tools hang off the chat client, and the model calls them directly: once per expense, reading a
refusal and retrying against it inside the same turn. The connector's structured-output extraction, its intent
vocabulary, and its hand-rolled MCP client are deleted.

## Consequences

- A refusal reaches the party that can act on it. The tool loop retries a corrected call inside the turn
  instead of failing it.
- The argument schema has one owner. The connector holds no copy of it, so a tool the ledger adds or reshapes
  reaches the model without a change here — and an argument description is now contract, since it is what the
  model fills from.
- Nothing validates a call before it is made. The connector used to reject an invented category or an unusable
  amount in its own code, with unit tests behind it; that judgement now sits with the ledger, at the far end of
  a network call.
- The model now performs the minor-units conversion and splits a category label into the tool's two category
  arguments — work the connector's core used to do and could unit test.
- Nothing caps the tool loop. It runs for as long as the model keeps issuing tool calls.
