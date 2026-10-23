# [Conventions](../conventions.md) > Agent Configuration

## Version Control

Commit contents, scope and message are repository-wide:
[Version Control](../../../docs/conventions/version-control.md). What the agent does with them:

- **Commit as the work goes**, once each check gating the change has passed, without being asked each time. A
  single edit inside a check earns no commit of its own.
- **Never create, switch or delete a branch.** Commit to whatever is current.
- **A commit refused because the repository index is locked is retried once**, after a moment. Any other refusal
  is reported, never repeated.
- **Another module's files may be half-written beside yours**, and naming your paths is what keeps them out.

## Sub-Agent Models

- **Deciding work** (`opus`): planning, reviewing a plan, the refactor pass.
- **Executing work** (`sonnet`): the stabilization agent and every red- and green-phase step agent.
- Anything else runs on the session's model.

## Parallelism

What the machine allows across every module is [Parallelism](../../../docs/conventions/parallelism.md).

- Max concurrent implementation agents on this module's plan: **4**.
- Concurrent test runs: effectively **1**; the wrapper's queue serializes them. The suite starts no container.

## Implementation Bundles

- Prefer items from the same: source grouping.
- Max plan items per implementation agent: unlimited.
- Max plan brief characters per implementation agent: unlimited.
