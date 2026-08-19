# Spending row

One proposed or recorded expense, whole, as the ledger published it.

## Invariants

- **Expense id:** positive.
- **Person:** positive.
- **Description:** present, non-blank.
- **Amount:** present, non-blank, a decimal string in the currency's main unit — never rescaled here.
- **Currency:** present, a [currency code](currency-code.md).
- **Category:** present, a [category reference](category-ref.md).
- **Grouping:** present, a [category reference](category-ref.md) — an expense is always filed under a category
  that sits in one.
- **Merchant:** stated as present or as absent, never left unstated.
- **Message:** the same.

## Made of / held by

- **Made of:** the ledger's own id for the expense, the person it belongs to, the message it came from, the
  description, the merchant, the amount, the currency, the category it is filed under, and the grouping that
  category sits in.
- **Names it:** a [message identity](message-identity.md), where the row carries a message.
- **Held by:** the fact the ledger published, alongside the [status](recorded-status.md) it leaves the expense in
  and the [position](stream-position.md) it stands at.
