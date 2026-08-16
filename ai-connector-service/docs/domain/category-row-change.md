# Category row change

What the ledger did to one category or grouping.

## Invariants

- **Operation:** a [change operation](change-operation.md), present.
- **Created:** an after side, present.
- **Deleted:** a before side, present.
- **Updated:** both sides, present.
- **Neither side is left unstated.**

## Made of / held by

- **Made of:** the operation, and up to two [category rows](category-row.md) — the row before and the row after.
- **Held by:** a [recorded change](recorded-change.md), as one of its two kinds.
