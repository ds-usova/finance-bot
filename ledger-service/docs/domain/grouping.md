# Grouping

A heading a user's [categories](category.md) are filed under. Spending is filed under a category, never under
the grouping holding it.

## Invariants

| Field                             | Bound                              |
|-----------------------------------|------------------------------------|
| `name`                            | mandatory, non-blank               |
| [`categories`](category.md)       | may be empty, fixed at construction |

One name is designated the catch-all — where spending falls when no other grouping fits. Every user's catalogue
carries a grouping under that name.

## Made of / held by

A name and the categories filed under it.

- [Category](category.md) — what a grouping holds.
- [User](user.md) — a catalogue of groupings is stored with each one.
- [Initialize a new user](../usecases/initialize-a-new-user.md) — lists the catalogue every new user starts
  with.
- [Act on a user's message](../usecases/handle-incoming-message.md) — reads the designated catch-all when it
  tells the connector which grouping to fall back on.
- [List a grouping's categories](../usecases/list-categories.md) — answers one grouping's categories by name.
- Which names may repeat under which parent
  ([ADR 0003](../adr/0003-a-category-is-unique-per-user-and-parent-not-per-user.md)).
