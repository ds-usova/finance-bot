# ADR 0004: Column widths are checked in the persistence adapter

- **Status:** Accepted
- **Date:** 2026-07-28
- **Source:** [Initialize a New User](../../../docs/implemented/3-plan-initialize-a-new-user.md)

## Context

The core validates its own values, so the database's width limits have to be enforced somewhere or an over-long
value reaches the driver and returns as a framework exception from a layer meant to know nothing about the
framework. Putting the widths on the core types is the first thing the existing rules suggest — and it copies a
number out of the schema into a type with no way to know when the schema changes.

## Decision

Core types carry the rules that hold regardless of storage: present, non-blank, correctly shaped. Widths belong
to the persistence adapter, checked before any write and rejected with the same domain exception the core would
have raised. A test reads the live column widths and fails if they and the constants disagree.

## Consequences

- Widening a column is one change to the migration and the adapter together; the test catches the half-done
  version.
- Callers get one exception whichever check fires, so nothing above the port distinguishes "too long for the
  column" from "not a valid value".
- Must stay true: no core type gains a length rule, and every new outbound adapter validates its own columns and
  translates its own failures.
