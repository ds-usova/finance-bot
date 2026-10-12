# Oracle and Scoring

The oracle compares the terminal written-message outcome and attributed proposals with the case's declared
constraints.

## Case Verdict

| Result | Meaning                                                                          |
|--------|----------------------------------------------------------------------------------|
| pass   | the message outcome, proposal count, and every declared field constraint match   |
| fail   | the run completes but observable application state does not match                |
| error  | setup, transport, provider, polling, inspection, or teardown prevents evaluation |

Proposal matching considers only constraints declared by the case. Undeclared and ignored fields do not affect
the verdict. Generated ids and timestamps never participate. When several proposals exist, each expected item
must match a different actual proposal.

If `items` is omitted, only the proposal count is checked. Declared proposal relations and MCP call expectations
also affect the verdict. Missing required trace evidence is an `error`, not a successful check.
Blocked cases are listed separately and excluded from verdict-rate denominators. Reports retain their IDs and
reasons so uncovered composition values remain visible.

`PARTIAL` and `FAILED` remain observable application outcomes, but no live-provider corpus case requires them as
its golden outcome. Either outcome makes the case an `error`, because it reports an execution failure rather than
the model's completed classification.

## Per-Repetition Proposal Metrics

Each repetition is compared independently with the same case's `expected`. Proposals from different repetitions
or cases are never combined for matching or metric calculation.

| Value | Definition |
|-------|------------|
| `E` | expected proposal count from the case YAML |
| `A` | actual target proposal count read through REST |
| `M`, count-only case | `min(E, A)`; no field accuracy is inferred |
| `M`, constrained case | maximum number of one-to-one matches satisfying every declared constraint of the expected item |

For constrained cases, `items` must contain exactly `E` entries. A proposal can match at most one expected item;
choose the matching with the largest number of matches, not the first available match. Undeclared and ignored
fields do not participate. Invalid case definitions are setup errors.

| Metric | Formula | Undefined when |
|--------|---------|----------------|
| precision | `M / A` | `A = 0` |
| recall | `M / E` | `E = 0` |
| F1 | `2 × M / (E + A)` | `E + A = 0` |

Undefined values are `N/A` (`null` in machine-readable artifacts), never zero or one. Repetitions with an `error`
verdict are excluded from quality metrics; their errors and available evidence remain visible. Proposal metrics
do not replace the verdict: message outcomes, proposal relations, and MCP expectations are checked separately.

## Per-Case Results

| Report item | Rule |
|-------------|------|
| repetitions | retain the verdict and metrics of each of the three repetitions separately |
| case precision, recall, F1 | arithmetic mean of that metric's valid repetition values |
| missing values | exclude errors and `N/A`; report the number of included values; no valid values means `N/A` |
| pass result | show pass/fail/error counts, e.g. one pass out of three repetitions |
| baseline comparison | compare this case's mean metrics and pass/fail/error counts with its baseline results |

## Run Summary

| Report item | Rule |
|-------------|------|
| precision, recall, F1 | arithmetic mean of the corresponding per-case means; each eligible case has equal weight |
| eligible cases | exclude `N/A` case means separately for each metric; report the included case count |
| pass rate | passed repetitions divided by completed non-error repetitions; also report error count and rate separately |
| pass rate per composition value | apply the same pass-rate rule to repetitions with that `covers` value |
| false-positive rate | among non-error repetitions expecting zero proposals, fraction producing at least one proposal |
| message-outcome confusion matrix | expected versus actual outcomes for non-error repetitions; execution errors listed separately |
| baseline differences | show per-case improvements and regressions in mean metrics and verdict counts |

No eligible observations means `N/A`. Run means average case metrics; they do not pool proposal counts across
cases or repetitions. Blocked cases and discarded UTC-rollover attempts do not contribute to quality statistics.

## Gate Policy

| Item              | Rule                                                              |
|-------------------|-------------------------------------------------------------------|
| Version 1 purpose | measure and approve the initial baseline                          |
| Output            | report scores and differences                                     |
| Quality threshold | none                                                              |
| Build impact      | evaluation results do not block the build                         |
| Future gate       | requires repeated-run evidence and an approved statistical policy |
