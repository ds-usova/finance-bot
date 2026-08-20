# Embedding

Where a message sits in the meaning-space the AI provider measures. Two messages are alike to the degree their
embeddings are close, which is how a message finds the earlier ones that read like it — a person who writes
"lunch at the canteen" reaches what they wrote when they last ate out, and not what they wrote about a train
ticket.

## Invariants

| Field        | Bound                                |
|--------------|--------------------------------------|
| `values`     | mandatory, at least one, none absent |
| each value   | the provider's own number, never rounded or rescaled |

The numbers carry no unit and no order a reader can use. An embedding says nothing on its own, is never shown to
anyone, and is only ever comparable with embeddings from the same model.

## Made of / held by

A list of components, in the order the provider answered them.

- [AI provider](../contracts/out/ai-provider.md#embedding-a-message) — computes one from a message's text.
- [Recall the person's own worked examples](../usecases/recall-examples.md) — the query its neighbours are found
  by.
- [Embed the messages nothing has embedded yet](../usecases/backfill-embeddings.md) — gives one to a message no
  turn managed to embed.
- [Database](../contracts/out/database.md) — where one is kept, and what compares them.
