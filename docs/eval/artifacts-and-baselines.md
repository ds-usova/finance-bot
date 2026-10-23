# Artifacts and Baselines

Every run writes immutable machine-readable data and a generated human-readable comparison.

```text
build/ai-evaluation/<run-id>/
    manifest.json
    cases.jsonl
    traces.jsonl
    summary.json
    report.md
```

| Artifact        | Contents                                                                                      |
|-----------------|-----------------------------------------------------------------------------------------------|
| `manifest.json` | run configuration and reproducibility metadata                                                |
| `cases.jsonl`   | application result and verdict for each case repetition                                       |
| `traces.jsonl`  | correlated spans, calls, parameters, payloads, timings, and statuses for each case repetition |
| `summary.json`  | aggregate quality and performance statistics                                                  |
| `report.md`     | verdict, regressions, improvements, and configuration differences                             |

Each date-sensitive case record carries the UTC date used to calculate its expected outcome.

## Record Conventions

| Rule                | Requirement                                                                                           |
|---------------------|-------------------------------------------------------------------------------------------------------|
| Version             | `manifest.json`, `summary.json`, and every `cases.jsonl` record carry `schemaVersion`                 |
| Identity            | results use `runId`, `caseId`, `repetition`, and `attempt`; repetition and attempt numbers start at 1 |
| Missing data        | use `null`, not fabricated values; empty lists mean collection completed with no entries              |
| Dates and durations | timestamps are UTC ISO-8601; durations are milliseconds measured with a monotonic clock               |
| Trace format        | owned by [Observability](observability.md#trace-contract), not duplicated here                        |

## Case Attempt Record — `cases.jsonl`

One JSON line per executed attempt, including attempts replaced after UTC rollover. Ordinary execution has one
attempt per repetition. Blocked cases are listed in the summary without executing an attempt.

| Required field                                                        | Contents                                                                                                                                                                                |
|-----------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `schemaVersion`, `runId`, `caseId`, `covers`, `repetition`, `attempt` | format version and attribution                                                                                                                                                          |
| `attemptStatus`                                                       | `completed`, `discarded-date-rollover`, or `cancelled`                                                                                                                                  |
| `startedAt`, `finishedAt`, `expectedDate`                             | attempt timestamps and captured UTC date used for date expectations; date is null when unused                                                                                           |
| `messageIds`                                                          | target message ID and learning IDs mapped to their application message IDs; unavailable IDs are null                                                                                    |
| `expected`                                                            | snapshot of the case's declared expectations with date tokens resolved                                                                                                                  |
| `actual`                                                              | terminal outcome, REST target proposals with IDs and resolved catalogue names, and declared MCP expectation evidence; unavailable collections are null                                  |
| `checks`                                                              | list of checks with `path`, `expected`, `actual`, and `status` (`pass`, `fail`, `not-evaluated`); proposal checks identify the expected item index and matched actual ID when available |
| `matching`                                                            | expected, actual, and matched proposal counts; matched item-index/proposal-ID pairs for constrained cases; unmatched expected indexes and actual IDs                                    |
| `verdict`                                                             | `pass`, `fail`, or `error`; null for a successfully cleaned-up discarded rollover attempt                                                                                               |
| `qualityMetrics`                                                      | precision, recall, and F1 for this attempt; null values for undefined or excluded metrics                                                                                               |
| `measurements`                                                        | raw timings, provider/MCP counts, usage, and learning availability with message/phase attribution as defined by Observability; missing measurements are null                            |
| `errors`                                                              | list of failures, each with `stage`, `component`, `type`, `message`, and nullable protocol status                                                                                       |
| `teardown`                                                            | separate evidence-collection, cleanup, and fixture-restoration statuses (`succeeded`, `failed`, `not-completed`) with references to corresponding errors                                |
| `traceIds`                                                            | available correlated trace IDs; sanitized payloads stay in `traces.jsonl`                                                                                                               |

Count-only matching records the three counts; item-level pairs and unmatched-item lists are null because the case
does not declare expected items. A missing proposal collection is null, not an empty list or an actual count of zero.

### Failure and Retry Records

| Situation                 | Recording rule                                                                                                                                                                                  |
|---------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| failed expectation        | retain the failing check's expected and actual values; normal completed evaluation has verdict `fail`                                                                                           |
| setup or processing error | retain partial evidence, record the error stage, and mark unavailable checks `not-evaluated`                                                                                                    |
| teardown error            | final verdict is `error`; retain any earlier failed checks and all teardown failures rather than overwriting them                                                                               |
| UTC rollover              | retain the original and finishing UTC dates in `rollover`; mark the cleaned-up attempt discarded and exclude its quality metrics; repeat the same repetition with an incremented attempt number |
| rollover cleanup failure  | record verdict `error`, not a successfully discarded attempt; apply the environment-rebuild rule before further execution                                                                       |
| cancellation              | retain partial results, attempt teardown, and mark the attempt cancelled and run incomplete                                                                                                     |

## Summary Record — `summary.json`

| Required field                         | Contents                                                                                                                                                            |
|----------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `schemaVersion`, `runId`, `baselineId` | format version, run identity, and comparison baseline ID or null                                                                                                    |
| `status`                               | `complete`, `incomplete`, or `cancelled`                                                                                                                            |
| `execution`                            | selected case IDs, requested repetitions, executed attempts, discarded rollover attempts, and unfinished repetitions                                                |
| `blockedCases`                         | case IDs and reasons, excluded from quality denominators                                                                                                            |
| `cases`                                | one entry per case: repetition verdicts, pass/fail/error counts, mean quality metrics and included-value counts, and performance statistics                         |
| `quality`                              | run means with eligible-case counts, pass/error rates with denominators, false-positive rate with denominator, outcome confusion matrix, and pass rates by `covers` |
| `performance`                          | total run duration and per-case timing/call/token statistics, each with unit and sample count                                                                       |
| `comparison`                           | baseline compatibility/configuration differences and per-case metric/verdict/performance deltas; null without a selected baseline                                   |

All statistics must be reproducible from the manifest and case records. Quality calculations, including excluded
attempts and averaging, follow [Oracle and scoring](scoring.md); `report.md` only presents these stored results.

## Reproducibility Metadata

`manifest.json` records:

- run id, start time, and runner version;
- corpus version and content hash;
- fixture version, the default catalogue received by the evaluation user, and confirmation that the user had no learned
  history;
- application git revision and whether the worktree is dirty;
- AI provider, chat model, embedding model, temperature, and relevant timeouts, including memory readiness;
- hashes of the system prompt and user-message template;
- repetition count, case order, concurrency, and warm-up policy;
- host and service versions needed to explain latency;
- redacted configuration sufficient to reproduce the run.

Secrets, bearer tokens, credentials, production data, and data unrelated to the evaluation never enter an
artifact. Stored request and response payloads are redacted before they are written.

## Baseline Comparison

An approved baseline contains the metadata, summary, and per-case results needed for comparison. Large provider
responses and transient service logs remain unapproved build artifacts. Updating a baseline is an explicit
review action. Running the suite never updates it automatically.

Approved baselines are versioned in the repository beside the corpus. Baseline changes therefore use the same
review and change history as the cases they describe.

### Approved Baseline Files

```text
docs/eval/baselines/<baseline-id>/
    manifest.json
    cases.jsonl
    summary.json
```

| File            | Approval contents                                                                                            |
|-----------------|--------------------------------------------------------------------------------------------------------------|
| `manifest.json` | source run metadata plus `baselineId` and `approvedAt`                                                       |
| `cases.jsonl`   | all sanitized attempt records from the source run, including errors and discarded attempts                   |
| `summary.json`  | source run summary; selection and repetition counts remain explicit                                          |
| Excluded        | `traces.jsonl`, provider payloads, service logs, and generated `report.md`; these remain local run artifacts |

Approval copies these files explicitly; it does not edit case expectations or remove failing results. Trace-ID
references may point to local-only evidence and are not required to calculate baseline comparisons.

The report separates:

- quality regressions from stochastic variation;
- latency and token changes from functional failures;
- model or configuration changes from application-code changes;
- infrastructure errors from model errors.

Token usage remains in provider-reported input and output token counts. The evaluation does not convert tokens
to monetary cost.
