# Expense intent

An action on an expense.

## Invariants

- **Operation:** present.
- **Create:** requires an amount and a category name.
- **Read, update, delete:** amount, category name and description are each optional.

## Made of / held by

- **Made of:** an [Operation](operation.md) · the name of the category the expense is filed under · a
  [Money](money.md) amount · a description.
- **Held by:** one entry of an extraction answer, as a kind of [Intent](intent.md).
- **Which category an expense may be filed under:** decided by
  [Extract the intents in a user's message](../usecases/extract-intents.md#rules).
