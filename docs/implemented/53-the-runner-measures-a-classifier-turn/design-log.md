# Design Log

## Concerns

Grilled (2026-10-14): grill-design

| Concern             | Verdict | Why                                                                                                                                                  |
|---------------------|---------|------------------------------------------------------------------------------------------------------------------------------------------------------|
| Failure modes       | finding | a failed scrape already leaves its service's values null (`TargetMetrics.java`); an unreadable mode changes no verdict (DF08)                        |
| Idempotency & retry | clear   | a rerun writes a new run file; nothing is keyed or stored beyond `build/eval`                                                                        |
| Concurrency         | clear   | attempts run one after another against one connector process (`CorpusRun.java`), so a window sees only the target turn                               |
| Recovery            | clear   | one effect per attempt, the run file rewritten whole (`RunOutput.java`)                                                                              |
| Data                | finding | an absent series sums to zero (`MetricSnapshot.java`), so a first turn and AC08's zero both read right; the run's mode sits in the summary (DF07)    |
| Contract compat     | finding | new fields are additive under `schemaVersion` 1; no check status changes (DN01 — 7-004 drops its `mcpCalls`); an older run file has no mode (DF04) |
| Lifecycle           | clear   | run files stay in `build/eval` as today; no new state                                                                                                |
| Authorization       | clear   | reads the management ports and console file the runner already reads                                                                                 |
| Security            | clear   | new inputs are scrape label values written into JSON; no new secret                                                                                  |
| Observability       | finding | a failed classifier call is not split out (DF03)                                                                                                     |
| Limits              | clear   | the same two scrapes per attempt; `eval-runner/docs/conventions/testing.md` measures no performance                                                  |
| Business invariants | finding | one process runs one mode (`EXTRACTION_MODE` fixed at start), so mixed means the stack changed mid-run; `9-011` is never measured (DF05)             |
| Stack-neutral       | pass    | meter names and `EXTRACTION_MODE` are wire and configuration contracts (`ai-connector-service/docs/operations.md`); no class in the spec or artifact |

## Findings

| #    | Question                                                              | Answer                                                                                                                                                                              | Evidence                                                                |
|------|-----------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------|
| DF01 | Where is a classifier turn's ledger call count taken from? (was DN02) | `ledger_mcp_tool_calls_total` growth plus `grpc_server_seconds_count{rpc_service="bot.finance.ledger.v1.LedgerTurnService"}` growth, inside the runner; tools-mode values unchanged | `ledger-service/docs/contracts/in/operations.md`; `TargetMetrics.java`  |
| DF02 | Do two runs in different modes still compare? (was DN03)              | yes, flagged as differing (AC13)                                                                                                                                                    | `docs/eval/artifacts-and-baselines.md`, configuration differences       |
| DF03 | Does the classifier-call count split a failed call?                   | deferred: every outcome counts as one call; back when a run must explain catch-all filings                                                                                          | `ClassifierTurn.java` timing a failed call                              |
| DF04 | What does compare show for a run without a mode?                      | `n/a`, no difference flagged (AC18)                                                                                                                                                 | `docs/eval/artifacts-and-baselines.md`, missing data; `Comparison.java` |
| DF05 | Is `9-011`'s `maximumCount` measured in either mode?                  | no: the case is blocked in every run today                                                                                                                                          | `CaseSelection.java`; `CaseExecutor.java`                               |
| DF06 | Do classifier calls reach the summary and the comparison?             | deferred: per attempt only; back when a comparison must split classifier time from model time                                                                                       | `PerformanceSummary.java`; `Comparison.java`                            |
| DF07 | Where does the run's mode live in the run file?                       | in `summary`, derived from the attempts                                                                                                                                             | `Summary.java`; `Comparison.java` reads only `summary`                  |
| DF08 | What verdict does an unreadable mode give?                            | none of its own: no check reads the mode once DN01 leaves no scoped expectation                                                                                                     | `docs/eval/scoring.md`; `Oracle.java`                                   |
| DF09 | Does the ledger scrape carry the gRPC timer?                          | deferred: taken from its contract page, unproven by any test; a missing series reads zero, not null; settled by a ledger scrape test naming it                                      | `ledger-service/docs/contracts/in/operations.md`                        |
| DF10 | Does BT08 still change an evaluation case?                            | no: its line about the case expecting a ledger-side `list_categories` call goes stale once 7-004 drops it                                                                           | `docs/backlog.md`, BT08                                                 |

## Decision Bases

- **DN01:** The user chose dropping 7-004's `mcpCalls` over a per-expectation mode scope; the scope would be a case
  schema change with 7-004 its only user (`docs/eval/cases.md`), and BT08 removes the ledger-side
  `list_categories` call from tools mode too (`docs/backlog.md`).
- **DN04:** The user chose leaving a scoped-out check out of `checks` over a status of its own; a new status would
  change the documented status list (`docs/eval/artifacts-and-baselines.md`) and `not-evaluated` already means
  missing evidence (`docs/eval/scoring.md`). Moot under DN01.
