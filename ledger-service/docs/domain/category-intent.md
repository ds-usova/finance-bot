# Category intent

A request to do something with one of a user's categories.

## Invariants

- An [operation](operation.md) is present.
- The name of the category to act on is present and not blank.
- Update requires the new name. Under any other operation a new name is optional.

## Made of / held by

An operation, the category's name, and a new name where one is given.

- [Intent](intent.md) — one of the three kinds.
- [Category](category.md) — what the name refers to.
