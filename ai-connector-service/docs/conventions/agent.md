# [Conventions](../conventions.md) > Agent Configuration

How the coding agent commits, parallelizes work, and where its planning artifacts live.

## Version Control

- Commit incrementally: yes — the agent commits intermediate changes as it goes, on whichever branch is
  currently checked out.
- Granularity: one commit per passed stage guardrail (stabilization, red, green, refactor, wrap-up) — not per
  step or per wave.
- Branch policy: the developer creates and checks out the branch manually before work starts; the agent never
  creates, switches, or deletes branches — it commits to the current branch only.
- Message format: `<Prefix>: <description>` — one subject line stating what the change does, and a prefix
  naming the kind of change:
  - `Feature` — new or extended functionality;
  - `Bug` — a fix for incorrect behavior;
  - `Configuration` — build, infrastructure, or application configuration;
  - `Test` — changes to test code only, such as tests written ahead of their implementation;
  - `Refactor` — behavior-preserving cleanup and restructuring.
  - `Documentation` — updates to documentation.

  Add new prefixes as new kinds of change show up.
- Message body: usually none — the subject carries the change and the diff carries the detail. Add a few lines
  only for what the diff cannot show: a constraint that forced the approach, or a consequence a later reader
  would otherwise miss. Never a list of touched files or a per-file summary (that is `git show --stat`), never
  a test count or suite status (not a property of the commit), never a restatement of the subject.
- Squash before merging: do not squash, do not merge the changes into the main branch.

## Sub-Agent Models

Delegated work splits by how much judgment it needs, and each kind runs on the model that matches:

- **Deciding work — the strongest model** (`opus`): planning a task, reviewing a plan, and the refactor pass
  over a finished diff. These choose what gets built and judge finished work against the conventions, so a weak
  call here is inherited by every step downstream and costs far more than the model does.
- **Executing work — a cheaper model** (`sonnet`): the stabilization agent and every red- and green-phase step
  agent. Such an agent is handed one target class, its scenarios, and the conventions, and writes code against
  them — the decisions were made in the plan, and the stage guardrails catch what it gets wrong.
- The orchestrator, and anything not listed above, runs on whatever model the session runs on.

The model name is the `model` parameter of the agent-spawning tool.

## Parallelism

Concurrent runs share the Gradle caches, `build/classes`, and this machine's memory — memory being the scarce
one. Nothing competes for a fixed port. Results cannot collide: the runner in [Build](build.md) gives every run
its own directory, and its queue protects the shared build directory.

- Max concurrent implementation agents: **4**.
- Concurrent test runs: effectively **1 per module**. Agents may all ask at once; the queue serializes them.
  It does not span modules, so a run here can overlap one in a sibling service.
- The suite runs in-process, so an unexplained failure is a real failure — rerunning is not a diagnosis. The
  first build after a clean checkout downloads the `protoc` toolchain and needs network access.

Parallel agents share the working tree. An agent stays inside the files its step owns, and never draws
conclusions from a file another agent is writing: a broken compile or failing test in someone else's target is
their work in progress, not a finding. Reconciling across steps is the orchestrator's job.

An agent never edits `build/generated/`. A missing generated type means the `.proto` has not been written —
a stabilization gap to report, not something to hand-write around.

## Plan Files

- Location: a task directory under repo-root `docs/` — `docs/<n>-<task-name>/`, holding `design.md` and the
  `plan.md` written from it. A completed task's whole directory is archived to `docs/implemented/` (see
  [Documentation References](orientation.md#documentation-references)).

## Post-Implementation Actions

What runs once a plan is finished — every item ticked, the guardrail green, the plan file moved to
`docs/implemented/`. A run that ends with anything open runs none of them.

1. `tools/plan-evidence/plan-evidence.sh --plan <the archived plan>`: measures every module and writes
   `evidence.md` and `evidence.json` into the archived plan's directory — see
   [Evidence for a Finished Plan](../../../docs/conventions/java-build.md#evidence-for-a-finished-plan). It runs
   first, so the commit it records is the one that closed the plan. Commit its output as
   `Documentation: <task> implementation evidence`. A non-zero exit means the plan is not finished: report the
   verdict rather than continuing down this list.
2. `archive-knowledge`, given the archived plan: a document per usecase class with its collaborators on both
   sides, the contracts with the systems around the service, and an ADR for each decision the code cannot
   explain by itself. Commits its own output.
