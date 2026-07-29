# Category intent

An action on one of the user's categories.

## Invariants

- **Operation:** present.
- **Name:** present and not blank.
- **New name:** required when the operation is update; optional under every other operation.

## Made of / held by

- **Made of:** an [Operation](operation.md) · the category's name · the new name, when renaming.
- **Held by:** one entry of an extraction answer, as a kind of [Intent](intent.md).
- **Which names may appear:** decided by
  [Extract the intents in a user's message](../usecases/extract-intents.md#rules).
