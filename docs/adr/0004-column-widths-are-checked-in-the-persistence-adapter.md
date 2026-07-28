# ADR 0004: Column widths are checked in the persistence adapter

- **Status:** Accepted
- **Date:** 2026-07-28
- **Source:** [Initialize a New User](../implemented/3-plan-initialize-a-new-user.md)

## Context

The core validates its own values: a command rejects a blank field in its constructor, and a value object cannot
exist in an invalid state. The database adds limits of its own — an external identity is 255 characters, a
category name 100 — and those limits have to be enforced somewhere, or an over-long value reaches the driver and
comes back as a framework exception from a layer that is meant to know nothing about the framework.

Putting the lengths on the core types would satisfy that, and it is the first thing the existing rules suggest.
It also copies a number out of the schema into a type that has no way to know when the schema changes, and the
two are then free to disagree — the copy staying strict after a migration widens the column, with nothing
failing to say so.

## Decision

The core types carry the rules that are true regardless of storage: present, non-blank, correctly shaped. They
carry no lengths.

The persistence adapter checks what its columns require, before writing anything, and rejects a violation with
the same domain exception the core would have raised. The check walks every value it is about to write,
including both levels of a category tree.

Failures the database itself raises are translated where they occur, so a constraint violation leaves the
adapter as a domain exception rather than a framework one.

## Consequences

A width lives in one layer — the layer that also owns the schema — so widening a column is a change to the
migration and the adapter together, in one place, rather than a change to the migration that some distant value
object silently contradicts.

Callers get the same exception whichever check fires, so nothing above the port has to distinguish "too long for
the column" from "not a valid value".

The guarantee is only as wide as the failures actually translated. Today that is the unique-identity violation;
a storage failure of another kind still leaves the adapter as a framework exception, and the architecture test
cannot see it, because a propagating exception is not a compile-time dependency.

What must stay true: a core type gains no length rule, and every new outbound adapter validates its own columns
and translates its own failures rather than assuming the core did it.
