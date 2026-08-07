# User

A person the service keeps a ledger for, known by the identity the delivering platform gives the sender of a
message.

## Invariants

- Two stored users with the same id are the same user, whatever else differs; a user not yet stored equals only
  itself.
- A user is stored or not yet stored, and carries the store's own id only once it is.

## Lifecycle

One state, entered once and never left.

| Event   | By                                                                  | Notes                                                          |
|---------|---------------------------------------------------------------------|-----------------------------------------------------------------|
| Created | [Initialize a new user](../usecases/initialize-a-new-user.md)       | find-or-create; a second sign-in resolves to the row already there |
| Changed | never                                                               | the external identity is the whole of it                       |
| Removed | never                                                               | nothing deletes a user, and everything else cascades from one  |

Creation also stores the default [groupings](grouping.md) and [categories](category.md), so a user never exists
without a tree to file spending under.

## Made of / held by

An external identity, plus the store's id once there is one.

- [Grouping](grouping.md) — a catalogue of them, holding the [categories](category.md) a user files spending
  under, is stored with them at creation.
- How long an identity may be is checked where it is stored
  ([ADR 0004](../adr/0004-column-widths-are-checked-in-the-persistence-adapter.md)).
