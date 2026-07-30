# [Conventions](../conventions.md) > Agent Configuration

How the coding agent commits, parallelizes work, and where its planning artifacts live.

## Version Control

- Commit incrementally: yes — the agent commits intermediate changes as it goes, on whichever branch is
  currently checked out.
- Granularity: one commit per passed stage guardrail (stabilization, red, green, refactor, wrap-up) — not per
  step or per wave.
- Branch policy: the developer creates and checks out the branch manually before work starts; the agent never
  creates, switches, or deletes branches — it commits to the current branch only. `main` is a normal choice of
  current branch: development happens there, so a run that starts on it commits to it and asks nothing.
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

Concurrent runs share the Gradle caches, `build/classes`, one Docker daemon and this machine's memory — memory
being the scarce one. Each concurrent test run starts its **own** Postgres container and WireMock server (JVM
singletons, not machine singletons), both on random ports, so ports are not contended. Results cannot collide:
the runner in [Build](build.md) gives every run its own directory, and its queue protects everything else on
that list.

- Max concurrent implementation agents: **4**.
- Concurrent test runs: effectively **1**. Agents may all ask at once; the queue serializes them. Compiling
  queues too, writing to the same `build/classes`.
- Treat an unexplained container-startup failure as memory pressure and rerun before debugging it as real.
  Never bypass the queue.

Parallel agents share the working tree. An agent stays inside the files its step owns, and never draws
conclusions from a file another agent is writing: a broken compile or failing test in someone else's target is
their work in progress, not a finding. Reconciling across steps is the orchestrator's job.

## Plan Files

- Location: repo-root `docs/`; completed plans are archived to `docs/implemented/` (see
  [Documentation References](orientation.md#documentation-references)).

## Post-Implementation Actions

What runs once a plan is finished — every item ticked, the guardrail green, the plan file moved to
`docs/implemented/`. A run that ends with anything open runs none of them.

1. `archive-knowledge`, given the archived plan: a document per usecase class with its collaborators on both
   sides, the contracts with the systems around the service, and the ADRs the plan was authorized to record.
   Commits its own output.

## Post-Implementation Plan Sections

What a plan lists under **Post-Implementation Steps**, and nothing else:

- **ADRs** — one item per decision the developer approved for recording, as `Write ADR: <the decision, stated as a
  fact>`. The number is not chosen in the plan; it is assigned when the ADR is written, so a rejected candidate
  consumes none.

**An ADR exists only because it was approved.** The plan raises each candidate as a numbered open question — the
decision as a fact, and the page that holds it if no ADR is written — and only an answered `yes` becomes an item
above. A candidate rejected, or never asked, means no ADR: nothing downstream writes one that has no item, and a
decision discovered too late for the plan is proposed in the archiving report instead of appearing as a fact
nobody agreed to.

Screen candidates before asking, so the list is one or none rather than every decision the plan made: a rule
statable without naming a technology, a file layout, or a type is product behaviour, and the use-case, contract,
or domain page that owns it is the whole answer.
