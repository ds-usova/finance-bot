# [Conventions](../conventions.md) > Agent Configuration

How the coding agent works this module, and what it cannot exercise here.

## Version Control

Repository-wide, since every module shares one history: [Version Control](../../../docs/conventions/version-control.md).

## Sub-Agent Models

Delegated work splits by how much judgment it needs, on the same terms as this repository's other modules:

- **Deciding work — the strongest model** (`opus`): planning a task, reviewing a plan, and the refactor pass over
  a finished diff. These choose what gets built and judge finished work against the conventions, so a weak call
  here is inherited by every step downstream and costs more than the model does.
- **Executing work — a cheaper model** (`sonnet`): the stabilization agent and every red- and green-phase step
  agent. Such an agent is handed one target, its scenarios and these conventions, and writes code against them —
  the decisions were made in the plan, and the stage guardrails catch what it gets wrong.
- The orchestrator, and anything not listed above, runs on whatever model the session runs on.

Nothing about a browser module argues for a different split: a component and its test are executing work in the
same sense a use case and its test are.

## Parallelism

What the machine allows across every module at once is repository-wide:
[Parallelism](../../../docs/conventions/parallelism.md). Below is this module only.

- Max concurrent implementation agents on this module's plan: **4**.
- Concurrent test runs: **1**. Two runs share `coverage/` and `dist/`, so a coverage run and a test run are not
  started at the same time.

Nothing else here is contended: there is no shared build directory of the kind that forces a queue on a Gradle
module, and the suite runs under jsdom without starting a container.

## Follow-Up Work in a Plan

What runs once a change is complete, and what it earns, is [Follow-Up Work](follow-up.md).

A plan carries these kinds in its **Post-Implementation Steps** group:

- **ADRs** — one item per approved decision, as `Write ADR: <the decision, stated as a fact>`.
- **Manual review** — one item, whenever the plan touches anything under
  [What the Suite Cannot See](testing.md#what-the-suite-cannot-see). It starts the app and hands it over, naming
  the screens and the states to look at. The list is the design's, recorded there by `grill-frontend`; the item
  carries it rather than inventing it.

An ADR's approval is a numbered open question in the plan, and only an answered `yes` becomes an item. A manual
review needs no approval: the plan cannot claim a screen is right on the strength of a suite that cannot see it.

## Reaching for a Tunnel

Never, to run the tests. [Orientation](orientation.md#what-cannot-be-exercised-locally) says what a local run
cannot exercise and why the suite does not need it: the tests sign their own payload with a test bot token.
