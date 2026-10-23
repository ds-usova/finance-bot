# Category choice

A category, named together with the grouping it sits in.

## Invariants

| Field      | Bound                |
|------------|----------------------|
| `grouping` | mandatory, non-blank |
| `category` | mandatory, non-blank |

Two choices are the same when both names match. A category name alone may repeat across groupings.

## Made of / held by

Two names.

- [Catalogue entry](catalogue-entry.md) — offers one per category it lists.
- [Category classifier](../contracts/out/classifier.md) — each option it answers stands for one.
- [Record the spending a user's message names](../usecases/extract-intents.md) — settles one per expense.
- [Ledger turn operations](../contracts/out/ledger-turn.md) — an expense is filed under one.
