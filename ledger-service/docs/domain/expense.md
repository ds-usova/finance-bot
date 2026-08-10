# Expense

One purchase or payment recorded against a user, filed under a category.

## Invariants

- An expense is stored or not yet stored, and carries the store's own id only once it is.
- Two stored expenses with the same id are the same expense, whatever else differs; an expense not yet stored
  equals only itself.

## Lifecycle

One state, reached two ways and never left.

| Event   | By                                                                        | Notes                                                   |
|---------|---------------------------------------------------------------------------|---------------------------------------------------------|
| Created | [Create an expense](../usecases/create-an-expense.md)                            | recorded directly                                       |
| Created | [Resolve a reported proposal](../usecases/resolve-a-reported-proposal.md)        | an accepted [proposal](expense-proposal.md) becomes one |
| Created | [Accept the proposals a person chose](../usecases/accept-chosen-proposals.md)    | the same, for the entries a person ticked on the page   |
| Changed | never                                                                            | every field is fixed at creation                        |
| Removed | never                                                                            | only with its user, by the store's own cascade          |

An expense created by accepting a proposal carries the day the proposal was made, on either path. Only the
last-updated instant is the moment of acceptance, so an entry stays on the day it first appeared on. See
[the proposal's lifecycle](expense-proposal.md#lifecycle) for the branch that leads here.

## Made of / held by

The owning user's id, the filed category's id, a description, an optional merchant, a [money](money.md) amount,
the incoming message id of the message it came from where one did, and the instants it was created and last
updated.

- [User](user.md) — who the expense is recorded against.
- [Incoming message id](incoming-message-id.md) — which message produced it, kept through the acceptance.
- [Category](category.md) — what it is filed under, by the category's stored id.
- [Money](money.md) — what was spent, and its [currency](currency-code.md).
- [Spending period](spending-period.md) — the stretch of days a user's expenses are totalled over.
- How long its text may be is checked where it is stored
  ([ADR 0004](../adr/0004-column-widths-are-checked-in-the-persistence-adapter.md)).
