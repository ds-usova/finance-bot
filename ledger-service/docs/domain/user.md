# User

A person the service keeps a ledger for, known by the identity the delivering platform gives the sender of a
message.

## Invariants

- Two stored users with the same id are the same user, whatever else differs; a user not yet stored equals only
  itself.
- A user is stored or not yet stored, and carries the store's own id only once it is.

## Made of / held by

An external identity, plus the store's id once there is one.

- [Category](category.md) — what a user files spending under; a set is stored with them at creation.
- [Initialize a new user](../usecases/initialize-a-new-user.md) — creates one, or hands back the one already
  stored under an identity.
- How long an identity may be is checked where it is stored
  ([ADR 0004](../adr/0004-column-widths-are-checked-in-the-persistence-adapter.md)).
