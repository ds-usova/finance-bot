# Category

A label a user files spending under. A category is either a group or one of a group's children.

## Invariants

- A category has a name, and the name is not blank.
- A category has a list of children; a leaf's list is empty.
- Every entry in that list is a category.
- The tree is two levels: a group's children have no children of their own.
- The children are fixed once the category exists.
- One grouping name is designated the catch-all — the grouping spending falls to when no other fits.
- The catalogue every new user starts with builds that grouping under the designated name, so the two cannot
  drift apart.
- Nothing else stands in for the catch-all: a user's groupings that do not carry the designated name are a
  catalogue that cannot exist, and the turn reading them ends there.

## Made of / held by

A name and its children, which are categories themselves.

- [User](user.md) — a set of categories is stored with each one.
- [Initialize a new user](../usecases/initialize-a-new-user.md) — lists the catalogue every new user starts with.
- [Act on a user's message](../usecases/handle-incoming-message.md) — reads the designated catch-all when it
  tells the connector which grouping to fall back on.
- [List a grouping's categories](../usecases/list-categories.md) — answers a grouping's children by name.
- Which names may repeat under which parent
  ([ADR 0003](../adr/0003-a-category-is-unique-per-user-and-parent-not-per-user.md)).
