# Review: The Connector Learns What Became of a Message

**Nothing open in this task; one refactoring candidate deferred (R1 open).**

## Refactoring candidate

| #  | Status | module                 | what                                                                                                                                                                                                              | why                                                                                                                                                                                                                             |
|----|--------|------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| R1 | open   | `ai-connector-service` | An expense with no `incoming_message_id` (entered by hand, once the ledger lets a person do that) is ignored on `c`, `u` and `d` (design F15). Keep it instead, as a row that hangs off no message, with its own retention. | A hand-entered expense is the most reliable example of how that person files spending. Today `recorded_expense.message_id` is `NOT NULL` and every row is purged with its message, so keeping one is a design change, not a fix. Nobody can create one yet |
