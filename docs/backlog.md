# Backlog

Work still open from reviews: one row per bug not yet fixed, one row per `R` row whose owner still says
`open`. The owning `review/findings.md` holds the finding; this file only points at it. The `#` column is the
id to name when planning a bug fix or a rework — `B<n>` for a bug, `C<n>` for a refactoring candidate — and it
never changes once given.

## Bugs

| #  | Raised by | Module   | What | Where |
|----|-----------|----------|------|-------|

## Refactoring candidates

| #  | Raised by | Module                                   | What                                                                                                        | Where                                                                                       |
|----|-----------|------------------------------------------|-------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| C1 | rework 24 | `ledger-service`                         | `CategoryNames` and `CategoryRow` sit in `adapter/cdc`, imported by `adapter/persistence`                   | [findings](implemented/24-evict-one-category-name-at-a-time/review/findings.md)             |
| C2 | task 32   | `ai-connector-service`                   | keep a hand-entered expense as a row of its own, with its own retention                                     | [findings](implemented/32-the-connector-learns-what-became-of-a-message/review/findings.md) |
| C3 | task 32   | `ai-connector-service`                   | split `ChangeStreamConsumer`: the per-entry handler out as a unit target, the consumer left as lifecycle    | [findings](implemented/32-the-connector-learns-what-became-of-a-message/review/findings.md) |
| C4 | task 32   | `ledger-service`, `ai-connector-service` | target architecture: the ledger publishes facts through an outbox; the connector applies each as one upsert | [findings](implemented/32-the-connector-learns-what-became-of-a-message/review/findings.md) |
| C5 | task 34   | `ledger-service`, `web-app`              | drop the `{status}` segment from the refile path — it identifies nothing now that ids are unique            | [findings](implemented/34-one-expense-table-with-a-status/review/findings.md)               |
| C6 | task 40   | `ledger-service`                         | loosen the log assertion pinning an SLF4J overload, and restore the class's own three-argument idiom        | [findings](implemented/40-set-a-default-currency/review/findings.md)                        |
| C7 | task 40   | `web-app`                                | show module-owned wording for a refused call, instead of the ledger's own English message                   | [findings](implemented/40-set-a-default-currency/review/findings.md)                        |
