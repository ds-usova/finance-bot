# Category row

One category or grouping, as the ledger holds it — one side of a change the ledger announced. A row with a
parent is a category; one without is a grouping.

## Invariants

- **Name:** present, non-blank.
- **Parent:** stated as present or as absent, never left unstated.

## Made of / held by

- **Made of:** the ledger's own id for the row, the person it belongs to, the grouping above it, and its name.
- **Held by:** a [category row change](category-row-change.md), as its before side, its after side, or both.
