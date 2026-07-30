# Expense

One purchase or payment recorded against a user, filed under a category.

## Invariants

- An expense is stored or not yet stored, and carries the store's own id only once it is.
- Two stored expenses with the same id are the same expense, whatever else differs; an expense not yet stored
  equals only itself.

## Made of / held by

The owning user's id, the filed category's id, a description, an optional merchant, a [money](money.md) amount,
and the instants it was created and last updated.

- [User](user.md) — who the expense is recorded against.
- [Category](category.md) — what it is filed under, by the category's stored id.
- [Money](money.md) — what was spent, and its [currency](currency-code.md).
- [Create an expense](../usecases/create-an-expense.md) — what it is built from, and what stores one.
- How long its text may be is checked where it is stored
  ([ADR 0004](../adr/0004-column-widths-are-checked-in-the-persistence-adapter.md)).
