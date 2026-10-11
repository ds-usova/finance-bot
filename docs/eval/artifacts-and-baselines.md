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

The report separates:

- quality regressions from stochastic variation;
- latency and token changes from functional failures;
- model or configuration changes from application-code changes;
- infrastructure errors from model errors.

Token usage remains in provider-reported input and output token counts. The evaluation does not convert tokens
to monetary cost.
