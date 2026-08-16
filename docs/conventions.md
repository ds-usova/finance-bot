# Conventions — repository-wide

Conventions every module follows, whatever its stack. A module's own
[`<module>/docs/conventions.md`](../ai-connector-service/docs/conventions.md) extends these and never contradicts
them.

- [Writing Documentation](conventions/documentation.md) — how every README, conventions file, contract,
  use-case page, domain page, design and plan is written.
- [Building a Java Module](conventions/java-build.md) — the test wrapper, how to read a run, the coverage
  guardrail, the evidence a finished plan carries, dependency versions and dependency inspection, for every JVM
  module. A module on
  another stack gets its own file beside it.
- [Building a Node Module](conventions/node-build.md) — the npm scripts, the coverage guardrail, the
  formatting gate and dependency versions, for every npm/TypeScript module.
- [Code Style](conventions/code-style.md) — what holds for production code in every module, whatever its stack.
- [Testing](conventions/testing.md) — what holds for tests in every module, whatever its stack.
- [Diagrams](conventions/diagrams.md) — the diagram language, its includes, and what each C4 level shows.
- [Version Control](conventions/version-control.md) — when a change is committed, what a message says, and how a
  commit is scoped while another module is being worked.
- [Parallelism](conventions/parallelism.md) — how much may run at once on the machine every module shares, and
  how many plans may be implemented side by side.
- [Follow-Up Work](conventions/follow-up.md) — what runs once a change is complete, and what documents it earns.
- [ADR Lifecycle](conventions/adr.md) — how a decision record is superseded or deprecated, and how it is
  numbered across the two tiers.
