# Expense intent

A request to do something with a user's spending.

## Invariants

- An [operation](operation.md) is present.
- Create requires an amount and a category name.
- Read, update and delete require nothing beyond the operation.

## Made of / held by

An operation, and — where the message gives them — a category name, an [amount](money.md) and a description.

- [Intent](intent.md) — one of the three kinds.
- [Category](category.md) — what the category name refers to.
