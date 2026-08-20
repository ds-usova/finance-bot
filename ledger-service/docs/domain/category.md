# Category

A label a user files spending under. A category is filed under a [grouping](grouping.md) and holds nothing
itself.

## Invariants

| Field    | Bound                                          |
|----------|------------------------------------------------|
| `name`   | mandatory, non-blank                           |
| `parent` | mandatory — always a [grouping](grouping.md) |

Spending is filed under a category, never under a grouping. A grouping holds categories, and a category holds
spending; nothing is filed under a heading that is itself a heading.

## Made of / held by

A name.

- [Grouping](grouping.md) — what a category is filed under.
- [Create an expense proposal](../usecases/create-an-expense-proposal.md) — files spending under one, named
  together with its grouping.
- [List a grouping's categories](../usecases/list-categories.md) — answers the categories one grouping holds.
- Which names may repeat under which parent
  ([ADR 0003](../adr/0003-a-category-is-unique-per-user-and-parent-not-per-user.md)).
