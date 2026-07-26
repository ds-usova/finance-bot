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

Concurrent runs share the Gradle caches, the `ledger-service/build/classes` they compile into, one Docker daemon,
and this machine's memory; each concurrent test run starts its **own** Postgres container and WireMock server (they
are JVM singletons, not machine singletons). Host ports are not the scarce resource — Postgres comes up on a random
port through Testcontainers, WireMock uses a dynamic one, and the application binds `@LocalServerPort` — memory is.
Results cannot collide, since the runner in [Build](build.md) gives every run a directory of its own; what the
runner's queue protects is everything else on that list, so one run proceeds and the rest wait. Compiling waits in
the same queue, because it writes to the same `build/classes`.

- Max concurrent implementation agents: **4**.
- Concurrent test runs: effectively **1** — the queue is the wrapper's, not something an agent arranges. Four
  agents may each ask for a run at the same time; they simply finish one after another.
- Notes: treat an unexplained container-startup failure as memory pressure and rerun before debugging it as a real
  failure. Never work around a wait by bypassing the queue.

Parallel agents also share the working tree. An agent stays inside the files its step owns: it never edits, and
never draws conclusions from, a file another agent is currently writing — a file that does not compile or a test
that fails inside someone else's target is their work in progress, not a finding. Reconciling across steps is the
orchestrator's job, after the wave.

## Plan Files

- Location: repo-root `docs/`; completed plans are archived to `docs/implemented/` (see
  [Documentation References](orientation.md#documentation-references)).
