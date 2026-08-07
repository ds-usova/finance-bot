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
| Created | [Create an expense](../usecases/create-an-expense.md)                     | recorded directly                                       |
| Created | [Resolve a reported proposal](../usecases/resolve-a-reported-proposal.md) | an accepted [proposal](expense-proposal.md) becomes one |
| Changed | never                                                                     | every field is fixed at creation                        |
| Removed | never                                                                     | only with its user, by the store's own cascade          |

An expense created by accepting a proposal is dated by the acceptance, not by the proposal. See
[the proposal's lifecycle](expense-proposal.md#lifecycle) for the branch that leads here.

## Made of / held by

The owning user's id, the filed category's id, a description, an optional merchant, a [money](money.md) amount,
and the instants it was created and last updated.

- [User](user.md) — who the expense is recorded against.
- [Category](category.md) — what it is filed under, by the category's stored id.
- [Money](money.md) — what was spent, and its [currency](currency-code.md).
- [Spending period](spending-period.md) — the stretch of days a user's expenses are totalled over.
- How long its text may be is checked where it is stored
  ([ADR 0004](../adr/0004-column-widths-are-checked-in-the-persistence-adapter.md)).
