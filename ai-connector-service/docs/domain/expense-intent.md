# Expense intent

An action on an expense.

## Invariants

- **Operation:** present.
- **Create:** requires an amount, a category name and a description.
- **Read, update, delete:** amount, category name and description are each optional.
- **Grouping:** optional under every operation — a category the same message asks to create has none.

## Made of / held by

- **Made of:** an [Operation](operation.md) · the name of the category the expense is filed under · that
  category's grouping · a [Money](money.md) amount · a description.
- **Held by:** one entry of what a message was read as, as a kind of [Intent](intent.md).
- **Which category an expense may be filed under:** decided by
  [Act on the actions in a user's message](../usecases/extract-intents.md#rules).
- **Acted on:** a create is proposed to the ledger through
  [the expense proposal tool](../contracts/out/ledger-mcp.md); every other operation is skipped.
