# Intent target

The thing a user's action is about: a category or an expense.

## Invariants

- **Closed set:** category, expense.
- **Read from a label:** matched ignoring case and surrounding whitespace.
- **No match is absent:** a label naming neither yields no target rather than a failure, leaving the reader to
  decide what that means.

## Made of / held by

- **Made of:** the two values, and nothing else.
- **Held by:** no intent carries it — it decides which kind of [Intent](intent.md) an entry becomes.
- **Absence:** turned into an unknown entry by
  [Extract the intents in a user's message](../usecases/extract-intents.md#rules).
