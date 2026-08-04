# Grouping

A heading a user's [categories](category.md) are filed under. Spending is filed under a category, never under
the grouping holding it.

## Invariants

- A grouping has a name, and the name is not blank.
- A grouping has a list of categories, and may hold none.
- Every entry in that list is a category.
- The categories are fixed once the grouping exists.
- One grouping name is designated the catch-all — the grouping spending falls to when no other fits.
- The catalogue every new user starts with carries a grouping under the designated name, so the two cannot
  drift apart.
- Nothing else stands in for the catch-all: a user's groupings that do not carry the designated name are a
  catalogue that cannot exist, and the turn reading them ends there.

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
