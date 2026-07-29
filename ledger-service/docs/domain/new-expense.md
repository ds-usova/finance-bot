# New expense

A request to record an expense against an existing user.

## Invariants

- A user external identity is present, and it is not blank.
- A description is present, and it is not blank.
- A category is present, named by its stored id, and the id is positive.
- A merchant is present as an optional value, never absent — but a present, blank merchant is normalized to
  absent rather than rejected.
- A [money](money.md) amount is present.

## Made of / held by

A user external identity, a category id, a description, an optional merchant, and a money amount.

- [Expense](expense.md) — what it becomes once stored.
- [Create an expense](../usecases/create-an-expense.md) — what it is handed to.
- Naming the category by its stored id rather than by name
  ([ADR 0007](../adr/0007-a-category-is-named-by-its-stored-id.md)).
