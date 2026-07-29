# Intent

One action a user's message asks for. A message can ask for several.

## Invariants

- The set is closed: an intent is a [category intent](category-intent.md), an
  [expense intent](expense-intent.md), or an [unknown intent](unknown-intent.md).

## Made of / held by

Nothing of its own; each kind carries its own payload.

- [AI Connector Service — intent extraction](../contracts/out/ai-connector.md) — answers with a list of them, in
  the order the user asked.
