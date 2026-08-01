# Category intent

An action on one of the user's categories.

## Invariants

- **Operation:** present.
- **Name:** present and not blank.
- **New name:** required when the operation is update; optional under every other operation.

## Made of / held by

- **Made of:** an [Operation](operation.md) · the category's name · the new name, when renaming.
- **Held by:** one entry of what a message was read as, as a kind of [Intent](intent.md).
- **Which names may appear:** decided by
  [Act on the actions in a user's message](../usecases/extract-intents.md#rules).
- **Acted on:** never — it is logged and skipped, though a created name widens the set later entries may be
  filed under.
