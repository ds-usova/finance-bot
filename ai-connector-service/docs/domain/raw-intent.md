# Raw intent

One entry as the model answered it, before anything has judged whether it makes sense.

## Invariants

None — and that is the point. Every field may be absent, because a bad answer from a model is an ordinary
outcome rather than an error. Deciding what an entry means, and rejecting what cannot be made sense of, is the
use case's job.

## Made of / held by

A target, an operation, a category name, a new category name, an amount, a currency and a description — all as
text, exactly as they came back.

- [Extract the intents in a user's message](../usecases/extract-intents.md) — turns one into an
  [intent](intent.md), or into an [unknown intent](unknown-intent.md) carrying why not.
- [AI provider](../contracts/out/ai-provider.md) — where one comes from.
