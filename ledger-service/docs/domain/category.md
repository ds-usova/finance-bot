# Category

A label a user files spending under. A category is either a group or one of a group's children.

## Invariants

- A category has a name, and the name is not blank.
- A category has a list of children; a leaf's list is empty.
- Every entry in that list is a category.
- The tree is two levels: a group's children have no children of their own.
- The children are fixed once the category exists.

## Made of / held by

A name and its children, which are categories themselves.

- [User](user.md) — a set of categories is stored with each one.
- [Initialize a new user](../usecases/initialize-a-new-user.md) — lists the catalogue every new user starts with.
- Which names may repeat under which parent
  ([ADR 0003](../adr/0003-a-category-is-unique-per-user-and-parent-not-per-user.md)).
