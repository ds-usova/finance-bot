# Design Log: Discard Chosen Proposals

## Concerns

Grilled (2026-10-12): grill-design.

| Concern             | Verdict                                                          | Why                                                                                          |
|---------------------|------------------------------------------------------------------|----------------------------------------------------------------------------------------------|
| Failure modes       | 503 with nothing persisted, except a transaction-boundary 500    | `WebExceptionHandler` maps `PersistenceFailedException`; the boundary gap is DF07              |
| Idempotency & retry | a repeat answers `discarded` 0, `missing` N, and no second fact  | the removal matches `PENDING` only and the outbox skips an empty set (DF05, AC03)             |
| Concurrency         | safe against a Telegram tap and an acceptance; not against a running web turn | ADR 0018 under `READ COMMITTED`; the running turn is DF02                          |
| Recovery            | a lost reply is checkable by listing; a dropped fact is not retryable | ids are stable (ADR 0018); a fact is dropped, not retried (DF01)                        |
| Data                | no schema change; a hard delete no row references                | the Telegram Delete already removes the row (`ExpenseEntityRepository.discard`)               |
| Contract compat     | additive path; `WrittenMessage.days` may newly read empty        | DN02, DN03                                                                                    |
| Lifecycle           | a removed row has no further life in the ledger                  | the connector's `DISCARDED` row ends with its own purge; a fully discarded web text is DN02   |
| Authorization       | the caller's identity only, `user_id` in every match             | AC05, AC07, AC08; an unmatched path is denied, so the new one must be admitted (DF04)         |
| Security            | at most 100 int64 ids, bound as parameters                       | `ProposalIds`; logs carry only the external id                                                |
| Observability       | one info line per discard, as the acceptance logs                | DF12; the resolution meter stays Telegram-only; a dropped clearing is silent (DF13)          |
| Limits              | 100 ids per request                                              | `ProposalIds.MAX_IDS`; the module measures no performance (DF06)                              |
| Business invariants | only `PENDING` rows are removed, `RECORDED` never touched        | AC04; a partly discarded report keeps listing the gone entry, as a partial acceptance does   |
| Stack-neutral       | pass after edits                                                 | filter-chain, thread and build-time wording removed from both artifacts (DF10)               |

## Findings

| #    | Question                                                     | Answer                                                                          | Evidence                                                                                           |
|------|--------------------------------------------------------------|---------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------|
| DF01 | Do the removal and its facts always commit together?         | No; an unrecordable fact is logged, metered and dropped, the removal commits    | `ledger-service/src/main/java/bot/finance/adapter/persistence/LedgerEventOutbox.java`              |
| DF02 | A discard while a web-filed message's turn still runs?       | Deferred; the outcome can misreport. The runner discards only after `DONE`      | `HandleWrittenMessageUseCase.java`, `SweepWrittenMessagesUseCase.java`; returns with DN01's page    |
| DF03 | Is a pending entry discarded by removing its row?            | Yes, as the Telegram Delete does                                                | `ledger-service/src/main/java/bot/finance/adapter/persistence/ExpenseEntityRepository.java`        |
| DF04 | Must the new path be admitted explicitly?                    | Yes; an unmatched path is denied                                                | `ledger-service/src/main/java/bot/finance/adapter/security/SecurityConfiguration.java`             |
| DF05 | What answers an id naming nothing pending of the caller's?   | 200, counted in `missing`, as the acceptance does                               | `openapi/paths/expense-acceptances.yaml`                                                           |
| DF06 | Is the endpoint's latency measured?                          | No; the module names no performance tool                                        | `ledger-service/docs/conventions/testing.md`                                                       |
| DF07 | Is a store failure always 503?                               | Deferred; a transaction open or commit failure answers 500, as the acceptance's | `ExpenseRepositoryAdapter.java`, `WebExceptionHandler.java`; returns with a backlog entry for both  |
| DF08 | What do null, absent or non-integer ids answer?              | 400, through the existing list and body handlers                                | `ledger-service/src/main/java/bot/finance/domain/value/ProposalIds.java`, `WebExceptionHandler.java` |
| DF09 | Where are the partial-report and later-tap branches drawn?   | In the existing clearing and resolution flows, unchanged                        | `ledger-service/docs/usecases/clear-emptied-reports.md`, `ResolveProposalsUseCase.java`             |
| DF10 | Does the flow need a diagram for each refusal?               | No; refusals are a table, only the write's ordering is drawn                    | `docs/49-discard-chosen-proposals/discard-flow.md`                                                 |
| DF11 | Which documents name only the acceptance as a source?        | The clearing use case and the change-stream contract                            | `ledger-service/docs/usecases/clear-emptied-reports.md`, `ledger-service/docs/contracts/out/change-stream.md` |
| DF12 | Does a web discard reach the resolution meter?               | No; one info line with both counts, as the acceptance                           | `AcceptExpensesUseCase.java`, `ResolveProposalsUseCase.java`                                        |
| DF13 | What trace does a dropped report clearing leave?             | Deferred; none, the pool drops it silently, as for the acceptance               | `ReportClearingConfiguration.java`, `ExecutorReportClearingDispatcher.java`; returns with a backlog entry |
| DF14 | Can a person discard a pending entry from the page?          | Deferred; only through the endpoint until a later task adds the action         | `docs/backlog.md` BT04                                                                             |

## Decision Bases

- **DN01:** The user chose the endpoint alone over adding the page action now; the evaluation runner is the only
  caller that needs it (`docs/eval/implementation/demo-plan.md`), and a page action would add `web-app` and a
  second grill to a two-day demo.
- **DN02:** The user chose describing an empty `days` over keeping the days of discarded entries; `days` is read
  from the entries still stored (`ExpenseRepository.findDaysByMessageReference`), and keeping them would need a
  store of their own (`openapi/ledger-api.yaml`).
- **DN03:** The user chose a `DiscardRequest` of its own over sharing `AcceptanceRequest`; that schema's text says
  "to accept" (`openapi/ledger-api.yaml`), and renaming it to a shared name would change the acceptance's
  generated types.
