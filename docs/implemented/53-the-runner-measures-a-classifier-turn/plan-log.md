# Plan Log: The Runner Measures a Classifier Turn

## Review Findings

- **RF01:** AC06, an unscoped expectation holding in both modes, had no step and no coverage note.
  - Resolution: decision
  - Action: resolved — held by the existing `OracleTest.mcpCountsSeparateAttemptedFromAcceptedCalls`, whose
    `count: 0` expectation passes with no such call, and no check reads the mode; named in the coverage note.

- **RF02:** PI01 changed a `usage.ledgerToolCalls` README row that does not exist.
  - Resolution: mechanical
  - Action: applied — PI01 adds the row and updates the **What It Needs Running** metrics rows.

- **RF03:** `Summary.of()` putting `extractionMode` had no test.
  - Resolution: decision
  - Action: resolved — a one-line delegation, which `step-formats.md`'s simple-delegation exclusion places in
    stabilization; stated in the coverage note.

- **RF04:** ST04 did not say whether `RunMode.of` reads every attempt or only `counted`.
  - Resolution: decision
  - Action: resolved — every recorded attempt: the spec's AC10–AC12 name "recorded attempts", and
    `RunOutput.record` records discarded rollovers too; ST04 says so.

- **RF05:** No test pins 7-004 to producing no `list_categories` check.
  - Resolution: decision
  - Action: resolved — the coverage note stands: `Oracle.java` checks only declared tools, so a new test would pass
    on arrival and could not be red.

- **RF06:** The architecture diagram used undirected relations, no `new` tag, and drew dependencies the code does
  not have.
  - Resolution: mechanical
  - Action: applied — directed relations, `new` tag on the run-mode responsibility, `runMode → window` removed.

- **RF07:** `docs/eval/implementation/demo-plan.md` and `docs/eval/cases.md` go stale once 7-004 drops its
  `mcpCalls`.
  - Resolution: decision
  - Action: Add a PI03 for both (Recommended) — added PI03.

## Run Log

- **RL01 (ST03):** the stubs' intent comments in `MetricSnapshot.labelValues` and `RunMode.of` were written
  without the `stub-intent:` marker; re-delegated once to add it.
- **RL02 (RU01):** expected passes against the stub, which returns an empty set:
  `MetricSnapshotTest.aNameWithNoSeriesHasNoLabelValues` and `aSeriesWithoutTheLabelContributesNoValue` assert an
  empty set.
- **RL03 (RU02):** expected passes: `TargetMetricsTest.hasNoExtractionModeWhenNoTurnCounted` and
  `hasNoExtractionModeWhenBothModesGrow` assert a null mode, which `Window.close()` passes today;
  `countsTheLedgersToolCallsWhenItHasNoRpcMeter` holds the existing `ledger_mcp_tool_calls_total` growth;
  `aFailedConnectorScrapeNullsTheConnectorValuesAndIsReported` holds the existing null-and-report on a failed
  connector scrape.
- **RL04 (RU03):** expected passes against the stub, which returns null:
  `RunModeTest.returnsNullWhereNoAttemptRecordedAMode` and `returnsNullForNoAttempts`.
- **RL05 (PI03):** hypothesis: the `docs/eval/implementation/demo-plan.md` Constraints row's effect cell ("a change
  that removes the call fails these cases") is wrong if the `9-007`–`9-011` cases expect `list_categories` with
  `count: 0`, as the spec describes them — settled by reading those cases' `expected.mcpCalls`.
  - Resolved: measured at Phase 3 — `9-007`–`9-010` hold `count: 0` and `9-011` `maximumCount: 2`, so none fails
    when the call is removed; the row became wrong when PI03 dropped 7-004, and was corrected in place.

## Suite Runs

- baseline · tree e325faf7b94f4d4ad98442ca3b0a0f38564768fa · total 21 · skipped 0 · green · paths: eval-runner
- stabilization · tree 2936ff2b2fcf43f5083dbdae9be8890607906e63 · total 21 · skipped 0 · green · paths: eval-runner

- green · tree a2c35851dcc49d73baaa9eee376b01f23298517f · total 42 · skipped 0 · green · paths: eval-runner
- refactor · tree 1fcff5f0f9d9cab38ba8ef1080dee396383bd112 · total 42 · skipped 0 · green · paths: eval-runner
- final · tree 84a967e50f8d618d22dfae7e861c6a10647e4b11 · total 42 · skipped 0 · green · paths: eval-runner
## Stubs

Marker: `stub-intent:`

- `eval-runner/src/main/java/bot/finance/eval/observe/MetricSnapshot.java`
- `eval-runner/src/main/java/bot/finance/eval/report/RunMode.java`
