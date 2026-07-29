# Intent extraction request

What the service asks the AI Connector to read: a line a user wrote, the categories that user has, and the
currency to assume when an amount is stated without one.

## Invariants

- Text is present, and it is not blank.
- At least one category is given, and none of them is blank.
- The assumed currency is stated as present or absent; it is never left unsaid.
- The categories are fixed once the request exists — a caller changing the list it passed cannot change the
  request.

## Made of / held by

Text, a list of category names, and an optional [currency code](currency-code.md).

- [AI Connector Service — intent extraction](../contracts/out/ai-connector.md) — what crosses out, and what
  comes back.
- [Intent](intent.md) — the answer's entries.
