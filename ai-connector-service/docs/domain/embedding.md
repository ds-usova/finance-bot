# Embedding

What a message means, as the AI provider measures it — the vector two messages are compared by.

## Invariants

- **Components:** present, at least one, none of them absent.
- **Fidelity:** kept exactly as the provider answered them — never rounded or rescaled.
- **Copied:** the components are taken on construction, so the list handed in cannot change them afterwards.

## Made of / held by

- **Made of:** the components themselves, in the provider's order.
- **Held by:** the message it was computed for, [kept in the store](../contracts/out/database.md).
- **Used by:** [Recall the person's own worked examples](../usecases/recall-examples.md), as the query the
  neighbours are found by.
