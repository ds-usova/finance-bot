# [Conventions](../conventions.md) > Agent Configuration

How the coding agent commits, parallelizes work, and where its planning artifacts live.

## Version Control

- Commit incrementally: yes — the agent commits intermediate changes as it goes, on whichever branch is
  currently checked out.
- Granularity: one commit per passed stage guardrail (stabilization, red, green, refactor, wrap-up) — not per
  step or per wave.
- Branch policy: the developer creates and checks out the branch manually before work starts; the agent never
  creates, switches, or deletes branches — it commits to the current branch only.
- Message format: `<Prefix>: <description>` — the description states exactly what was done, and the prefix names
  the kind of change:
  - `Feature` — new or extended functionality;
  - `Bug` — a fix for incorrect behavior;
  - `Configuration` — build, infrastructure, or application configuration;
  - `Test` — changes to test code only, such as tests written ahead of their implementation;
  - `Refactor` — behavior-preserving cleanup and restructuring.
  - `Documentation` — updates to documentation.

  Add new prefixes as new kinds of change show up. The format applies from this point in the history onward;
  earlier commits predate it.
- Squash before merging: do not squash, do not merge the changes into the main branch.

## Parallelism

Concurrent test runs share one Gradle daemon, one Docker daemon, and this machine's RAM; each concurrent
`gradlew test` invocation starts its **own** Postgres container and WireMock server (they are JVM singletons, not
machine singletons).

- Max concurrent test runs: **4** — container-starting suites (system and outbound-adapter) included.
- Max concurrent build/implementation tasks: **4**.
- Notes: treat unexplained container-startup or port-binding failures during parallel runs as contention — rerun
  serially before debugging them as real failures.
- Concurrent `gradlew test` invocations also share one `ledger-service/build/` directory, so they clobber each
  other's results in `build/test-results/test/`. The symptom is a Gradle-level
  `NoSuchFileException: …/binary/in-progress-results-*.bin`, or another run's XML appearing where yours should be,
  often *after* the tests themselves have reported. Console pass/fail stays trustworthy; the XML and HTML reports
  do not. Judge a parallel run by its console output, and rerun serially whenever the reports matter.

## Plan Files

- Location: repo-root `docs/`; completed plans are archived to `docs/implemented/` (see
  [Documentation References](orientation.md#documentation-references)).
