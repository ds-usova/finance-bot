# Operation

What a user asks to be done to the thing they named.

## Invariants

- **Closed set:** create, read, update, delete.
- **Read from a label:** matched ignoring case and surrounding whitespace.
- **No match is absent:** a label naming none of the four yields no operation rather than a failure, leaving the
  reader to decide what that means.

## Made of / held by

- **Made of:** the four values, and nothing else.
- **Held by:** [Category intent](category-intent.md) · [Expense intent](expense-intent.md), each of which
  requires one.
- **Absence:** turned into an unknown entry by
  [Act on the actions in a user's message](../usecases/extract-intents.md#rules).
