# Spending row

One proposed or recorded expense, as the ledger holds it — one side of a change the ledger announced.

## Invariants

- **Description:** present, non-blank.
- **Currency:** present, a [currency code](currency-code.md).
- **Merchant:** stated as present or as absent, never left unstated.
- **Message:** the same.
- **Category name, grouping name:** the same.

## Made of / held by

- **Made of:** the ledger's own id for the row, the person it belongs to, the message it came from, the
  description, the merchant, the amount in the currency's minor units, the currency, the category it is filed
  under, and the names of that category and its grouping.
- **Names it:** a [message identity](message-identity.md), where the row carries a message.
- **Held by:** a [spending row change](spending-row-change.md), as its before side, its after side, or both.
