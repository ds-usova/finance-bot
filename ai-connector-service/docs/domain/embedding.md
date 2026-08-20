# Embedding

Where a message sits in the meaning-space the AI provider measures. Two messages are alike to the degree their
embeddings are close, which is how a message finds the earlier ones that read like it — a person who writes
"lunch at the canteen" reaches what they wrote when they last ate out, and not what they wrote about a train
ticket.

The numbers carry no unit and no order a reader can use. An embedding says nothing on its own, is never shown to
anyone, and is only ever compared with embeddings from the same model.

## Invariants

- **Components:** present, at least one, none of them absent.
- **Fidelity:** the provider's own numbers — never rounded or rescaled.

## Made of / held by

- **Made of:** the components themselves, in the provider's order.
- **Computed by:** the [AI provider](../contracts/out/ai-provider.md#embedding-a-message), from one message's
  text.
- **Held by:** the message it was computed for, [kept in the store](../contracts/out/database.md).
- **Compared by:** the store. This type holds no comparison of its own.
- **Used by:** [Recall the person's own worked examples](../usecases/recall-examples.md), as the query the
  neighbours are found by.
