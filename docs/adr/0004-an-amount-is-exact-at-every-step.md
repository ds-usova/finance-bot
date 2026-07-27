# ADR 0004: An amount is exact at every step

- **Status:** Accepted
- **Date:** 2026-07-27
- **Source:** [Initialize the AI Connector Service](../implemented/2-plan-init-ai-connector-service.md)

## Context

Money reaches this service as words and leaves it as a number a ledger stores. In between it passes through a
JSON schema the model answers against, a record holding that answer, a value object, and a protobuf message.
Binary floating point cannot hold most decimal amounts exactly, and a single such step anywhere in that chain
discards precision no later conversion recovers — so the choice is not per-layer, it is one rule that either
holds all the way through or does not hold at all.

The step that makes this a decision rather than a default is the model's answer. A structured-output schema is
derived from the record it targets, so a numeric field there tells the model to reply with a JSON number, and
the amount is through a floating-point parse before any code of ours sees it.

## Decision

No layer represents an amount as a binary floating-point number. The model is asked for the amount as text and
the record it fills holds text, so the derived schema never invites a number. The amount is parsed exactly from
that text, and an amount that is not a decimal number is refused.

The exponent comes from the currency, so an amount carrying more decimal places than its currency has is
refused rather than rounded — a value the user did not say is not a value to invent.

Across the wire an amount is whole minor units plus an ISO 4217 code, as described in
[the intent extraction contract](../../ai-connector-service/docs/contracts/in/intent-extraction.md#semantics).

## Consequences

Every amount a caller receives is the amount the user said, and currencies that do not scale by a hundred are
correct without a special case.

An answer the model gives in a shape this rule refuses becomes an unknown entry rather than a rounded one, so
strictness here is visible to the caller as unknowns rather than as quiet drift.

What must stay true: the amount stays text from the model to the point it is parsed, and no field, parameter,
or local anywhere in the chain takes a floating-point type — including a record added later to carry a new
kind of answer.
