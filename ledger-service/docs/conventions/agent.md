# [Conventions](../conventions.md) > Agent Configuration

How the coding agent commits, parallelizes work, and where its planning artifacts live.

## Version Control

- Commit incrementally: yes — the agent commits intermediate changes as it goes, on whichever branch is
  currently checked out.
- Granularity: one commit per passed stage guardrail (stabilization, red, green, refactor, wrap-up) — not per
  step or per wave.
- Branch policy: the developer creates and checks out the branch manually before work starts; the agent never
  creates, switches, or deletes branches — it commits to the current branch only.
- Message format: `<Prefix>: <description>` — one subject line stating what the change does, and a prefix naming
  the kind of change:
  - `Feature` — new or extended functionality;
  - `Bug` — a fix for incorrect behavior;
  - `Configuration` — build, infrastructure, or application configuration;
  - `Test` — changes to test code only, such as tests written ahead of their implementation;
  - `Refactor` — behavior-preserving cleanup and restructuring.
  - `Documentation` — updates to documentation.

  Add new prefixes as new kinds of change show up. The format applies from this point in the history onward;
  earlier commits predate it.
- Message body: usually none — the subject carries the change and the diff carries the detail. Add a few lines
  only for what the diff cannot show: a constraint that forced the approach, or a consequence a later reader
  would otherwise miss. Never a list of touched files or a per-file summary (that is `git show --stat`), never
  a test count or suite status (not a property of the commit), never a restatement of the subject.
- Squash before merging: do not squash, do not merge the changes into the main branch.

## Sub-Agent Models

Delegated work splits by how much judgment it needs, and each kind runs on the model that matches:

- **Deciding work — the strongest model** (`opus`): planning a task, reviewing a plan, and the refactor pass over
  a finished diff. These choose what gets built and judge finished work against the conventions, so a weak call
  here is inherited by every step downstream and costs far more than the model does.
- **Executing work — a cheaper model** (`sonnet`): the stabilization agent and every red- and green-phase step
  agent. Such an agent is handed one target class, its scenarios, and the conventions, and writes code against
  them — the decisions were made in the plan, and the stage guardrails catch what it gets wrong.
- The orchestrator, and anything not listed above, runs on whatever model the session runs on.

The model name is the `model` parameter of the agent-spawning tool.

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
