# ADR 0003: A category is unique per user and parent, not per user

- **Status:** Accepted
- **Date:** 2026-07-28
- **Source:** [Initialize a New User](../../../docs/implemented/3-initialize-a-new-user/3-plan-initialize-a-new-user.md)

## Context

Every user starts with the same predefined categories, arranged as groups holding children. One name per user is
the obvious constraint — until the set is read: `Travel` appears both as a group and under `Insurance`, and both
are wanted. The parent column complicates it, because a group has no parent and SQL treats every null as
distinct, so a plain unique index would constrain the children and silently exempt the groups.

## Decision

A category is unique on user, parent and name together, with the index declared `NULLS NOT DISTINCT` so the rule
covers top-level categories as one another's peers. The tree is exactly two levels, enforced where categories
are constructed rather than by the schema.

## Consequences

- The predefined set can repeat a name under a different parent, with no exception list.
- Resolving a category from a name alone can match several rows; the parent is part of the key.
- Requires PostgreSQL 15 or newer, so the version is load-bearing rather than incidental.
- Must stay true: two levels. A third would need a new pairing strategy on the write path and would make the
  parent column ambiguous as a key.
