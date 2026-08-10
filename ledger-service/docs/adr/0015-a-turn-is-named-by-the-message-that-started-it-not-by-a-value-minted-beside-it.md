# ADR 0015: A turn is named by the message that started it, not by a value minted beside it

- **Status:** Accepted
- **Date:** 2026-08-10
- **Source:** [Accept Expenses from the Web — ledger-service](../../../docs/21-accept-expenses-from-the-web/ledger-service/plan.md)
- **Supersedes:** [0010](0010-a-message-reference-rides-the-caller-token-not-the-extraction-request.md)

## Context

`MessageReference` named a turn with a UUID the ledger minted per extraction, carried on the caller token as the
`mrf` claim and read back inside a tool call ([ADR 0010](0010-a-message-reference-rides-the-caller-token-not-the-extraction-request.md)).
That value has no source outside the mint itself: two calls for the same message mint two different references, so
a redelivered update cannot be recognized as the message already handled.

The conversation a message arrived in and the message that started it are both already on
`HandleIncomingMessageCommand`, present before anything is minted.

## Decision

A turn's id is derived, not minted: `<conversationId>:<inboundMessageId>`, joined by a colon. The type is renamed
`IncomingMessageId`, and the claim that carries it on the caller token is renamed `imi`. It still rides the token
rather than a request argument, read back the same way ADR 0010 decided; only what fills the claim changes, from a
minted UUID to a derived value.

The id is opaque text rather than a UUID, bounded by bytes rather than by a fixed-format parse, since it must fit
the same `callback_data` budget the minted form fit.

## Consequences

- A redelivered update now derives the same id it derived the first time, so both deliveries file under one turn
  instead of two.
- Rows already stored under a minted UUID keep that value, rendered as text; nothing is backfilled, and a
  stored id is read the same way regardless of which form produced it.
- The column these ids live in widens from `UUID` to `TEXT` everywhere it is held.
- Every other part of ADR 0010's decision stands: the id is still read server-side from a token this service
  signs, never from a value the acting agent supplied, and no cross-service contract carries it.
