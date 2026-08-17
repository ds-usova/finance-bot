# Backlog

Refactoring candidates still open, one row per `R` row whose owner still says `open`. The owning
`review/findings.md` holds the finding; this file only points at it.

| Raised by | #  | Module                                   | What                                                                                                        | Where                                                                                       |
|-----------|----|------------------------------------------|-------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| rework 24 | R2 | `ledger-service`                         | `CategoryNames` and `CategoryRow` sit in `adapter/cdc`, imported by `adapter/persistence`                    | [findings](implemented/24-evict-one-category-name-at-a-time/review/findings.md)              |
| task 32   | R1 | `ai-connector-service`                   | keep a hand-entered expense as a row of its own, with its own retention                                      | [findings](implemented/32-the-connector-learns-what-became-of-a-message/review/findings.md)  |
| task 32   | R2 | `ai-connector-service`                   | split `ChangeStreamConsumer`: the per-entry handler out as a unit target, the consumer left as lifecycle      | [findings](implemented/32-the-connector-learns-what-became-of-a-message/review/findings.md)  |
| task 32   | R3 | `ledger-service`, `ai-connector-service` | target architecture: the ledger publishes facts through an outbox; the connector applies each as one upsert  | [findings](implemented/32-the-connector-learns-what-became-of-a-message/review/findings.md)  |
