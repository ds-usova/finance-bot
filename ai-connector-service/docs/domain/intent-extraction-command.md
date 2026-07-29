# Intent extraction command

What a caller asks this service to read: a line a user wrote, the closed set of categories that user has, and
the currency to assume when an amount is stated without one.

## Invariants

- Text is present, and it is not blank.
- At least one category is given, and none of them is null or blank.
- The assumed currency is stated as present or absent; it is never left unsaid.
- The categories are fixed once the command exists — a caller changing the list it passed cannot change the
  command.

## Made of / held by

Text, a list of category names, and an optional [currency code](currency-code.md).

- [Ledger Service — intent extraction](../contracts/in/intent-extraction.md) — what the caller may send, and
  what it gets back.
- [Extract the intents in a user's message](../usecases/extract-intents.md) — what reads it.
