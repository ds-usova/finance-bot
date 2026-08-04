# Category

A label a user files spending under. A category is filed under a [grouping](grouping.md) and holds nothing
itself.

## Invariants

- A category has a name, and the name is not blank.

## Made of / held by

A name.

- [Grouping](grouping.md) — what a category is filed under.
- [Create an expense proposal](../usecases/create-an-expense-proposal.md) — files spending under one, named
  together with its grouping.
- [List a grouping's categories](../usecases/list-categories.md) — answers the categories one grouping holds.
- Which names may repeat under which parent
  ([ADR 0003](../adr/0003-a-category-is-unique-per-user-and-parent-not-per-user.md)).
