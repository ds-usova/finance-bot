docs/53-the-runner-measures-a-classifier-turn · spec.md: 17 scenarios

| ID   | Verdict | Evidence |
|------|---------|----------|
| AC01 | covered | RU01 `MetricSnapshotTest` (eval-runner/src/test/java/bot/finance/eval/observe/MetricSnapshotTest.java) · RU02 `TargetMetricsTest` (eval-runner/src/test/java/bot/finance/eval/observe/TargetMetricsTest.java) |
| AC02 | covered | RU01 `MetricSnapshotTest` (eval-runner/src/test/java/bot/finance/eval/observe/MetricSnapshotTest.java) · RU02 `TargetMetricsTest` (eval-runner/src/test/java/bot/finance/eval/observe/TargetMetricsTest.java) |
| AC14 | covered | RU02 `TargetMetricsTest` (eval-runner/src/test/java/bot/finance/eval/observe/TargetMetricsTest.java) |
| AC19 | held    | AC19 and AC20 are held by ST01 and the existing `Oracle`, which checks only the tools a case declares under `mcpCalls` |
| AC20 | held    | AC19 and AC20 are held by ST01 and the existing `Oracle`, which checks only the tools a case declares under `mcpCalls` |
| AC06 | held    | AC06 is held by the existing `OracleTest.mcpCountsSeparateAttemptedFromAcceptedCalls`: a `count: 0` expectation passes with no such call, and no check reads the mode. `Summary.of()` putting `RunMode.of()` under `extractionMode` is a one-line delegation, made in ST04. |
| AC07 | covered | RU02 `TargetMetricsTest` (eval-runner/src/test/java/bot/finance/eval/observe/TargetMetricsTest.java) |
| AC08 | covered | RU02 `TargetMetricsTest` (eval-runner/src/test/java/bot/finance/eval/observe/TargetMetricsTest.java) |
| AC09 | covered | RU02 `TargetMetricsTest` (eval-runner/src/test/java/bot/finance/eval/observe/TargetMetricsTest.java) |
| AC15 | covered | RU02 `TargetMetricsTest` (eval-runner/src/test/java/bot/finance/eval/observe/TargetMetricsTest.java) |
| AC16 | covered | RU02 `TargetMetricsTest` (eval-runner/src/test/java/bot/finance/eval/observe/TargetMetricsTest.java) |
| AC10 | covered | RU03 `RunModeTest` (eval-runner/src/test/java/bot/finance/eval/report/RunModeTest.java) |
| AC17 | covered | RU03 `RunModeTest` (eval-runner/src/test/java/bot/finance/eval/report/RunModeTest.java) |
| AC11 | covered | RU03 `RunModeTest` (eval-runner/src/test/java/bot/finance/eval/report/RunModeTest.java) |
| AC12 | covered | RU03 `RunModeTest` (eval-runner/src/test/java/bot/finance/eval/report/RunModeTest.java) |
| AC13 | covered | RU04 `ComparisonTest` (eval-runner/src/test/java/bot/finance/eval/report/ComparisonTest.java) |
| AC18 | covered | RU04 `ComparisonTest` (eval-runner/src/test/java/bot/finance/eval/report/ComparisonTest.java) |

every scenario is covered or held
