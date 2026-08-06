# Conventions — repository-wide

Conventions every module follows, whatever its stack. A module's own
[`<module>/docs/conventions.md`](../ai-connector-service/docs/conventions.md) extends these and never contradicts
them.

- [Writing Documentation](conventions/documentation.md) — how every README, conventions file, contract,
  use-case page, domain page, design and plan is written.
- [Building a Java Module](conventions/java-build.md) — the test wrapper, how to read a run, the coverage
  guardrail, the evidence a finished plan carries, and dependency inspection, for every JVM module. A module on
  another stack gets its own file beside it.
- [Building a Node Module](conventions/node-build.md) — the npm scripts, the coverage guardrail and the
  formatting gate, for every npm/TypeScript module.
- [Diagrams](conventions/diagrams.md) — the diagram language, its includes, and what each C4 level shows.
- [ADR Lifecycle](conventions/adr.md) — how a decision record is superseded or deprecated, and how it is
  numbered across the two tiers.
