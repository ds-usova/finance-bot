# Spending period

A stretch of days a user's spending can be asked about — a first day and a last day, both counted.

## Invariants

- A first day and a last day are both present.
- The last day is not before the first.
- A period of one day is a period: the two ends may be the same day.
- No maximum span is imposed.
- No day is refused for lying in the future.

## Built from written dates

A period can be given as the two days were written — `2026-07-27` and `2026-08-02` — and is read the same way
[money](money.md) takes a written amount.

Refused, never adjusted:

- A day that is absent or blank, naming which of the two is at fault.
- A day that is not an ISO-8601 `YYYY-MM-DD` value, naming the value that could not be read.
- A day that reads as a date but names none — the thirtieth of February.
- A relative phrase. A period is never worked out here; it arrives already resolved to two days.

## Made of / held by

Two days.

- [Spending query](spending-query.md) — the period one caller asked about, tied to the message that asked.
- [Summarize spending over a period](../usecases/summarize-spending.md) — what builds one from the days a call
  was made with, and answers it back.
- [Act on a user's message](../usecases/handle-incoming-message.md) — totals the ledger over one, by currency.
- [Database](../contracts/out/database.md) — how a period's days become the bounds of a read over stored
  expenses.
