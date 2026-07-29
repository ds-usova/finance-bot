# ADR 0007: A category is named to the expense port by its stored id

- **Status:** Accepted
- **Date:** 2026-07-29
- **Source:** [Create an Expense](../../../docs/implemented/5-plan-create-expense.md)

## Context

Every expense is filed under a category, and the identity a caller holds is usually the name — that is what an
extracted intent carries. A name is not unique, though: uniqueness is per parent
([ADR 0003](0003-a-category-is-unique-per-user-and-parent-not-per-user.md)), so `Travel` names both a group and a
child of Insurance. Resolving one inside the service would mean inventing a rule for the ambiguous case and a
second failure the caller cannot act on.

## Decision

The create-expense command carries the category's stored id. Whoever holds only a name resolves it before
calling, on its own terms, and an id naming no category is refused by the store like any other bad write.

## Consequences

- The command mixes an opaque identity for the user with a stored id for the category; a caller must have read
  the user's categories before it can record spending.
- Ambiguity is decided where the name came from, which is the only place that knows what the user meant.
- Must stay true: no name-to-category resolution is added behind this port — a caller that needs one gets its own
  read side.
