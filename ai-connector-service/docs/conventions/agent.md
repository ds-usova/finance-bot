# [Conventions](../conventions.md) > Agent Configuration

How the coding agent parallelizes work on this module, and which model does what.

## Version Control

Repository-wide, since every module shares one history: [Version Control](../../../docs/conventions/version-control.md).

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

What the machine allows across every module at once is repository-wide:
[Parallelism](../../../docs/conventions/parallelism.md). Below is this module only.

Concurrent runs share the Gradle caches, `build/classes`, and this machine's memory — memory being the scarce
one. Nothing competes for a fixed port. Results cannot collide: the runner in [Build](build.md) gives every run
its own directory, and its queue protects the shared build directory.

- Max concurrent implementation agents: **4**.
- Concurrent test runs: effectively **1**. Agents may all ask at once; the queue serializes them. The queue does
  not span modules, which is why the repository-wide file limits how many pipelines run side by side.
- The suite runs in-process, so an unexplained failure is a real failure — rerunning is not a diagnosis. The
  first build after a clean checkout downloads the `protoc` toolchain and needs network access.

Parallel agents share the working tree. An agent stays inside the files its step owns, and never draws
conclusions from a file another agent is writing: a broken compile or failing test in someone else's target is
their work in progress, not a finding. Reconciling across steps is the orchestrator's job.

An agent never edits `build/generated/`. A missing generated type means the `.proto` has not been written —
a stabilization gap to report, not something to hand-write around.

## Follow-Up Work in a Plan

What runs once a change is complete, and what it earns, is [Follow-Up Work](follow-up.md) — true of this service
however the work was done, so it does not live here.

A plan carries one of those kinds in its **Post-Implementation Steps** group:

- **ADRs** — one item per approved decision, as `Write ADR: <the decision, stated as a fact>`.

The approval is a numbered open question in the plan, and only an answered `yes` becomes an item. Nothing
downstream writes an ADR that has no item.
