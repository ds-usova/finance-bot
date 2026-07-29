# Intent

One action a user's message asks for.

## Invariants

- **Closed set:** [Category intent](category-intent.md), [Expense intent](expense-intent.md),
  [Unknown intent](unknown-intent.md). A fourth kind cannot exist without changing the type.

## Made of / held by

- **Made of:** nothing of its own — each kind carries its own fields.
- **Held by:** the answer to an extraction, one entry per action —
  [Extract the intents in a user's message](../usecases/extract-intents.md). What the answer means to a caller
  is in [Intent extraction](../contracts/in/intent-extraction.md#semantics).
