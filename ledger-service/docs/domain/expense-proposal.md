# Expense proposal

One spending record assembled against a user and filed under a category, not yet accepted into their ledger
([ADR 0006](../adr/0006-an-expense-proposal-is-a-table-and-an-entity-of-its-own.md)).

## Invariants

- A proposal is stored or not yet stored, and carries the store's own id only once it is.
- Two stored proposals with the same id are the same proposal, whatever else differs; a proposal not yet stored
  equals only itself.
- A description is present, and it is not blank.
- A merchant is present as an optional value, never absent; the value itself may be empty.
- A money amount is present.
- The owning user's id and the filed category's id are both positive.
- The reference of the message that produced it is present.
- Both instants are present.

## Lifecycle

| Event    | By                                                                                | Notes                                             |
|----------|-----------------------------------------------------------------------------------|---------------------------------------------------|
| Created  | [Create an expense proposal](../usecases/create-an-expense-proposal.md)           | one per spending the model read out of a message  |
| Changed  | never                                                                             | every field is fixed at creation                  |
| Removed  | [Resolve a reported proposal](../usecases/resolve-a-reported-proposal.md)         | accepted or discarded, and removed either way     |

Accepting does not change a proposal's state — it removes the proposal and writes an [expense](expense.md)
carrying the same values, in one statement
([ADR 0012](../adr/0012-a-set-of-rows-moves-between-tables-in-one-statement.md)). The two are separate tables and
separate types ([ADR 0006](../adr/0006-an-expense-proposal-is-a-table-and-an-entity-of-its-own.md)), so "pending"
and "recorded" are not a column on either.

```plantuml
@startuml
[*] --> Proposed : Create an expense proposal
Proposed --> [*] : Resolve — discarded
Proposed --> Recorded : Resolve — accepted
state Recorded #line.dashed : an Expense, in its own table
Recorded --> [*] : only with its user
@enduml
```

A proposal nobody resolves stays proposed. Nothing expires one.

## Made of / held by

The owning user's id, the filed category's id, a description, an optional merchant, a [money](money.md) amount,
the reference of the message it came from, and the instants it was created and last updated.

- [User](user.md) — who the proposal is recorded against.
- [Category](category.md) — what it is filed under, by the category's stored id.
- [Message reference](message-reference.md) — which message produced it, and what the report answering that
  message is assembled from.
- [Money](money.md) — what was proposed, and its [currency](currency-code.md).
- How long its text may be is checked where it is stored
  ([ADR 0004](../adr/0004-column-widths-are-checked-in-the-persistence-adapter.md)).
