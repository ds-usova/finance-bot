# Intent

One action a user's message asks for.

## Invariants

- **Closed set:** [Category intent](category-intent.md), [Expense intent](expense-intent.md),
  [Unknown intent](unknown-intent.md). A fourth kind cannot exist without changing the type.

## Made of / held by

- **Made of:** nothing of its own — each kind carries its own fields.
- **Held by:** what a message was read as, one entry per action — no intent leaves the service.
- **Acted on:** which kinds are acted on and which are skipped is decided by
  [Act on the actions in a user's message](../usecases/extract-intents.md#rules).
