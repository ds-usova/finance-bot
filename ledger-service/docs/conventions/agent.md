# [Conventions](../conventions.md) > Agent Configuration

How the coding agent parallelizes work on this module, and which model does what.

## Version Control

Repository-wide, since every module shares one history: [Version Control](../../../docs/conventions/version-control.md).

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

What the machine allows across every module at once is repository-wide:
[Parallelism](../../../docs/conventions/parallelism.md). This module's suite is the one that starts containers,
so that file limits how it runs beside another module. Below is what is contended inside this module.

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

What runs once a change is complete, and what it earns, is [Follow-Up Work](follow-up.md) — true of this service
however the work was done, so it does not live here.

A plan carries one of those kinds in its **Post-Implementation Steps** group:

- **ADRs, placed** — one item per approved decision, as `Place ADR: <the decision, stated as a fact>`.

The item authorizes the agent to **place** the record and never to write it:
[Architecture Decision Records](../../../docs/conventions/adr.md) defines both states, and the sections of a
placed one are briefs that stay as written. The decision was the developer's, so the explanation is theirs; an
agent that fills those sections in produces something indistinguishable from a considered ADR and wrong in a
way no review catches.

The approval is a numbered open question in the plan, and only an answered `yes` becomes an item. Nothing
downstream places an ADR that has no item.
