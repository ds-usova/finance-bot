# ADR 0003: A category is unique per user and parent, not per user

- **Status:** Accepted
- **Date:** 2026-07-28
- **Source:** [Initialize a New User](../implemented/3-plan-initialize-a-new-user.md)

## Context

Every user starts with the same predefined set of spending categories, arranged as groups holding children. The
obvious constraint on that table is one name per user — until the set itself is read: `Travel` appears twice,
once as a group of its own and once under `Insurance`. Both are wanted, and neither is a mistake in the list.

A per-user uniqueness rule therefore cannot be applied without editing the product's own category set, and
dropping uniqueness altogether would let a later feature write a duplicate that no rule catches.

The parent column is the complication. Uniqueness must span it, and a group has no parent — and in SQL a null
is distinct from every other null, so a plain unique index would constrain the children and silently exempt the
20 groups, which are exactly the rows a duplicate is most visible on.

## Decision

A category is unique on the user, the parent, and the name together. Two categories may share a name when they
sit under different parents, and a top-level name may repeat a name used beneath some other group.

The index is declared `NULLS NOT DISTINCT`, so the rule covers top-level categories as one another's peers
rather than exempting them.

The tree is exactly two levels, enforced where categories are constructed rather than by the schema: a category
whose child carries children of its own is rejected. The write path pairs children to groups in one pass and
depends on that.

## Consequences

The predefined set can carry a repeated name wherever the product wants one, and the constraint needs no
exception list.

Finding a category by name alone is not a lookup that can return one row; the parent is part of the key, and
any later feature that resolves a user's category from text has to say which parent it means or accept several
matches.

`NULLS NOT DISTINCT` requires PostgreSQL 15 or newer. The service runs 18, and the version is now load-bearing
rather than incidental.

What must stay true: the tree stays two levels deep. A third level would need a new pairing strategy on the
write path and would make the parent column ambiguous as a uniqueness key.
