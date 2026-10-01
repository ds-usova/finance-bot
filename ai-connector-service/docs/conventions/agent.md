# [Conventions](../conventions.md) > Agent Configuration

How the coding agent parallelizes work on this module, and which model does what.

## Version Control

Repository-wide, since every module shares one history: [Version Control](../../../docs/conventions/version-control.md).
What the agent does with it:

- **Commit as the work goes**, once each check gating the change has passed, without being asked each time.
- **Never create, switch or delete a branch.** Commit to whatever is current, `main` included.
- **A commit refused because the repository index is locked is retried once**, after a moment. Any other refusal
  is reported, never repeated.

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

Concurrent runs share the Gradle caches, `build/classes`, one Docker daemon and this machine's memory — memory
being the scarce one. Each run starts its own containers on random ports, so nothing competes for a fixed port.
Results cannot collide: the runner in [Build](build.md) gives every run its own directory, and its queue
protects the shared build directory.

- Max concurrent implementation agents: **4**.
- Concurrent test runs: effectively **1**. Agents may all ask at once; the queue serializes them. The queue does
  not span modules, which is why the repository-wide file limits how many pipelines run side by side.
- Treat an unexplained container-startup failure as memory pressure and rerun before debugging it as real. The
  first build after a clean checkout downloads the `protoc` toolchain and needs network access.

Parallel agents share the working tree. An agent stays inside the files its step owns, and never draws
conclusions from a file another agent is writing: a broken compile or failing test in someone else's target is
their work in progress, not a finding. Reconciling across steps is the orchestrator's job.

An agent never edits `build/generated/`. A missing generated type means the `.proto` has not been written —
a stabilization gap to report, not something to hand-write around.

## Implementation Bundles

- Prefer items from the same: source grouping. A bundle never mixes test types.
- Max plan items per implementation agent: unlimited.
- Max plan brief characters per implementation agent: unlimited.

## Follow-Up Work in a Plan

What runs once a change is complete, and what it earns, is [Follow-Up Work](follow-up.md) — true of this service
however the work was done, so it does not live here.

A plan carries one of those kinds in its **Post-Implementation Steps** group:

- **ADR placeholders** — one item per approved decision, as `Place ADR: <the decision, stated as a fact>`.

**An agent places an ADR; it never writes one.** Placing means creating the file at the number, tier and
filename [Architecture Decision Records](../../../docs/conventions/adr.md) gives it, with the title, the date,
the source link, and each section left as that page's angle-bracket brief, `Status: Proposed`.

The approval is a numbered open question in the plan, and only an answered `yes` becomes an item. Nothing
downstream places an ADR that has no item.
