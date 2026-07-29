# Operation

What an intent asks to be done with the thing it names.

## Invariants

- The set is closed: create, read, update, delete.
- Every intent that carries an operation carries exactly one.

## Made of / held by

One of the four actions.

- [Category intent](category-intent.md) and [expense intent](expense-intent.md) — each carries one, and what
  else they must carry follows from it.
- An action outside the set arrives as an [unknown intent](unknown-intent.md)
  ([AI Connector Service — intent extraction](../contracts/out/ai-connector.md)).
