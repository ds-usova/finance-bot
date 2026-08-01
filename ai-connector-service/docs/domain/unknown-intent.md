# Unknown intent

An entry that could not be read as an action, carrying why.

## Invariants

- **Reason:** present and not blank.

## Made of / held by

- **Made of:** the reason.
- **Held by:** one entry of what a message was read as, as a kind of [Intent](intent.md).
- **Acted on:** never — it is logged and skipped by
  [Act on the actions in a user's message](../usecases/extract-intents.md#rules).
