# Expense to file

One expense read out of a message, fit to be classified and filed.

## Invariants

| Field                          | Bound                                                                      |
|--------------------------------|----------------------------------------------------------------------------|
| `description`                  | mandatory, non-blank                                                       |
| `merchant`                     | optional; a blank one reads as absent                                      |
| `amount`                       | mandatory, `> 0`; digits, optionally a dot and digits; no sign or exponent |
| [`currency`](currency-code.md) | mandatory                                                                  |

The amount is in the currency's main unit, as the message wrote it.

## Made of / held by

Plain values and a currency code. Built per turn, never stored.

- [Currency code](currency-code.md) — what the amount is counted in.
- [Record the spending a user's message names](../usecases/extract-intents.md) — makes one of each usable
  expense the model read out.
- [Category classifier](../contracts/out/classifier.md) — asked one question about each.
- [Ledger turn operations](../contracts/out/ledger-turn.md) — each is filed as one proposal.
