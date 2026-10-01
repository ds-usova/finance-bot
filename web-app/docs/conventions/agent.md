# [Conventions](../conventions.md) > Agent Configuration

How the coding agent works this module, and what it cannot exercise here.

## Version Control

Repository-wide: [Version Control](../../../docs/conventions/version-control.md).

## Sub-Agent Models

Delegated work splits by how much judgment it needs, on the same terms as this repository's other modules.

| Work            | Model             | Which agents                                                   |
|-----------------|-------------------|-----------------------------------------------------------------|
| Deciding        | `opus`            | planning a task, reviewing a plan, the refactor pass            |
| Executing       | `sonnet`          | the stabilization agent, every red- and green-phase step agent  |
| Everything else | the session's own | the orchestrator                                                |

## Parallelism

What the machine allows across every module at once is repository-wide:
[Parallelism](../../../docs/conventions/parallelism.md). This module adds:

- Max concurrent implementation agents on this module's plan: **4**.
- Concurrent test runs: **1** — two runs share `coverage/` and `dist/`, so a coverage run and a test run never
  start together.

## Follow-Up Work in a Plan

What runs once a change is complete, and what it earns, is [Follow-Up Work](follow-up.md).

A plan carries two kinds in its **Post-Implementation Steps** group:

- **ADRs** — one item per approved decision, as `Write ADR: <the decision, stated as a fact>`.
- **A contract-page rewrite the design already commits to** — one item per page, naming what changes.

An ADR's approval is a numbered open question in the plan, and only an answered `yes` becomes an item.

## What a Person Still Has to Look At

A change touching anything under [What the Suite Cannot See](testing.md#what-the-suite-cannot-see) is looked at
by a person. **No plan step carries that, and nothing waits for it.** The task archives, the follow-up runs, and
the looking happens whenever its reader gets to it.

**The level that finishes the task writes that list**, into the task's `review/findings.md`, in whatever shape
that file's own template gives: the screens and the states to check, carried from the design rather than
invented. An agent implementing one plan writes neither the list nor the file — what it has for them goes under
its own plan's `### Open Questions / Blockers`, which is the one place it may write outside its module.

## Reaching for a Tunnel

Never, to run the tests. [Orientation](orientation.md#what-cannot-be-exercised-locally) says what a local run
cannot exercise and why the suite does not need it.
