# Backlog

Work still open from reviews: one row per bug not yet fixed, one row per `R` row whose owner still says
`open`. The owning `review/findings.md` holds the finding; this file only points at it. The `#` column is the
id to name when planning a bug fix or a rework — `B<n>` for a bug, `C<n>` for a refactoring candidate — and it
never changes once given.

## Bugs

| # | Raised by | Module | What | Where |
|---|-----------|--------|------|-------|

## Refactoring candidates

| #  | Raised by | Module                 | What                                                                                                  | Where                                                                                       |
|----|-----------|------------------------|-----------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| C2 | task 32   | `ai-connector-service` | keep a hand-entered expense as a row of its own, with its own retention                               | [findings](implemented/32-the-connector-learns-what-became-of-a-message/review/findings.md) |
| C6 | task 40   | `ledger-service`       | loosen the log assertion pinning an SLF4J overload, and restore the class's own three-argument idiom  | [findings](implemented/40-set-a-default-currency/review/findings.md)                        |
