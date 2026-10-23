# Execution Policy

- Cases run sequentially so aggregate meter deltas remain attributable.
- The runner waits for a terminal written-message outcome before starting the next case.
- Every case has a timeout and records an error instead of blocking the run.
- Cases declaring `executionLimits` require call-budget enforcement before provider or MCP dispatch in the AI Connector.
  A limit violation stops further calls and records a case error; cleanup still runs. Budgets apply to the target turn,
  including retries. If enforcement is unavailable, the runner records a setup error and does not file the message.
  Checking traces after dispatch is not budget enforcement.
- Case teardown runs after pass, fail, error, or cancellation.
- The next case starts only after fixture restoration is verified.
- If restoration fails, record a teardown error and rebuild the ephemeral environment before continuing.
- Memory readiness is polled until its timeout; expiry records a case error and the target message is not filed.
- Calibration runs use a provisional memory-readiness timeout.
- Approved runs use twice the mean learning-availability duration measured by calibration.
- The effective memory-readiness timeout is recorded in the run manifest.
- Case order is stable by default. A shuffled order is recorded in the run manifest.
- An approved baseline run executes each case three times.
- A full baseline run has no separate monetary budget; its scope is bounded by the corpus, repetitions, and case timeouts.
- A full-run wall-clock limit is not set before calibration. Initial runs record total duration; the limit is chosen from
  those measurements after the evaluator exists.
- A warm-up case may be excluded from latency statistics. Its result remains visible in the artifact.
- A cancelled run keeps completed case records and is marked incomplete.
- The ordinary build never calls a paid provider.
- An evaluation is started locally through an explicit command.
- CI does not run the evaluation, either on a schedule or as a build gate.
- Every run satisfies [Environment and fixtures](environment-and-fixtures.md).

The same case and artifact formats apply to a selected subset and to the complete corpus.
