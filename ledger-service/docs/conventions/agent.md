# [Conventions](../conventions.md) > Agent Configuration

How the coding agent commits, parallelizes work, and where its planning artifacts live.

## Version Control

- Commit incrementally: yes — the agent commits intermediate changes as it goes, on whichever branch is
  currently checked out.
- Granularity: one commit per passed stage guardrail (stabilization, red, green, refactor, wrap-up) — not per
  step or per wave.
- Branch policy: the developer creates and checks out the branch manually before work starts; the agent never
  creates, switches, or deletes branches — it commits to the current branch only.
- Message format: `<Prefix>: <description>` — prefix is one of `Bug`, `Feature`, `Configuration`, `Test`,
  `Refactor` (add new prefixes as new kinds of change show up); the description states exactly what was done.
  Stage commits map naturally: red phase → `Test`, refactor stage → `Refactor`, the other stages → whichever
  prefix fits the change. The format applies from here on; history predating it does not follow it.
- Squash before merging: do not squash, do not merge the changes into the main branch.

## Parallelism

Concurrent test runs share one Gradle daemon, one Docker daemon, and this machine's RAM; each concurrent
`gradlew test` invocation starts its **own** Postgres container and WireMock server (they are JVM singletons, not
machine singletons).

- Max concurrent test runs: **4** — container-starting suites (system and outbound-adapter) included.
- Max concurrent build/implementation tasks: **4**.
- Notes: treat unexplained container-startup or port-binding failures during parallel runs as contention — rerun
  serially before debugging them as real failures.

## Plan Files

- Location: repo-root `docs/`; completed plans are archived to `docs/implemented/` (see
  [Documentation References](orientation.md#documentation-references)).
