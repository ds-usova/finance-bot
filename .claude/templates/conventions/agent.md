# [Conventions](../conventions.md) > Agent Configuration

How a coding agent commits, parallelizes work, and where its planning artifacts live. Every agent-only fact
belongs here; the other section files describe the module and never mention agents.

## Version Control

If this section is silent, nothing is committed automatically.

- Commit incrementally: `<yes/no — e.g. "yes, after each passed stage guardrail", or "no — the developer commits">`
- Granularity: `<e.g. one commit per stage; one commit per logical unit of work>`
- Branch policy: `<e.g. the developer checks out the branch beforehand; the agent commits to the current branch and
  never creates, switches, or deletes one>`
- Message format: `<the format this repository's history already uses — e.g. Conventional Commits (feat:, fix:),
  or "<Prefix>: <description>" with the prefixes listed>`
- Message body: `<e.g. usually none — the subject carries the change and the diff carries the detail; never a file
  list or a test count>`
- Squash before merging: `<e.g. "no — keep the full history">`

## Sub-Agent Models

Which model each kind of delegated work runs on. Naming none means everything runs on the session's own model.

- Deciding work — `<model>`: `<what counts — e.g. planning, plan review, the refactor pass over a finished diff>`
- Executing work — `<model>`: `<what counts — e.g. stabilization, and every red- and green-phase step agent>`
- Everything else: the session's model.

## Parallelism

How many agents and test runs may run at once. Concurrent runs share build caches, a container runtime, and this
machine's memory; too many at once produces failures that look like broken tests and are resource contention. A
silent section means no known cap.

- Max concurrent implementation agents: `<e.g. 4>`
- Max concurrent test runs: `<e.g. 1 — a queue serializes them; or "unlimited">`
- Notes: `<e.g. treat an unexplained container-startup failure as memory pressure and rerun before debugging it;
  or "none">`

Parallel agents share one working tree: an agent stays inside the files its step owns, and never draws conclusions
from a file another agent is writing.

## Plan Files

- Location: `<where a task's design and plan live, and where a finished one is archived — e.g. docs/<n>-<task-name>/,
  archived to docs/implemented/>`
