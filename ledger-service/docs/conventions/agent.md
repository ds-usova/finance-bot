# [Conventions](../conventions.md) > Agent Configuration

## Version Control

Commit contents, scope and message are repository-wide:
[Version Control](../../../docs/conventions/version-control.md). What the agent does with them:

- **Commit as the work goes**, once each check gating the change has passed, without being asked each time. A
  single edit inside a check earns no commit of its own.
- **Never create, switch or delete a branch.** The developer checks one out before work starts; commit to
  whatever is current, `main` included.
- **A commit refused because the repository index is locked is retried once**, after a moment. Only that failure
  is retried: a rejected hook, an empty commit or a bad path is reported, never repeated. A retry that also fails
  is left alone, and the next commit takes the files.
- **Another module's files may be half-written beside yours**, and naming your paths is what keeps them out.

## Sub-Agent Models

Delegated work splits by how much judgment it needs:

- **Deciding work — the strongest model** (`opus`): planning a task, reviewing a plan, and the refactor pass over
  a finished diff. These choose what gets built and judge finished work against the conventions.
- **Executing work — a cheaper model** (`sonnet`): the stabilization agent and every red- and green-phase step
  agent. Each is handed one target class, its scenarios and the conventions, and writes code against them.
- The orchestrator, and anything not listed above, runs on whatever model the session runs on.

The model name is the `model` parameter of the agent-spawning tool.

## Parallelism

What the machine allows across every module at once is repository-wide:
[Parallelism](../../../docs/conventions/parallelism.md). This module's suite is the one that starts containers,
so that file limits how it runs beside another module.

Concurrent runs share the Gradle caches, `build/classes`, one Docker daemon and this machine's memory — memory
being the scarce one. Each concurrent test run starts its **own** Postgres container and WireMock server (JVM
singletons, not machine singletons), both on random ports, so ports are not contended. Results cannot collide:
the runner in [Build](build.md) gives every run its own directory, and its queue protects everything else on
that list.

- Max concurrent implementation agents on this module's plan: **4**.
- Concurrent test runs: effectively **1**. Agents may all ask at once; the queue serializes them. Compiling
  queues too, writing to the same `build/classes`.
- Treat an unexplained container-startup failure as memory pressure and rerun before debugging it as real.
  Never bypass the queue.

Parallel agents share the working tree. An agent stays inside the files its step owns, and never draws
conclusions from a file another agent is writing: a broken compile or failing test in someone else's target is
their work in progress, not a finding. Reconciling across steps is the orchestrator's job.

## Follow-Up Work in a Plan

What runs once a change is complete, and what it earns, is [Follow-Up Work](follow-up.md).

A plan carries one of those kinds in its **Post-Implementation Steps** group:

- **ADR placeholders** — one item per approved decision, as `Place ADR: <the decision, stated as a fact>`.

**An agent places an ADR; it never writes one.** Placing means creating the file at the number, tier and
filename [Architecture Decision Records](../../../docs/conventions/adr.md) gives it, with the title, the date,
the source link, and each section left as the angle-bracket brief that page's template carries. `Status:` reads
`Proposed` until the developer replaces the briefs with the content and moves it to `Accepted`. The decision was
the developer's, so the explanation is theirs.

The approval is a numbered open question in the plan, and only an answered `yes` becomes an item. Nothing
downstream places an ADR that has no item.
