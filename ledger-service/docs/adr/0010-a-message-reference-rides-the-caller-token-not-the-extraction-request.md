# ADR 0010: A message reference rides the caller token, not the extraction request

- **Status:** Accepted
- **Date:** 2026-08-03
- **Source:** [Identify the User by Telegram User Id and Report the Proposals Back](../../../docs/implemented/11-report-expense-proposals-to-the-user/plan.md)

## Context

The model records each expense by calling the ledger's own `create_expense_proposal` MCP tool, once per expense,
each call its own committed transaction ([ADR 0008](../../../docs/adr/0008-the-connector-hands-expense-recording-to-the-model.md)).
`ExtractIntentsResponse` is empty, so when the gRPC call returns the ledger knows only that the turn ended — not
which rows that turn produced.

Reporting those rows back to whoever sent the message requires correlating them with the message. Two things can
carry that correlation from the ledger, through the connector, into a tool call: a new field on
`ExtractIntentsRequest`, or a claim on the caller token the ledger already mints per extraction.

## Decision

The ledger mints a `MessageReference` per handled message and puts it on the caller token as the `mrf` claim.
`AuthenticatedCallerUtils` reads it back inside the tool call and stores it on the proposal row; the read-back
matches on it.

`intent_extraction.proto` gains no field, and the tool's input schema gains no argument.

## Consequences

- The connector needs no change and no release. It forwards the token verbatim as the `Authorization` header and
  neither reads nor rewrites its claims.
- The reference cannot be set by the model, for the same reason the caller's identity cannot
  ([ADR 0007](0007-an-mcp-caller-is-identified-by-a-signed-token-not-a-tool-argument.md)): it is read server-side
  from a token the ledger signed, not from an argument a prompt injection could dictate.
- The two services stay decoupled on this: correlation data is added by extending a token the ledger owns
  end-to-end, so no cross-service contract is versioned for it.
- The claim is bounded by the token's lifetime, so it correlates one extraction and nothing longer. A correlation
  that must outlive a single call needs a different carrier.
- Anything else the ledger later wants to hand its own tools follows the same route, which is a mint-side change
  rather than a schema negotiation.
- The reference is minted rather than derived from the Telegram message id, so a redelivered update produces a
  second reference and a second report. Deriving it from the message id is what would make the retry idempotent.
