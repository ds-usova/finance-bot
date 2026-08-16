# Spending row change

What the ledger did to one proposal or one recorded expense.

## Invariants

- **Kind:** a [spending kind](spending-kind.md), present.
- **Operation:** a [change operation](change-operation.md), present.
- **Created:** an after side, present.
- **Deleted:** a before side, present.
- **Updated:** both sides, present.
- **Neither side is left unstated.**
- **Transaction:** present, non-blank.

## Made of / held by

- **Made of:** the kind, the operation, the ledger transaction the change happened in, and up to two
  [spending rows](spending-row.md) — the row before and the row after.
- **Speaks of:** the after row where there is one, otherwise the before row.
- **Names it:** that row's [message identity](message-identity.md), where it carries a message.
- **Held by:** a [recorded change](recorded-change.md), as one of its two kinds.
