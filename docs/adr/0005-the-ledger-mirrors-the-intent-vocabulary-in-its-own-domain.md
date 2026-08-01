# ADR 0005: The ledger mirrors the intent vocabulary in its own domain

- **Status:** Accepted
- **Date:** 2026-07-28
- **Source:** [Call the AI Connector from the Ledger Service](../implemented/4-plan-ledger-ai-connector-integration.md)

## Context

Both services speak the same intent vocabulary — operations, category and expense intents, money — and both
generate Java from the same [schema](../../proto/intent_extraction.proto). Sharing those generated types, or
extracting the vocabulary into a module both depend on, would have removed an obvious duplication.

## Decision

Each service defines the vocabulary as its own value objects. Generated types stay inside the adapter package
that speaks the wire, and a mapper there translates in both directions. The duplication is deliberate.

## Consequences

- The two definitions may diverge, and only the mapper and its tests will notice. That is the price.
- Either service can change what it holds — a currency it accepts, a rule it enforces — without a release
  train through the other.
- The schema stays the only shared artifact, so a shared module never becomes a place for behaviour to hide.
- Five simple names collide between the generated and domain types; the mapper imports the domain ones and
  qualifies the generated ones in full.
- 2026-07-31: since [plan 9](../implemented/9-plan-handle-incoming-message-extracts-intents.md) no intent
  crosses into the ledger, so only the connector holds a mirror; this decision no longer applies to the ledger,
  which held the boundary this title names as it stood in July 2026.
