# Category reference

A category or a grouping, by the ledger's id for it and the name it carried when the fact was published.

## Invariants

- **Id:** positive.
- **Name:** present, non-blank.

## Made of / held by

- **Made of:** the id and the name.
- **Held by:** a [spending row](spending-row.md), as the category it is filed under and as the grouping that
  category sits in.
