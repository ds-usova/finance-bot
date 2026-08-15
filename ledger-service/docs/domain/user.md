# User

A person the service keeps a ledger for, known by the identity the delivering platform gives the sender of a
message.

## Invariants

| Field        | Bound                |
|--------------|----------------------|
| `id`         | only once stored     |
| `externalId` | mandatory, non-blank |

Two stored users with the same `id` are the same user. An unstored one equals only itself.

## Lifecycle

| Event   | By                                                                  | Notes                                                          |
|---------|---------------------------------------------------------------------|-----------------------------------------------------------------|
| Created | [Initialize a new user](../usecases/initialize-a-new-user.md)       | find-or-create; a second sign-in resolves to the row already there |
| Changed | never                                                               | the external identity is the whole of it                       |
| Removed | never                                                               | nothing deletes a user, and everything else cascades from one  |

## Made of / held by

An external identity, plus the store's id once there is one.

- [Authenticated user id](authenticated-user-id.md) — what a request names a person by, once they are signed in.
- [Grouping](grouping.md) — a catalogue of them, holding the [categories](category.md) a user files spending
  under, is stored with them at creation.
- How long an identity may be is checked where it is stored
  ([ADR 0004](../adr/0004-column-widths-are-checked-in-the-persistence-adapter.md)).
